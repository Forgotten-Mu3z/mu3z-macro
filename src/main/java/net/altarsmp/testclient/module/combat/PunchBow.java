package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.altarsmp.testclient.mixin.MinecraftInvoker;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.SpeedMode;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Chat;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.altarsmp.testclient.util.KeyUtil;
import net.altarsmp.testclient.util.RotationSpoof;
import net.altarsmp.testclient.util.RotationUtil;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Punch Bow (action): one key press selects the best bow in the hotbar (highest Punch, then Power), draws,
 * shoots and selects the previous slot again.
 *
 * <p>Self boost (default mode, the "P bow boost"): shoots an arrow almost straight up so it comes back down on
 * you and its Punch knockback launches you. Why it works this way (1.21.11 arrow code): an arrow cannot hit its
 * own shooter until it has fully left the shooter's hitbox (plus a 1-block margin), so it has to go up and come
 * back; Punch then pushes the victim in the arrow's horizontal direction of travel (plus a small lift), so a
 * slight tilt toward where you face decides which way you fly. Standing still, a 4-tick draw is the minimum that
 * clears your hitbox (17-tick flight); the default 5 (22 ticks) still works if the server counts one tick more or
 * less. The server aims the arrow with the rotation from your last movement packet and adds your last known
 * movement to it, so the macro keeps the camera locked upward every frame during the draw (never sending a
 * rotation-only packet, which would zero that movement) and, while you walk forward, tilts the shot just enough
 * to make up for the arrow falling behind you. It puts your camera back after the shot.
 *
 * <p>Shoot mode: fires where you are aiming (e.g. to knock another player back), drawn for "Draw time" ticks.
 *
 * <p>Vanilla cancels a draw on the next tick unless the use key is held, so the macro holds the use key itself
 * while drawing. Blatant (the default for this module): swap and draw start in the key-press tick, release on
 * exactly the draw tick, swap back in that same tick. Humanlike: random gaps between the steps (and, in shoot
 * mode, up to "Extra draw max" extra draw ticks).
 */
public final class PunchBow extends Module {
	public enum Mode {
		BOOST("Self boost"), SHOOT("Shoot where aiming");

		private final String displayName;

		Mode(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	private enum Stage { IDLE, DRAW, DRAWING, RESTORE }

	/** Vanilla's right-click delay after using an item. */
	private static final int VANILLA_USE_DELAY = 4;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode",
			"Self boost: shoot yourself to get launched. Shoot: fire where you are aiming", Mode.BOOST));
	private final IntSetting boostDraw = add(new IntSetting("boostDraw", "Boost draw",
			"Self boost: draw ticks. 5 lands back on you after ~1.1 s and still works if the server counts a tick more or less; more = higher and slower",
			5, 4, 8, "t"));
	private final DoubleSetting boostTilt = add(new DoubleSetting("boostTilt", "Boost tilt",
			"Self boost, standing still: degrees from straight up toward where you face (sets the launch direction). While walking forward the tilt is computed from your speed",
			1.5, 0.0, 4.0, 0.5, "°"));
	private final BooleanSetting silentAim = add(new BooleanSetting("silentAim", "Silent aim",
			"Self boost: only the server sees you aim up; your camera never moves", true));
	private final IntSetting drawTicks = add(new IntSetting("drawTicks", "Draw time",
			"Shoot mode: ticks to draw. 20 = full power and a critical arrow, 3 = fastest shot that still fires",
			20, 3, 40, "t"));
	private final IntSetting drawJitter = add(new IntSetting("drawJitter", "Extra draw max",
			"Shoot mode, humanlike: up to this many extra draw ticks", 3, 0, 10, "t"));
	private final IntSetting delayMin = add(new IntSetting("delayMin", "Step delay min",
			"Humanlike: minimum delay between swapping, drawing and swapping back", 40, 0, 500, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Step delay max",
			"Humanlike: maximum delay between swapping, drawing and swapping back", 100, 0, 500, "ms"));
	private final BooleanSetting swapBack = add(new BooleanSetting("swapBack", "Swap back",
			"Return to the previously held slot after shooting", true));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private int originalSlot = -1;
	private int bowSlot = -1;
	private int targetDrawTicks;
	private boolean holdingUse;
	private boolean aimedUp;
	private boolean spoofing;
	private float savedPitch;

	public PunchBow() {
		super("punch_bow", "Punch Bow", "Key press: Punch bow self-boost (or shot), then swap back", Kind.ACTION, SpeedMode.BLATANT);
	}

	@Override
	public void onKeyPressed() {
		LocalPlayer player = mc().player;
		if (player == null || mc().screen != null) {
			return;
		}
		if (!isEnabled()) {
			Chat.actionBar("Punch Bow is not armed - enable it in the AltarTestClient screen");
			return;
		}
		if (!ServerGuard.isAllowed()) {
			Chat.actionBar("AltarTestClient is inactive on this server");
			return;
		}
		if (stage != Stage.IDLE) {
			return;
		}
		if (player.isUsingItem()) {
			Chat.actionBar("Already using an item");
			return;
		}
		int slot = InventoryUtil.findBestHotbar(PunchBow::bowScore);
		if (slot < 0) {
			Chat.actionBar("No bow in hotbar");
			return;
		}
		ItemStack bow = player.getInventory().getItem(slot);
		if (player.getProjectile(bow).isEmpty() && !player.hasInfiniteMaterials()) {
			Chat.actionBar("No arrows");
			return;
		}
		if (mode.get() == Mode.BOOST && ItemUtil.punchLevel(bow) == 0) {
			Chat.actionBar("That bow has no Punch - the boost will only hurt");
		}
		if (!ActionLock.tryAcquire(this)) {
			return;
		}
		originalSlot = InventoryUtil.selectedSlot();
		bowSlot = slot;
		InventoryUtil.select(slot);
		log("SELECT_BOW", "", "mode " + mode.get().name() + " slot " + originalSlot + "->" + slot
				+ " punch " + ItemUtil.punchLevel(bow) + " power " + ItemUtil.powerLevel(bow));
		stage = Stage.DRAW;
		next.in(stepDelay(delayMin, delayMax));
		if (next.passed()) {
			onTick();
		}
	}

	@Override
	public void onTick() {
		if (stage == Stage.DRAW && next.passed()) {
			startDraw();
		}
		if (stage == Stage.DRAWING) {
			LocalPlayer player = mc().player;
			if (!player.isUsingItem()) {
				log("ABORT", "", "draw was interrupted");
				restoreSlot();
				finish();
				return;
			}
			// Re-assert every tick: opening a menu or losing focus releases all keys, and the mouse may move.
			holdUse();
			if (spoofing) {
				// Recomputed every tick: the forward tilt follows your current walking speed.
				RotationSpoof.setPitch(boostPitch(player));
			} else if (aimedUp) {
				aimUp(player);
			}
			if (player.getTicksUsingItem() >= targetDrawTicks) {
				shoot();
			}
		}
		if (stage == Stage.RESTORE && next.passed()) {
			restoreSlot();
			finish();
		}
	}

	private void startDraw() {
		LocalPlayer player = mc().player;
		if (mode.get() == Mode.BOOST && silentAim.get()) {
			// From the next movement packet on, the server sees you looking up; the camera stays put.
			spoofing = true;
			RotationSpoof.setPitch(boostPitch(player));
		} else if (mode.get() == Mode.BOOST) {
			// Look up before the use packet, so the server has the upward rotation for the whole draw.
			savedPitch = player.getXRot();
			aimedUp = true;
			aimUp(player);
		}
		holdUse();
		InteractionResult result = mc().gameMode.useItem(player, InteractionHand.MAIN_HAND);
		if (!player.isUsingItem()) {
			log("ABORT", "", "bow did not start drawing (" + result.getClass().getSimpleName() + ")");
			restoreSlot();
			finish();
			return;
		}
		if (mode.get() == Mode.BOOST) {
			targetDrawTicks = boostDraw.get();
		} else {
			int extra = blatant() || drawJitter.get() <= 0 ? 0 : ThreadLocalRandom.current().nextInt(drawJitter.get() + 1);
			targetDrawTicks = drawTicks.get() + extra;
		}
		log("DRAW", "", "release after " + targetDrawTicks + "t");
		stage = Stage.DRAWING;
	}

	private void shoot() {
		LocalPlayer player = mc().player;
		// No extra rotation packet here: a rotation-only packet makes the server reset your known movement to
		// zero, and the arrow inherits that movement. The upward look was already sent with this tick's normal
		// movement packet (silent aim swaps it into that packet; camera mode keeps the camera locked).
		int ticks = player.getTicksUsingItem();
		float power = BowItem.getPowerForTime(ticks);
		float pitch = spoofing ? RotationSpoof.pitch() : player.getXRot();
		mc().gameMode.releaseUsingItem(player);
		releaseUse();
		restoreCamera();
		// Same delay vanilla applies after a use, so a physically held right mouse button doesn't redraw instantly.
		((MinecraftInvoker) mc()).altar$setRightClickDelay(VANILLA_USE_DELAY);
		log(mode.get() == Mode.BOOST ? "BOOST_SHOT" : "SHOOT", "", String.format(Locale.ROOT,
				"draw %dt power %.2f%s yaw %.1f pitch %.1f%s", ticks, power, power >= 1.0F ? " crit" : "",
				player.getYRot(), pitch, mode.get() == Mode.BOOST ? ", arrow lands back in ~" + boostFlightTicks() + "t" : ""));
		if (!swapBack.get()) {
			finish();
			return;
		}
		stage = Stage.RESTORE;
		next.in(stepDelay(delayMin, delayMax));
		if (next.passed()) {
			restoreSlot();
			finish();
		}
	}

	/** Keeps the upward look locked every frame while drawing, so each movement packet carries it. */
	@Override
	public void onFrame(double frameSeconds) {
		if (aimedUp && stage == Stage.DRAWING && mc().player != null) {
			aimUp(mc().player);
		}
	}

	/** Pitch almost straight up, tilted toward where you face; yaw stays yours so your walking is not affected. */
	private void aimUp(LocalPlayer player) {
		RotationUtil.turnBy(player, 0.0, boostPitch(player) - player.getXRot());
	}

	/**
	 * The arrow inherits your walking speed but loses 1% of it per tick while you keep walking, so it lands
	 * behind you by speed * (flight - sum of 0.99^t). When you walk forward the shot is tilted forward just enough
	 * for the arrow's own forward speed to make up that lag; standing still it uses "Boost tilt" (which also
	 * sets the launch direction).
	 */
	private float boostPitch(LocalPlayer player) {
		double tilt = boostTilt.get();
		double yaw = Math.toRadians(player.getYRot());
		double forwardSpeed = (player.getX() - player.xo) * -Math.sin(yaw) + (player.getZ() - player.zo) * Math.cos(yaw);
		if (forwardSpeed > 0.03) {
			int draw = boostDraw.get();
			int flight = boostFlightTicks();
			double carried = (1.0 - Math.pow(0.99, flight)) / 0.01;
			double lag = forwardSpeed * (flight - carried);
			double launchSpeed = 3.0 * BowItem.getPowerForTime(draw);
			double compensation = Math.toDegrees(Math.asin(Math.min(1.0, lag / carried / launchSpeed)));
			tilt = Math.max(tilt, compensation);
		}
		return (float) (-90.0 + Math.min(tilt, 30.0));
	}

	/** Ends silent aim (the next movement packet carries your real pitch again) or puts the camera back. */
	private void restoreCamera() {
		if (spoofing) {
			RotationSpoof.clear();
			spoofing = false;
		}
		if (aimedUp) {
			LocalPlayer player = mc().player;
			if (player != null) {
				RotationUtil.turnBy(player, 0.0, savedPitch - player.getXRot());
			}
			aimedUp = false;
		}
	}

	/** Flight time back onto a standing player, from the arrow-physics simulation (4t draw = 17t ... 8t = 37t). */
	private int boostFlightTicks() {
		return 17 + (boostDraw.get() - 4) * 5;
	}

	private void restoreSlot() {
		if (swapBack.get() && originalSlot >= 0 && originalSlot != InventoryUtil.selectedSlot()) {
			InventoryUtil.select(originalSlot);
			log("SWAP_BACK", "", "slot " + originalSlot);
		}
	}

	private void holdUse() {
		mc().options.keyUse.setDown(true);
		holdingUse = true;
	}

	private void releaseUse() {
		if (holdingUse && !KeyUtil.isPhysicallyDown(mc().options.keyUse)) {
			mc().options.keyUse.setDown(false);
		}
		holdingUse = false;
	}

	private void finish() {
		releaseUse();
		restoreCamera();
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		originalSlot = -1;
		bowSlot = -1;
	}

	/** Bows only; Punch counts most, then Power. */
	private static int bowScore(ItemStack stack) {
		if (!stack.is(Items.BOW)) {
			return 0;
		}
		return 1 + ItemUtil.punchLevel(stack) * 10 + ItemUtil.powerLevel(stack);
	}

	@Override
	public void resetState() {
		LocalPlayer player = mc().player;
		if (stage != Stage.IDLE && player != null) {
			boolean drawing = stage == Stage.DRAWING && player.isUsingItem();
			// The server stops a draw without shooting when the selected slot changes.
			if (originalSlot >= 0 && originalSlot != bowSlot) {
				InventoryUtil.select(originalSlot);
			} else if (drawing && bowSlot >= 0) {
				InventoryUtil.select((bowSlot + 1) % InventoryUtil.HOTBAR_SIZE);
				InventoryUtil.select(bowSlot);
			}
			if (drawing) {
				player.stopUsingItem();
				log("CANCEL", "", "draw cancelled without shooting");
			}
		}
		finish();
	}
}
