package net.altarsmp.testclient.module.combat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.ClientTicks;
import net.altarsmp.testclient.util.ServerGuard;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Jump Reset: when you get hit while on the ground, jumps on the tick the knockback arrives, so the jump
 * cancels part of the knockback (the manual PvP "jump reset", done automatically).
 *
 * <p>The knockback packet for your own player is caught in {@code ClientPacketListenerMixin}. Packets are
 * handled between client ticks, so pressing the jump key right then makes vanilla jump during the very next
 * player tick: the same tick the knockback velocity is applied. The key is released at the end of that tick,
 * unless you are physically holding it.
 *
 * <p>Blatant: jumps on every hit taken on the ground, exactly on the knockback tick (no misses). Humanlike:
 * jumps on "Success chance" % of hits, sometimes a tick or more late, the way a person's timing varies.
 */
public final class JumpReset extends Module {
	private final IntSetting chance = add(new IntSetting("chance", "Success chance",
			"Humanlike: percentage of hits that get a jump reset", 80, 0, 100, "%"));
	private final IntSetting maxLateTicks = add(new IntSetting("maxLateTicks", "Max late ticks",
			"Humanlike: a jump may come up to this many ticks after the knockback", 1, 0, 5, ""));

	/** Humanlike: press the key at the end of this client tick (-1 = nothing scheduled). */
	private long pressAtTick = -1;
	private String pendingDetail = "";
	/** We are holding the jump key since this client tick (-1 = not holding). */
	private long pressedAtTick = -1;

	public JumpReset() {
		super("jump_reset", "Jump Reset", "Jumps on the knockback tick when you get hit, to cancel knockback", Kind.TOGGLE);
	}

	/** Called by {@code CombatHooks} when the server sets our own velocity (knockback). */
	public void onKnockback(Vec3 velocity) {
		LocalPlayer player = mc().player;
		if (!isEnabled() || !ServerGuard.isAllowed() || player == null) {
			return;
		}
		// Only react to knockback from a hit that just landed (damage event sets hurtTime to hurtDuration).
		if (player.hurtTime <= 0 || player.hurtTime < player.hurtDuration - 2) {
			return;
		}
		String knockback = String.format(Locale.ROOT, "kb %.3f hurtTime %d", velocity.horizontalDistance(), player.hurtTime);
		String skip = skipReason(player);
		if (skip != null) {
			log("SKIP", "", skip + ", " + knockback);
			return;
		}
		if (blatant()) {
			press(knockback + ", late 0t");
			return;
		}
		if (ThreadLocalRandom.current().nextInt(100) >= chance.get()) {
			log("MISS", "", "chance roll, " + knockback);
			return;
		}
		int late = maxLateTicks.get() <= 0 ? 0 : ThreadLocalRandom.current().nextInt(maxLateTicks.get() + 1);
		if (late == 0) {
			press(knockback + ", late 0t");
		} else {
			pressAtTick = ClientTicks.now() + late;
			pendingDetail = knockback + ", late " + late + "t";
		}
	}

	@Override
	public boolean runsWithScreenOpen() {
		// Always get the chance to release the key.
		return true;
	}

	@Override
	public void onTick() {
		if (pressAtTick >= 0 && ClientTicks.now() >= pressAtTick) {
			pressAtTick = -1;
			String skip = skipReason(mc().player);
			if (skip == null) {
				press(pendingDetail);
			} else {
				log("SKIP", "", skip + " by the time the late jump was due, " + pendingDetail);
			}
		}
		// The press is read by the player tick after it; release once that tick has run.
		if (pressedAtTick >= 0 && ClientTicks.now() > pressedAtTick) {
			release();
		}
	}

	private @Nullable String skipReason(LocalPlayer player) {
		if (mc().screen != null) {
			return "screen open";
		}
		if (!player.onGround()) {
			return "airborne";
		}
		if (player.isInWater() || player.isInLava()) {
			return "in liquid";
		}
		if (player.isPassenger()) {
			return "riding";
		}
		if (player.isFallFlying()) {
			return "gliding";
		}
		return null;
	}

	private void press(String detail) {
		KeyMapping jump = mc().options.keyJump;
		if (jump.isDown()) {
			log("JUMP", "", detail + " (jump key already held)");
			return;
		}
		jump.setDown(true);
		pressedAtTick = ClientTicks.now();
		log("JUMP", "", detail);
	}

	private void release() {
		if (!physicallyHeld()) {
			mc().options.keyJump.setDown(false);
		}
		pressedAtTick = -1;
	}

	/** Whether the player is holding the jump key themselves (keyboard keys only). */
	private boolean physicallyHeld() {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mc().options.keyJump);
		return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc().getWindow(), key.getValue());
	}

	@Override
	public void resetState() {
		pressAtTick = -1;
		if (pressedAtTick >= 0) {
			release();
		}
	}
}
