package net.altarsmp.testclient.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/** Item classification helpers. */
public final class ItemUtil {
	private ItemUtil() {
	}

	/** Items whose weapon component disables shields on hit (all axes in vanilla 1.21.11). */
	public static boolean isShieldDisabler(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Weapon weapon = stack.get(DataComponents.WEAPON);
		return (weapon != null && weapon.disableBlockingForSeconds() > 0) || stack.is(ItemTags.AXES);
	}

	public static boolean isMace(ItemStack stack) {
		return stack.is(Items.MACE);
	}

	public static boolean isTotem(ItemStack stack) {
		return stack.is(Items.TOTEM_OF_UNDYING);
	}

	public static boolean isElytra(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.GLIDER);
	}

	/** Chest-slot armour that is not a glider (i.e. a chestplate). */
	public static boolean isChestplate(ItemStack stack) {
		if (stack.isEmpty() || stack.has(DataComponents.GLIDER)) {
			return false;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
	}

	public static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(key)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	public static int breachLevel(ItemStack stack) {
		return enchantmentLevel(stack, Enchantments.BREACH);
	}

	public static int densityLevel(ItemStack stack) {
		return enchantmentLevel(stack, Enchantments.DENSITY);
	}
}
