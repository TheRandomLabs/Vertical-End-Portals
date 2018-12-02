package com.therandomlabs.verticalendportals.api.frame;

import net.minecraft.block.state.BlockWorldState;
import net.minecraft.world.World;

public abstract class SidedFrameDetector extends FrameDetector {
	private final FrameType defaultType;

	protected SidedFrameDetector(FrameType defaultType) {
		this.defaultType = defaultType;
	}

	@Override
	public FrameType getDefaultType() {
		return defaultType;
	}

	@Override
	protected final boolean test(World world, FrameType type, BlockWorldState state, FrameSide side,
			int position) {
		if(position == CORNER) {
			switch(side) {
			case TOP:
				return testTopLeftCorner(type, state);
			case RIGHT:
				return testTopRightCorner(type, state);
			case BOTTOM:
				return testBottomRightCorner(type, state);
			default:
				return testBottomLeftCorner(type, state);
			}
		}

		switch(side) {
		case TOP:
			return testTop(type, state, position);
		case RIGHT:
			return testRight(type, state, position);
		case BOTTOM:
			return testBottom(type, state, position);
		default:
			return testLeft(type, state, position);
		}
	}

	protected boolean testTopRightCorner(FrameType type, BlockWorldState state) {
		return true;
	}

	protected boolean testTopLeftCorner(FrameType type, BlockWorldState state) {
		return true;
	}

	protected boolean testBottomRightCorner(FrameType type, BlockWorldState state) {
		return true;
	}

	protected boolean testBottomLeftCorner(FrameType type, BlockWorldState state) {
		return true;
	}

	protected abstract boolean testTop(FrameType type, BlockWorldState state, int position);

	protected abstract boolean testRight(FrameType type, BlockWorldState state, int position);

	protected abstract boolean testBottom(FrameType type, BlockWorldState state, int position);

	protected abstract boolean testLeft(FrameType type, BlockWorldState state, int position);
}