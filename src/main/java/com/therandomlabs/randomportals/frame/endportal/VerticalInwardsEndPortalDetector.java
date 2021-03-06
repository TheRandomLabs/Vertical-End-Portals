package com.therandomlabs.randomportals.frame.endportal;

import java.util.function.Function;
import com.therandomlabs.randomportals.api.config.FrameSize;
import com.therandomlabs.randomportals.api.frame.Frame;
import com.therandomlabs.randomportals.api.frame.FrameType;
import com.therandomlabs.randomportals.api.frame.detector.SidedFrameDetector;
import com.therandomlabs.randomportals.block.RPOBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.block.BlockEndPortalFrame.EYE;
import static net.minecraft.block.BlockHorizontal.FACING;

public final class VerticalInwardsEndPortalDetector extends SidedFrameDetector {
	VerticalInwardsEndPortalDetector() {
		super(FrameType.VERTICAL);
	}

	@Override
	public Function<FrameType, FrameSize> getDefaultSize() {
		return EndPortalFrames.VERTICAL_INWARDS_FACING_SIZE;
	}

	@Override
	protected boolean test(Frame frame) {
		return true;
	}

	@Override
	protected boolean testTop(FrameType type, BlockPos pos, IBlockState state, int position) {
		return test(
				type, state, RPOBlocks.upside_down_end_portal_frame, null, null
		);
	}

	@Override
	protected boolean testRight(FrameType type, BlockPos pos, IBlockState state, int position) {
		return test(
				type, state, RPOBlocks.vertical_end_portal_frame, EnumFacing.WEST, EnumFacing.SOUTH
		);
	}

	@Override
	protected boolean testBottom(FrameType type, BlockPos pos, IBlockState state, int position) {
		return test(
				type, state, Blocks.END_PORTAL_FRAME, null, null
		);
	}

	@Override
	protected boolean testLeft(FrameType type, BlockPos pos, IBlockState state, int position) {
		return test(
				type, state, RPOBlocks.vertical_end_portal_frame, EnumFacing.EAST, EnumFacing.NORTH
		);
	}

	private boolean test(
			FrameType type, IBlockState state, Block block, EnumFacing facingX,
			EnumFacing facingY
	) {
		final boolean result = state.getBlock() == block && state.getValue(EYE);

		if (facingX == null) {
			return result;
		}

		final EnumFacing facing = type == FrameType.VERTICAL_X ? facingX : facingY;
		return result && state.getValue(FACING) == facing;
	}
}
