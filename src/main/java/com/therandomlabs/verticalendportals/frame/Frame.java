package com.therandomlabs.verticalendportals.frame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class Frame {
	private final World world;

	private final int width;
	private final int height;

	private final BlockPos topLeft;
	private final BlockPos topRight;
	private final BlockPos bottomLeft;
	private final BlockPos bottomRight;

	private final EnumFacing widthDirection;
	private final EnumFacing heightDirection;

	private final ImmutableList<BlockPos> topBlocks;
	private final ImmutableList<BlockPos> rightBlocks;
	private final ImmutableList<BlockPos> bottomBlocks;
	private final ImmutableList<BlockPos> leftBlocks;
	private final ImmutableList<BlockPos> innerBlocks;

	Frame(World world, Map<Integer, FrameDetector.Corner> corners, EnumFacing[] facings) {
		this.world = world;

		final FrameDetector.Corner topLeftCorner = corners.get(0);
		final FrameDetector.Corner rightCorner = corners.get(1);

		width = topLeftCorner.sideLength;
		height = rightCorner.sideLength;

		topLeft = topLeftCorner.pos;
		topRight = rightCorner.pos;
		bottomLeft = corners.get(3).pos;
		bottomRight = corners.get(2).pos;

		widthDirection = facings[0];
		heightDirection = facings[1];

		final List<BlockPos> topBlocks = new ArrayList<>(width);

		for(int width = 0; width < this.width; width++) {
			topBlocks.add(
					topLeft.offset(widthDirection, width)
			);
		}

		this.topBlocks = ImmutableList.copyOf(topBlocks);

		final List<BlockPos> rightBlocks = new ArrayList<>(height);

		for(int height = 0; height < this.height; height++) {
			rightBlocks.add(
					topLeft.offset(widthDirection, width - 1).
							offset(heightDirection, height)
			);
		}

		this.rightBlocks = ImmutableList.copyOf(rightBlocks);

		final List<BlockPos> bottomBlocks = new ArrayList<>(width);

		for(int width = 0; width < this.width; width++) {
			bottomBlocks.add(
					topLeft.offset(widthDirection, width).
							offset(heightDirection, height - 1)
			);
		}

		this.bottomBlocks = ImmutableList.copyOf(bottomBlocks);

		final List<BlockPos> leftBlocks = new ArrayList<>(height);

		for(int height = 0; height < this.height; height++) {
			leftBlocks.add(
					topLeft.offset(heightDirection, height)
			);
		}

		this.leftBlocks = ImmutableList.copyOf(leftBlocks);

		final List<BlockPos> innerBlocks = new ArrayList<>((width - 2) * (height - 2));

		for(int width = 1; width < this.width - 1; width++) {
			for(int height = 1; height < this.height - 1; height++) {
				innerBlocks.add(
						topLeft.offset(widthDirection, width).
								offset(heightDirection, height)
				);
			}
		}

		this.innerBlocks = ImmutableList.copyOf(innerBlocks);
	}

	public World getWorld() {
		return world;
	}

	public boolean isLateral() {
		return heightDirection == EnumFacing.SOUTH;
	}

	public boolean isVertical() {
		return heightDirection == EnumFacing.DOWN;
	}

	public boolean isVerticalX() {
		return widthDirection == EnumFacing.EAST && heightDirection == EnumFacing.DOWN;
	}

	public boolean isVerticalY() {
		return widthDirection == EnumFacing.NORTH && heightDirection == EnumFacing.DOWN;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public BlockPos getTopLeft() {
		return topLeft;
	}

	public BlockPos getTopRight() {
		return topRight;
	}

	public BlockPos getBottomLeft() {
		return bottomLeft;
	}

	public BlockPos getBottomRight() {
		return bottomRight;
	}

	public EnumFacing getWidthDirection() {
		return widthDirection;
	}

	public EnumFacing getHeightDirection() {
		return heightDirection;
	}

	public boolean isCorner(BlockPos pos) {
		return topLeft.equals(pos) || topRight.equals(pos) || bottomLeft.equals(pos) ||
				bottomRight.equals(pos);
	}

	public boolean isTopBlock(BlockPos pos) {
		return isSide(pos, topLeft, topRight);
	}

	public ImmutableList<BlockPos> getTopBlockPositions() {
		return topBlocks;
	}

	public List<IBlockState> getTopBlocks() {
		return topBlocks.stream().map(world::getBlockState).collect(Collectors.toList());
	}

	public boolean isRightBlock(BlockPos pos) {
		return isSide(pos, topRight, bottomRight);
	}

	public ImmutableList<BlockPos> getRightBlockPositions() {
		return rightBlocks;
	}

	public List<IBlockState> getRightBlocks() {
		return rightBlocks.stream().map(world::getBlockState).collect(Collectors.toList());
	}

	public boolean isBottomBlock(BlockPos pos) {
		return isSide(pos, bottomRight, bottomLeft);
	}

	public ImmutableList<BlockPos> getBottomBlockPositions() {
		return bottomBlocks;
	}

	public List<IBlockState> getBottomBlocks() {
		return bottomBlocks.stream().map(world::getBlockState).collect(Collectors.toList());
	}

	public boolean isLeftBlock(BlockPos pos) {
		return isSide(pos, bottomLeft, topLeft);
	}

	public ImmutableList<BlockPos> getLeftBlockPositions() {
		return leftBlocks;
	}

	public List<IBlockState> getLeftBlocks() {
		return leftBlocks.stream().map(world::getBlockState).collect(Collectors.toList());
	}

	public boolean isInnerBlock(BlockPos pos) {
		return !isTopBlock(pos) && !isRightBlock(pos) && !isBottomBlock(pos) &&
				!isLeftBlock(pos);
	}

	public ImmutableList<BlockPos> getInnerBlockPositions() {
		return innerBlocks;
	}

	public List<IBlockState> getInnerBlocks() {
		return innerBlocks.stream().map(world::getBlockState).collect(Collectors.toList());
	}

	public boolean isEmpty() {
		for(IBlockState state : getInnerBlocks()) {
			if(state.getBlock() != Blocks.AIR) {
				return false;
			}
		}

		return true;
	}

	private boolean isSide(BlockPos pos, BlockPos corner1, BlockPos corner2) {
		final int corner1X = corner1.getX();
		final int corner1Y = corner1.getY();
		final int corner1Z = corner1.getZ();

		final int corner2X = corner2.getX();
		final int corner2Y = corner2.getY();
		final int corner2Z = corner2.getZ();

		final int minX = Math.min(corner1X, corner2X);
		final int minY = Math.min(corner1Y, corner2Y);
		final int minZ = Math.min(corner1Z, corner2Z);

		final int maxX = Math.max(corner1X, corner2X);
		final int maxY = Math.max(corner1Y, corner2Y);
		final int maxZ = Math.max(corner1Z, corner2Z);

		final int x = pos.getX();
		final int y = pos.getY();
		final int z = pos.getZ();

		return x >= minX && y >= minY && z >= minZ && x <= maxX && y <= maxY && z <= maxZ;
	}
}
