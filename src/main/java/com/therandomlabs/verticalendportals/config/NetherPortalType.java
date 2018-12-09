package com.therandomlabs.verticalendportals.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.therandomlabs.verticalendportals.api.frame.Frame;
import com.therandomlabs.verticalendportals.api.frame.RequiredCorner;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class NetherPortalType {
	public List<FrameBlock> frameBlocks;
	public RequiredCorner requiredCorner;
	public boolean cornerBlocksContributeToMinimumAmount;

	public boolean doGeneratedFramesDrop;

	public boolean whitelist; //Whether dimensions is a whitelist or a blacklist
	public List<Integer> dimensions = new ArrayList<>();

	public int dimensionID;

	public boolean forcePortal; //In the End

	public int minWidth = 3;
	public int maxWidth = 9000;
	public int minHeight = 3;
	public int maxHeight = 9000;

	transient String name;

	public NetherPortalType() {}

	public NetherPortalType(List<FrameBlock> frameBlocks, int dimensionID) {
		this.frameBlocks = frameBlocks;
		this.dimensionID = dimensionID;
	}

	@Override
	public String toString() {
		return "NetherPortalType[name=" + getName() + ",frameBlocks=" + frameBlocks +
				"],dimensionID=" + dimensionID + "]";
	}

	@SuppressWarnings("Duplicates")
	public void ensureCorrect() {
		for(int i = 0; i < frameBlocks.size(); i++) {
			final FrameBlock frameBlock = frameBlocks.get(i);

			if(!frameBlock.isValid()) {
				frameBlocks.remove(i--);
				continue;
			}

			frameBlock.ensureCorrect();
		}

		if(minWidth < 3) {
			minWidth = 3;
		}

		if(minHeight < 3) {
			minHeight = 3;
		}

		if(maxWidth < minWidth) {
			maxWidth = minWidth;
		}

		if(maxHeight < minHeight) {
			maxHeight = minHeight;
		}
	}

	public String getName() {
		return name == null ? "unknown_type" : name;
	}

	public boolean test(Frame frame) {
		final World world = frame.getWorld();
		final int dimension = world.provider.getDimension();

		if(whitelist) {
			if(!dimensions.contains(dimension)) {
				return false;
			}
		} else {
			if(dimensions.contains(dimension)) {
				return false;
			}
		}

		final int width = frame.getWidth();
		final int height = frame.getHeight();

		if(width < minWidth || width > maxWidth || height < minHeight || height > maxHeight) {
			return false;
		}

		final Map<FrameBlock, Integer> detectedBlocks = new HashMap<>();

		for(BlockPos pos : frame.getInnerBlockPositions()) {
			final IBlockState state = world.getBlockState(pos);
			final boolean corner = frame.isCorner(pos);
			boolean found = false;

			if(corner) {
				found = requiredCorner.test(world, pos, state);

				if(found && !cornerBlocksContributeToMinimumAmount) {
					continue;
				}
			}

			for(FrameBlock block : frameBlocks) {
				if(block.test(state)) {
					if(cornerBlocksContributeToMinimumAmount) {
						detectedBlocks.merge(block, 1, (a, b) -> a + b);
					}

					found = true;
					break;
				}
			}

			if(!found) {
				return false;
			}
		}

		for(FrameBlock block : frameBlocks) {
			if(block.minimumAmount == 0) {
				continue;
			}

			final Integer detectedAmount = detectedBlocks.get(block);

			if(detectedAmount == null || detectedAmount < block.minimumAmount) {
				return false;
			}
		}

		return true;
	}
}
