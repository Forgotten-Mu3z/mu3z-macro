package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.altarsmp.testclient.mixin.MinecraftInvoker;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.SpeedMode;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Chat;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.altarsmp.testclient.util.KeyUtil;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Punch Bow (action): one key press selects the best bow in the hotbar (highest Punch, then Power), draws it
 * for "Draw time" ticks, releases it (the shot goes where you are looking) and selects the previous slot again.
 *
 * <p>Vanilla cancels a draw on the next tick unless the use key is held, so the macro holds the use key itself
 * while drawing and lets go right after the shot. A bow fires from 3 ticks of draw (weak shot) and reaches
 * full power and a critical arrow at 20 ticks.
 *
 * <p>Blatant (the default for this module): select and start drawing in the key-press tick, release on
 * exactly the draw-time tick and swap back in that same tick. Humanlike: random gaps between the steps and up
 * to "Extra draw max" extra draw ticks.
 */
public final class PunchBow extends Module {
	private enum Stage { IDLE, DRAW, DRAWING, RESTORE }

	/** Vanilla's right-click delay after using an item. */
	private static final int VANILLA_USE_DELAY = 4;

	private final IntSetting drawTicks = add(new IntSetting("drawTicks", "Draw time",
			"Ticks to draw before releasing: 20 = full power and a critical arrow, 3 = fastest shot that still fires",
			20, 3, 40, "t"));
	private final IntSetting drawJitter = add(new IntSetting("drawJitter", "Extra draw max",
			"Humanlike: up to this many extra draw ticks", 3, 0, 10, "t"));
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

	public PunchBow() {
		super("punch_bow", "Punch Bow", "Key press: swap to your Punch bow, draw, shoot, swap back", Kind.ACTION, SpeedMode.BLATANT);
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
		if (!ActionLock.tryAcquire(this)) {
			return;
		}
		originalSlot = InventoryUtil.selectedSlot();
		bowSlot = slot;
		InventoryUtil.select(slot);
		log("SELECT_BOW", "", "slot " + originalSlot + "->" + slot + " punch " + ItemUtil.punchLevel(bow)
				+ " power " + ItemUtil.powerLevel(bow));
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
				finish();
				return;
			}
			// Re-assert every tick: opening a menu or losing focus releases all keys.
			holdUse();
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
		holdUse();
		InteractionResult result = mc().gameMode.useItem(player, InteractionHand.MAIN_HAND);
		if (!player.isUsingItem()) {
			log("ABORT", "", "bow did not start drawing (" + result.getClass().getSimpleName() + ")");
			restoreSlot();
			finish();
			return;
		}
		int extra = blatant() || drawJitter.get() <= 0 ? 0 : ThreadLocalRandom.current().nextInt(drawJitter.get() + 1);
		targetDrawTicks = drawTicks.get() + extra;
		log("DRAW", "", "release after " + targetDrawTicks + "t");
		stage = Stage.DRAWING;
	}

	private void shoot() {
		LocalPlayer player = mc().player;
		int ticks = player.getTicksUsingItem();
		float power = BowItem.getPowerForTime(ticks);
		mc().gameMode.releaseUsingItem(player);
		releaseUse();
		// Same delay vanilla applies after a use, so a physically held right mouse button doesn't redraw instantly.
		((MinecraftInvoker) mc()).altar$setRightClickDelay(VANILLA_USE_DELAY);
		log("SHOOT", "", String.format(Locale.ROOT, "draw %dt power %.2f%s yaw %.1f pitch %.1f",
				ticks, power, power >= 1.0F ? " crit" : "", player.getYRot(), player.getXRot()));
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
		if (stage != Stage.IDLE && mc().player != null) {
			// Switching away from the bow cancels an unfinished draw without shooting.
			if (originalSlot >= 0 && originalSlot != bowSlot) {
				InventoryUtil.select(originalSlot);
			}
		}
		finish();
	}
}
