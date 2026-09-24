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
import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InteractionAim;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Auto Crystal: targets the nearest player in range and runs two independent steps, at most one
 * interaction per tick:
 * <ul>
 * <li>Break: attack the end crystal closest to the target that is within reach.</li>
 * <li>Place: find the obsidian/bedrock block closest to the target that has free space above it and is
 * within reach, then right-click its top face with end crystals (selecting them from the hotbar, or
 * using the offhand if it holds crystals).</li>
 * </ul>
 * Each step has its own delay (humanlike: random from min/max; blatant: none) and can rotate first
 * according to the Rotate setting. There is deliberately no self-damage protection: it is a test tool.
 */
public final class AutoCrystal extends Module {
	private final DoubleSetting targetRange = add(new DoubleSetting("targetRange", "Target range",
			"Players further than this are ignored", 8.0, 2.0, 16.0, 0.5, "m"));
	private final DoubleSetting placeRange = add(new DoubleSetting("placeRange", "Place range",
			"Max distance from your eyes to the clicked block face", 4.5, 1.0, 6.0, 0.1, "m"));
	private final DoubleSetting breakRange = add(new DoubleSetting("breakRange", "Break range",
			"Max distance from your eyes to a crystal's hitbox", 4.5, 1.0, 6.0, 0.1, "m"));
	private final DoubleSetting maxTargetDistance = add(new DoubleSetting("maxTargetDistance", "Max crystal-target dist",
			"Only place/break crystals this close to the target's feet", 3.5, 1.0, 8.0, 0.5, "m"));
	private final IntSetting placeDelayMin = add(new IntSetting("placeDelayMin", "Place delay min",
			"Humanlike: minimum delay before each placement", 80, 0, 1000, "ms"));
	private final IntSetting placeDelayMax = add(new IntSetting("placeDelayMax", "Place delay max",
			"Humanlike: maximum delay before each placement", 160, 0, 1000, "ms"));
	private final IntSetting breakDelayMin = add(new IntSetting("breakDelayMin", "Break delay min",
			"Humanlike: minimum delay before each break", 50, 0, 1000, "ms"));
	private final IntSetting breakDelayMax = add(new IntSetting("breakDelayMax", "Break delay max",
			"Humanlike: maximum delay before each break", 120, 0, 1000, "ms"));
	private final EnumSetting<RotateMode> rotate = add(new EnumSetting<>("rotate", "Rotate",
			"Off, turn the camera and act next tick, or send a rotation packet before acting", RotateMode.CAMERA));
	private final BooleanSetting place = add(new BooleanSetting("place", "Place", "Place crystals", true));
	private final BooleanSetting doBreak = add(new BooleanSetting("break", "Break", "Break crystals", true));
	private final BooleanSetting autoSwitch = add(new BooleanSetting("autoSwitch", "Auto switch",
			"Select end crystals from the hotbar automatically", true));

	private final Deadline placeTimer = new Deadline();
	private final Deadline breakTimer = new Deadline();

	public AutoCrystal() {
		super("auto_crystal", "Auto Crystal", "Places and breaks end crystals on obsidian near the target", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		if (!ActionLock.isFreeFor(this)) {
			return;
		}
		Player target = TargetUtil.nearest(targetRange.get());
		if (target == null) {
			resetState();
			return;
		}
		if (doBreak.get() && tryBreak(target)) {
			return;
		}
		if (place.get()) {
			tryPlace(target);
		}
	}

	/** @return true if this tick was used (rotating or breaking). */
	private boolean tryBreak(Player target) {
		EndCrystal crystal = findCrystal(target);
		if (crystal == null) {
			breakTimer.clear();
			return false;
		}
		if (!breakTimer.armed()) {
			breakTimer.in(stepDelay(breakDelayMin, breakDelayMax));
		}
		if (!breakTimer.passed()) {
			return false;
		}
		if (!InteractionAim.ready(rotate.get(), crystal.getBoundingBox().getCenter())) {
			return true;
		}
		CombatHooks.attack(crystal, false);
		log("BREAK", TargetUtil.describe(target), String.format(Locale.ROOT, "crystal %s dist-to-target %.2f reach %.2f",
				BlockUtil.describe(crystal.blockPosition()), crystal.distanceTo(target), TargetUtil.reachDistance(crystal)));
		breakTimer.clear();
		return true;
	}

	private void tryPlace(Player target) {
		LocalPlayer player = mc().player;
		InteractionHand hand;
		int slot = -1;
		if (player.getOffhandItem().is(Items.END_CRYSTAL)) {
			hand = InteractionHand.OFF_HAND;
		} else {
			hand = InteractionHand.MAIN_HAND;
			slot = InventoryUtil.findHotbar(stack -> stack.is(Items.END_CRYSTAL));
			if (slot < 0 || (!autoSwitch.get() && slot != InventoryUtil.selectedSlot())) {
				placeTimer.clear();
				return;
			}
		}
		BlockPos base = findBase(target);
		if (base == null) {
			placeTimer.clear();
			return;
		}
		if (!placeTimer.armed()) {
			placeTimer.in(stepDelay(placeDelayMin, placeDelayMax));
		}
		if (!placeTimer.passed()) {
			return;
		}
		BlockHitResult hit = BlockUtil.topFaceHit(base);
		if (!InteractionAim.ready(rotate.get(), hit.getLocation())) {
			return;
		}
		if (hand == InteractionHand.MAIN_HAND && slot != InventoryUtil.selectedSlot()) {
			log("SWITCH", TargetUtil.describe(target), InventoryUtil.selectedSlot() + "->" + slot);
			InventoryUtil.select(slot);
		}
		BlockUtil.use(hand, hit);
		log("PLACE", TargetUtil.describe(target), String.format(Locale.ROOT, "on %s hand %s dist-to-target %.2f",
				BlockUtil.describe(base), hand, Vec3.atBottomCenterOf(base.above()).distanceTo(target.position())));
		placeTimer.clear();
	}

	private @Nullable EndCrystal findCrystal(Player target) {
		LocalPlayer player = mc().player;
		EndCrystal best = null;
		double bestDist = Double.MAX_VALUE;
		AABB searchBox = player.getBoundingBox().inflate(breakRange.get() + 2.0);
		for (EndCrystal crystal : mc().level.getEntitiesOfClass(EndCrystal.class, searchBox, Entity::isAlive)) {
			if (TargetUtil.reachDistance(crystal) > breakRange.get()) {
				continue;
			}
			double toTarget = crystal.distanceTo(target);
			if (toTarget <= maxTargetDistance.get() + 1.5 && toTarget < bestDist) {
				best = crystal;
				bestDist = toTarget;
			}
		}
		return best;
	}

	/** Obsidian/bedrock block with an empty block above and no entities in the crystal's 1x2x1 space. */
	private @Nullable BlockPos findBase(Player target) {
		ClientLevel level = mc().level;
		Vec3 eye = mc().player.getEyePosition();
		BlockPos feet = target.blockPosition();
		int radius = (int) Math.ceil(maxTargetDistance.get()) + 1;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -3; dy <= 1; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
						continue;
					}
					BlockPos above = pos.above();
					if (!level.isEmptyBlock(above)) {
						continue;
					}
					Vec3 face = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
					if (eye.distanceTo(face) > placeRange.get()) {
						continue;
					}
					double toTarget = Vec3.atBottomCenterOf(above).distanceTo(target.position());
					if (toTarget > maxTargetDistance.get() || toTarget >= bestDist) {
						continue;
					}
					AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
					if (!level.getEntities((Entity) null, space).isEmpty()) {
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
		placeTimer.clear();
		breakTimer.clear();
	}
}
