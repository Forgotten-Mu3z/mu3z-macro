package net.altarsmp.testclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Block placement / interaction helpers. */
public final class BlockUtil {
	/** Neighbour order when looking for a face to place against: below first, like a normal floor placement. */
	private static final Direction[] PLACE_ORDER = {
			Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
	};

	private BlockUtil() {
	}

	/** Hit result that places a block at {@code target} by clicking a solid neighbour; null if none. */
	public static @Nullable BlockHitResult placementHit(BlockPos target) {
		ClientLevel level = Minecraft.getInstance().level;
		for (Direction dir : PLACE_ORDER) {
			BlockPos neighbour = target.relative(dir);
			BlockState state = level.getBlockState(neighbour);
			if (state.isAir() || state.canBeReplaced()) {
				continue;
			}
			Direction face = dir.getOpposite();
			Vec3 hit = Vec3.atCenterOf(neighbour).add(face.getUnitVec3().scale(0.5));
			return new BlockHitResult(hit, face, neighbour, false);
		}
		return null;
	}

	/** Hit result for clicking the top face of {@code pos}. */
	public static BlockHitResult topFaceHit(BlockPos pos) {
		return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), Direction.UP, pos, false);
	}

	/** Uses the held item on a block like a right click, including the client-side hand swing. */
	public static InteractionResult use(InteractionHand hand, BlockHitResult hit) {
		Minecraft mc = Minecraft.getInstance();
		InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
		if (result instanceof InteractionResult.Success success
				&& success.swingSource() == InteractionResult.SwingSource.CLIENT) {
			mc.player.swing(hand);
		}
		return result;
	}

	public static double eyeDistance(Vec3 point) {
		return Minecraft.getInstance().player.getEyePosition().distanceTo(point);
	}

	public static String describe(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
