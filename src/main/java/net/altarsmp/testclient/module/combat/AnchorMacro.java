package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.RotateMode;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.BlockUtil;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InteractionAim;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Anchor Macro: when a target is in range it runs one cycle of
 * <ol>
 * <li>place a respawn anchor on a free block next to the target (clicking a solid neighbour face),</li>
 * <li>charge it with glowstone (Charges times),</li>
 * <li>detonate it by right-clicking with any non-glowstone item (anchors explode outside the Nether),</li>
 * <li>swap back to the original hotbar slot,</li>
 * </ol>
 * then waits the cycle cooldown. Humanlike: random delay before every step. Blatant: every step goes out
 * in the same tick (the server applies them in order), unless Rotate = Camera forces a tick per turn.
 * Skipped in the Nether and while sneaking (sneak-clicking would place the glowstone instead).
 */
public final class AnchorMacro extends Module {
	private enum Stage { IDLE, PLACE, CHARGE, DETONATE, RESTORE, COOLDOWN }

	private static final int MAX_STEPS_PER_TICK = 8;

	private final DoubleSetting targetRange = add(new DoubleSetting("targetRange", "Target range",
			"Players further than this are ignored", 6.0, 2.0, 12.0, 0.5, "m"));
	private final DoubleSetting placeRange = add(new DoubleSetting("placeRange", "Place range",
			"Max distance from your eyes to the anchor position", 4.5, 1.0, 6.0, 0.1, "m"));
	private final DoubleSetting maxTargetDistance = add(new DoubleSetting("maxTargetDistance", "Max anchor-target dist",
			"Only use anchor positions this close to the target", 2.5, 1.0, 5.0, 0.5, "m"));
	private final IntSetting charges = add(new IntSetting("charges", "Charges",
			"Glowstone charges before detonating", 1, 1, 4, ""));
	private final IntSetting delayMin = add(new IntSetting("delayMin", "Step delay min",
			"Humanlike: minimum delay before each step", 60, 0, 1000, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Step delay max",
			"Humanlike: maximum delay before each step", 140, 0, 1000, "ms"));
	private final IntSetting cycleCooldown = add(new IntSetting("cycleCooldownMs", "Cycle cooldown",
			"Wait this long after a cycle before starting the next", 600, 0, 5000, "ms"));
	private final EnumSetting<RotateMode> rotate = add(new EnumSetting<>("rotate", "Rotate",
			"Off, turn the camera and act next tick, or send a rotation packet before acting", RotateMode.CAMERA));
	private final BooleanSetting swapBack = add(new BooleanSetting("swapBack", "Swap back",
			"Return to the originally held slot after detonating", true));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private @Nullable Player target;
	private @Nullable BlockPos anchorPos;
	private int originalSlot = -1;
	private int chargesDone;
	private long stepsTaken;

	public AnchorMacro() {
		super("anchor_macro", "Anchor Macro", "Places, charges and detonates a respawn anchor near the target", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		// Loop so blatant mode can run several zero-delay steps in one tick.
		for (int i = 0; i < MAX_STEPS_PER_TICK; i++) {
			long before = stepsTaken;
			step();
			if (stepsTaken == before || stage == Stage.COOLDOWN || !next.passed()) {
				return;
			}
		}
	}

	private void step() {
		switch (stage) {
			case IDLE -> tryStart();
			case PLACE -> {
				if (next.passed()) {
					place();
				}
			}
			case CHARGE -> {
				if (next.passed()) {
					charge();
				}
			}
			case DETONATE -> {
				if (next.passed()) {
					detonate();
				}
			}
			case RESTORE -> {
				if (next.passed()) {
					finish();
				}
			}
			case COOLDOWN -> {
				if (next.passed()) {
					stage = Stage.IDLE;
				}
			}
		}
	}

	private void tryStart() {
		LocalPlayer player = mc().player;
		if (!ActionLock.isFreeFor(this) || mc().level.dimension() == Level.NETHER || player.isSecondaryUseActive()) {
			return;
		}
		Player candidate = TargetUtil.nearest(targetRange.get());
		if (candidate == null) {
			return;
		}
		if (InventoryUtil.findHotbar(s -> s.is(Items.RESPAWN_ANCHOR)) < 0 || InventoryUtil.findHotbar(s -> s.is(Items.GLOWSTONE)) < 0) {
			return;
		}
		BlockPos pos = findAnchorPos(candidate);
		if (pos == null || !ActionLock.tryAcquire(this)) {
			return;
		}
		target = candidate;
		anchorPos = pos;
		originalSlot = InventoryUtil.selectedSlot();
		chargesDone = 0;
		advance(Stage.PLACE);
	}

	private void place() {
		int anchorSlot = InventoryUtil.findHotbar(s -> s.is(Items.RESPAWN_ANCHOR));
		BlockHitResult hit = anchorPos == null ? null : BlockUtil.placementHit(anchorPos);
		if (anchorSlot < 0 || hit == null || !mc().level.getBlockState(anchorPos).canBeReplaced()) {
			abort("anchor missing or position blocked");
			return;
		}
		if (!InteractionAim.ready(rotate.get(), hit.getLocation())) {
			return;
		}
		InventoryUtil.select(anchorSlot);
		BlockUtil.use(InteractionHand.MAIN_HAND, hit);
		log("PLACE_ANCHOR", TargetUtil.describe(target), String.format(Locale.ROOT, "at %s slot %d dist-to-target %.2f",
				BlockUtil.describe(anchorPos), anchorSlot, Vec3.atCenterOf(anchorPos).distanceTo(target.position())));
		advance(Stage.CHARGE);
	}

	private void charge() {
		int glowSlot = InventoryUtil.findHotbar(s -> s.is(Items.GLOWSTONE));
		if (!isAnchorAt(anchorPos) || glowSlot < 0 || mc().player.isSecondaryUseActive()) {
			abort("anchor not placed or glowstone missing");
			return;
		}
		BlockHitResult hit = BlockUtil.topFaceHit(anchorPos);
		if (!InteractionAim.ready(rotate.get(), hit.getLocation())) {
			return;
		}
		InventoryUtil.select(glowSlot);
		BlockUtil.use(InteractionHand.MAIN_HAND, hit);
		chargesDone++;
		log("CHARGE", TargetUtil.describe(target), "charge " + chargesDone + "/" + charges.get() + " slot " + glowSlot);
		advance(chargesDone >= charges.get() ? Stage.DETONATE : Stage.CHARGE);
	}

	private void detonate() {
		int slot = detonateSlot();
		if (!isAnchorAt(anchorPos) || slot < 0 || mc().player.isSecondaryUseActive()) {
			abort("anchor gone or no non-glowstone slot");
			return;
		}
		BlockHitResult hit = BlockUtil.topFaceHit(anchorPos);
		if (!InteractionAim.ready(rotate.get(), hit.getLocation())) {
			return;
		}
		InventoryUtil.select(slot);
		BlockUtil.use(InteractionHand.MAIN_HAND, hit);
		log("DETONATE", TargetUtil.describe(target), String.format(Locale.ROOT, "at %s slot %d target-dist %.2f",
				BlockUtil.describe(anchorPos), slot, target == null ? -1.0 : Vec3.atCenterOf(anchorPos).distanceTo(target.position())));
		if (blatant()) {
			finish();
		} else {
			advance(Stage.RESTORE);
		}
	}

	/** Any hotbar slot not holding glowstone, preferring the slot the player started on. */
	private int detonateSlot() {
		if (originalSlot >= 0 && !mc().player.getInventory().getItem(originalSlot).is(Items.GLOWSTONE)) {
			return originalSlot;
		}
		return InventoryUtil.findHotbar(stack -> !stack.is(Items.GLOWSTONE));
	}

	private void advance(Stage nextStage) {
		stepsTaken++;
		stage = nextStage;
		next.in(stepDelay(delayMin, delayMax));
	}

	private void abort(String reason) {
		log("ABORT", TargetUtil.describe(target), reason);
		finish();
	}

	private void finish() {
		if (swapBack.get() && originalSlot >= 0 && originalSlot != InventoryUtil.selectedSlot()) {
			InventoryUtil.select(originalSlot);
			log("SWAP_BACK", TargetUtil.describe(target), "slot " + originalSlot);
		}
		ActionLock.release(this);
		stepsTaken++;
		target = null;
		anchorPos = null;
		originalSlot = -1;
		stage = Stage.COOLDOWN;
		next.in(cycleCooldown.get());
	}

	private boolean isAnchorAt(@Nullable BlockPos pos) {
		return pos != null && mc().level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR);
	}

	/** Replaceable block with a solid neighbour to click, no entities inside, in reach, closest to the target. */
	private @Nullable BlockPos findAnchorPos(Player candidate) {
		ClientLevel level = mc().level;
		Vec3 eye = mc().player.getEyePosition();
		BlockPos feet = candidate.blockPosition();
		int radius = (int) Math.ceil(maxTargetDistance.get());
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (!state.canBeReplaced()) {
						continue;
					}
					Vec3 center = Vec3.atCenterOf(pos);
					double toTarget = center.distanceTo(candidate.position());
					if (toTarget > maxTargetDistance.get() || toTarget >= bestDist || eye.distanceTo(center) > placeRange.get()) {
						continue;
					}
					if (!level.getEntities((Entity) null, new AABB(pos)).isEmpty() || BlockUtil.placementHit(pos) == null) {
						continue;
					}
					best = pos;
					bestDist = toTarget;
				}
			}
		}
		return best;
	}

	@Override
	public void resetState() {
		boolean midCycle = stage == Stage.PLACE || stage == Stage.CHARGE || stage == Stage.DETONATE || stage == Stage.RESTORE;
		if (midCycle && swapBack.get() && originalSlot >= 0 && mc().player != null) {
			InventoryUtil.select(originalSlot);
		}
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		target = null;
		anchorPos = null;
		originalSlot = -1;
		chargesDone = 0;
	}
}
