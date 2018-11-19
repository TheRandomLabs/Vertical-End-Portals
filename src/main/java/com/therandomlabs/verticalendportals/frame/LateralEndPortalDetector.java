package com.therandomlabs.verticalendportals.frame;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockWorldState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import static net.minecraft.block.BlockHorizontal.FACING;

public final class LateralEndPortalDetector extends SidedFrameDetector {
	private final Block block;

	public LateralEndPortalDetector(Block block) {
		super(Type.LATERAL);
		this.block = block;
	}

	@Override
	protected boolean testTop(Type type, BlockWorldState state, int position) {
		return test(state, EnumFacing.SOUTH);
	}

	@Override
	protected boolean testRight(Type type, BlockWorldState state, int position) {
		return test(state, EnumFacing.WEST);
	}

	@Override
	protected boolean testBottom(Type type, BlockWorldState state, int position) {
		return test(state, EnumFacing.NORTH);
	}

	@Override
	protected boolean testLeft(Type type, BlockWorldState state, int position) {
		return test(state, EnumFacing.EAST);
	}

	@Override
	protected boolean test(Frame frame) {
		return true;
	}

	private boolean test(BlockWorldState state, EnumFacing facing) {
		final IBlockState blockState = state.getBlockState();
		return blockState.getBlock() == block && blockState.getValue(FACING) == facing;
	}
}
