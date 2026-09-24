package net.altarsmp.testclient.module.combat;

import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.minecraft.world.item.ItemStack;

/**
 * Auto Totem: whenever the offhand is not a totem (normally right after one pops) it waits a delay and
 * moves a totem into the offhand with one SWAP click on the player inventory, the same packet as
 * hovering the totem in the inventory screen and pressing F. It works with the inventory screen open,
 * but not while another container (chest etc.) is open.
 *
 * <p>Humanlike: random delay from [min, max] ms after the offhand is noticed empty. Blatant: same tick.
 */
public final class AutoTotem extends Module {
	private static final long POP_WINDOW_MS = 5000;

	private final IntSetting delayMin = add(new IntSetting("delayMin", "Delay min",
			"Humanlike: minimum delay before re-equipping", 100, 0, 1000, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Delay max",
			"Humanlike: maximum delay before re-equipping", 250, 0, 1000, "ms"));
	private final BooleanSetting onlyAfterPop = add(new BooleanSetting("onlyAfterPop", "Only after pop",
			"Only refill within 5 s of a totem pop (otherwise keep a totem in the offhand at all times)", false));
	private final BooleanSetting mainInventoryFirst = add(new BooleanSetting("mainInventoryFirst", "Main inventory first",
			"Take totems from the main inventory before the hotbar", true));

	private final Deadline pending = new Deadline();
	private long lastPopMillis;

	public AutoTotem() {
		super("auto_totem", "Auto Totem", "Re-equips a totem to the offhand after a pop", Kind.TOGGLE);
	}

	/** Called by {@code CombatHooks} when the server reports our own totem being used. */
	public void onOwnTotemPop() {
		lastPopMillis = System.currentTimeMillis();
	}

	@Override
	public boolean runsWithScreenOpen() {
		return true;
	}

	@Override
	public void onTick() {
		ItemStack offhand = mc().player.getOffhandItem();
		if (ItemUtil.isTotem(offhand)) {
			pending.clear();
			return;
		}
		long sincePop = System.currentTimeMillis() - lastPopMillis;
		if (onlyAfterPop.get() && sincePop > POP_WINDOW_MS) {
			pending.clear();
			return;
		}
		if (!InventoryUtil.canClickInventory()) {
			return;
		}
		int slot = InventoryUtil.findInventory(ItemUtil::isTotem, mainInventoryFirst.get());
		if (slot < 0) {
			pending.clear();
			return;
		}
		if (!pending.armed()) {
			pending.in(stepDelay(delayMin, delayMax));
		}
		if (!pending.passed()) {
			return;
		}
		String replaced = offhand.isEmpty() ? "empty" : offhand.getItem().toString();
		InventoryUtil.swapWithOffhand(slot);
		log("EQUIP_TOTEM", "", "from inventory slot " + slot + ", offhand was " + replaced
				+ (sincePop <= POP_WINDOW_MS ? ", " + sincePop + "ms after pop" : ""));
		pending.clear();
	}

	@Override
	public void resetState() {
		pending.clear();
	}
}
