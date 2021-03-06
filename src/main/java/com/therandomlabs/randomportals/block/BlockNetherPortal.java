package com.therandomlabs.randomportals.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.therandomlabs.randomportals.RandomPortals;
import com.therandomlabs.randomportals.advancements.RPOCriteriaTriggers;
import com.therandomlabs.randomportals.api.config.ColorData;
import com.therandomlabs.randomportals.api.config.EntitySpawns;
import com.therandomlabs.randomportals.api.config.FrameSize;
import com.therandomlabs.randomportals.api.config.PortalType;
import com.therandomlabs.randomportals.api.config.PortalTypes;
import com.therandomlabs.randomportals.api.config.SpawnRate;
import com.therandomlabs.randomportals.api.event.NetherPortalEvent;
import com.therandomlabs.randomportals.api.frame.Frame;
import com.therandomlabs.randomportals.api.frame.FrameDetector;
import com.therandomlabs.randomportals.api.frame.FrameType;
import com.therandomlabs.randomportals.api.netherportal.NetherPortal;
import com.therandomlabs.randomportals.api.netherportal.PortalBlockRegistry;
import com.therandomlabs.randomportals.api.util.FrameStatePredicate;
import com.therandomlabs.randomportals.client.RPOPortalRenderer;
import com.therandomlabs.randomportals.client.particle.ParticleRPOPortal;
import com.therandomlabs.randomportals.config.RPOConfig;
import com.therandomlabs.randomportals.frame.NetherPortalFrames;
import com.therandomlabs.randomportals.handler.NetherPortalTeleportHandler;
import com.therandomlabs.randomportals.world.storage.RPOSavedData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBeacon;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.ArrayUtils;

@Mod.EventBusSubscriber(modid = RandomPortals.MOD_ID)
public class BlockNetherPortal extends BlockPortal {
	public static final class Matcher {
		public static final FrameStatePredicate LATERAL = FrameStatePredicate.ofBlock(
				block -> block.getClass() == BlockLateralNetherPortal.class
		);

		public static final FrameStatePredicate VERTICAL_X = FrameStatePredicate.ofBlock(
				block -> block.getClass() == BlockNetherPortal.class
		).where(BlockNetherPortal.AXIS, axis -> axis == EnumFacing.Axis.X);

		public static final FrameStatePredicate VERTICAL_Z = FrameStatePredicate.ofBlock(
				block -> block.getClass() == BlockNetherPortal.class
		).where(BlockNetherPortal.AXIS, axis -> axis == EnumFacing.Axis.Z);

		private Matcher() {}

		public static FrameStatePredicate ofType(FrameType type) {
			return type.get(LATERAL, VERTICAL_X, VERTICAL_Z);
		}
	}

	public static final PropertyBool USER_PLACED = PropertyBool.create("user_placed");

	public static final AxisAlignedBB AABB_X = new AxisAlignedBB(
			0.0, 0.0, 0.375, 1.0, 1.0, 0.625
	);

	public static final AxisAlignedBB AABB_Y = new AxisAlignedBB(
			0.0, 0.375, 0.0, 1.0, 0.625, 1.0
	);

	public static final AxisAlignedBB AABB_Z = new AxisAlignedBB(
			0.375, 0.0, 0.0, 0.625, 1.0, 1.0
	);

	private static final EnumFacing[] xRelevantFacings = {
			EnumFacing.UP,
			EnumFacing.EAST,
			EnumFacing.DOWN,
			EnumFacing.WEST
	};

	private static final EnumFacing[] yRelevantFacings = {
			EnumFacing.NORTH,
			EnumFacing.EAST,
			EnumFacing.SOUTH,
			EnumFacing.WEST
	};

	private static final EnumFacing[] zRelevantFacings = {
			EnumFacing.UP,
			EnumFacing.NORTH,
			EnumFacing.DOWN,
			EnumFacing.SOUTH
	};

	private static final List<BlockPos> removing = new ArrayList<>();

	private static final Map<EnumDyeColor, BlockNetherPortal> colors =
			new EnumMap<>(EnumDyeColor.class);

	private final EnumDyeColor color;
	private final Class<?> clazz;

	public BlockNetherPortal(EnumDyeColor color) {
		this(
				color == EnumDyeColor.PURPLE ?
						"minecraft:portal" : color.getName() + "_vertical_nether_portal",
				color
		);

		final String translationKey = color.getTranslationKey();
		setTranslationKey(
				"netherPortalVertical" + Character.toUpperCase(translationKey.charAt(0)) +
						translationKey.substring(1)
		);

		if (!colors.containsKey(color)) {
			colors.put(color, this);
		}
	}

	protected BlockNetherPortal(String registryName, EnumDyeColor color) {
		setDefaultState(blockState.getBaseState().
				withProperty(AXIS, EnumFacing.Axis.X).
				withProperty(USER_PLACED, true));
		setTickRandomly(true);
		setHardness(-1.0F);
		setSoundType(SoundType.GLASS);
		setLightLevel(0.75F);
		setCreativeTab(CreativeTabs.DECORATIONS);
		setRegistryName(registryName);
		PortalBlockRegistry.register(this);
		this.color = color;
		clazz = getClass();
	}

	@SuppressWarnings("deprecation")
	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
		switch (getEffectiveAxis(state)) {
		case X:
			return AABB_X;
		case Y:
			return AABB_Y;
		default:
			return AABB_Z;
		}
	}

	@Override
	public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
		if (!world.getGameRules().getBoolean("doMobSpawning")) {
			return;
		}

		final NetherPortal portal = RPOSavedData.get(world).getNetherPortalByInner(pos);
		final PortalType portalType =
				portal == null ? PortalTypes.getDefault(world) : portal.getType();
		final EntitySpawns spawns =
				portalType.group.entitySpawns.get(world.provider.getDimension());

		if (spawns == null || spawns.entities.isEmpty() ||
				random.nextInt(spawns.rate) >= world.getDifficulty().getId()) {
			return;
		}

		final int minY = RandomPortals.CUBIC_CHUNKS_INSTALLED ? pos.getY() - 256 : 0;
		boolean found = false;

		while (pos.getY() > minY) {
			pos = pos.down();

			if (world.getBlockState(pos).isSideSolid(world, pos, EnumFacing.UP)) {
				found = true;
				break;
			}
		}

		if (!found || world.getBlockState(pos.up()).isNormalCube()) {
			return;
		}

		final SpawnRate spawnRate = spawns.getRandom(random);
		NBTTagCompound compound = null;

		try {
			compound = JsonToNBT.getTagFromJson(spawnRate.nbt);
		} catch (NBTException ignored) {}

		compound.setString("id", spawnRate.key);

		final float x = pos.getX() + 0.5F;
		final float y = pos.getY() + 1.1F;
		final float z = pos.getZ() + 0.5F;

		final Entity entity = AnvilChunkLoader.readWorldEntityPos(compound, world, x, y, z, true);

		if (entity == null) {
			return;
		}

		final EntityLiving living = entity instanceof EntityLiving ? (EntityLiving) entity : null;

		if (living != null && ForgeEventFactory.doSpecialSpawn(living, world, x, y, z, null)) {
			world.removeEntity(entity);
			return;
		}

		entity.setLocationAndAngles(
				x, y, z, MathHelper.wrapDegrees(random.nextFloat() * 360.0F), 0.0F
		);

		if (living != null) {
			living.rotationYawHead = entity.rotationYaw;
			living.renderYawOffset = entity.rotationYaw;
			living.onInitialSpawn(world.getDifficultyForLocation(new BlockPos(living)), null);
			living.playLivingSound();
		}

		entity.timeUntilPortal = entity.getPortalCooldown();
	}

	@Override
	public void neighborChanged(
			IBlockState state, World world, BlockPos pos, Block block,
			BlockPos fromPos
	) {
		if (removing.contains(fromPos)) {
			return;
		}

		final RPOSavedData savedData = RPOSavedData.get(world);
		NetherPortal portal = savedData.getNetherPortalByInner(pos);

		//If there is an activated portal here, then ignore userPlaced
		if (state.getValue(USER_PLACED) && portal == null) {
			return;
		}

		final EnumFacing.Axis axis = getEffectiveAxis(state);
		final IBlockState fromState = world.getBlockState(fromPos);

		if (fromState.getBlock() == this && !fromState.getValue(USER_PLACED) &&
				getEffectiveAxis(fromState) == axis) {
			return;
		}

		final EnumFacing irrelevantFacing = getIrrelevantFacing(axis);

		if (pos.offset(irrelevantFacing).equals(fromPos) ||
				pos.offset(irrelevantFacing.getOpposite()).equals(fromPos)) {
			return;
		}

		final Tuple<Boolean, NetherPortal> tuple;

		if (portal == null) {
			tuple = findFrame(world, pos);
		} else {
			tuple = new Tuple<>(true, portal);
		}

		if (tuple != null) {
			portal = tuple.getSecond();
			final Frame frame = portal.getFrame();

			//If the frame was retrieved from saved data (tuple.getFirst()),
			// the frame is not guaranteed to still exist, so we call PortalType.test
			//The following loop then ensures that the inner blocks are all portal blocks
			boolean shouldBreak = tuple.getFirst() && !portal.getType().test(frame);

			if (!shouldBreak) {
				for (BlockPos innerPos : frame.getInnerBlockPositions()) {
					final IBlockState innerState = world.getBlockState(innerPos);
					final Block innerBlock = innerState.getBlock();

					if (innerBlock.getClass() != clazz ||
							((BlockNetherPortal) innerBlock).getEffectiveAxis(innerState) != axis) {
						shouldBreak = true;
						break;
					}
				}
			}

			if (!shouldBreak) {
				return;
			}

			for (BlockPos innerPos : frame.getInnerBlockPositions()) {
				final IBlockState innerState = world.getBlockState(innerPos);
				final Block innerBlock = innerState.getBlock();

				if (innerBlock.getClass() == clazz &&
						((BlockNetherPortal) innerBlock).getEffectiveAxis(innerState) == axis) {
					removing.add(innerPos);
				}
			}

			savedData.removeNetherPortalByTopLeft(frame.getTopLeft());
		} else {
			removing.addAll(getConnectedPortals(
					world, pos, this, axis, state.getValue(USER_PLACED)
			));
		}

		for (BlockPos removePos : removing) {
			world.setBlockToAir(removePos);
		}

		removing.clear();
	}

	@SuppressWarnings("deprecation")
	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldSideBeRendered(
			IBlockState state, IBlockAccess world, BlockPos pos,
			EnumFacing side
	) {
		//I adapted this from vanilla and am assuming it is an optimization of some sort
		pos = pos.offset(side);
		EnumFacing.Axis axis = null;

		if (state.getBlock() == this) {
			axis = getEffectiveAxis(state);

			if (axis == null) {
				return false;
			}

			if (axis == EnumFacing.Axis.Z && side != EnumFacing.EAST && side != EnumFacing.WEST) {
				return false;
			}

			if (axis == EnumFacing.Axis.X && side != EnumFacing.SOUTH && side != EnumFacing.NORTH) {
				return false;
			}
		}

		final boolean west = world.getBlockState(pos.west()).getBlock() == this &&
				world.getBlockState(pos.west(2)).getBlock() != this;

		final boolean east = world.getBlockState(pos.east()).getBlock() == this &&
				world.getBlockState(pos.east(2)).getBlock() != this;

		final boolean north = world.getBlockState(pos.north()).getBlock() == this &&
				world.getBlockState(pos.north(2)).getBlock() != this;

		final boolean south = world.getBlockState(pos.south()).getBlock() == this &&
				world.getBlockState(pos.south(2)).getBlock() != this;

		final boolean x = west || east || axis == EnumFacing.Axis.X;
		final boolean z = north || south || axis == EnumFacing.Axis.Z;

		if (x) {
			return side == EnumFacing.WEST || side == EnumFacing.EAST;
		}

		return z && (side == EnumFacing.NORTH || side == EnumFacing.SOUTH);
	}

	@Override
	public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
		if (entity.isRiding() || entity.isBeingRidden() || !entity.isNonBoss()) {
			return;
		}

		final AxisAlignedBB aabb = entity.getEntityBoundingBox();

		if (!aabb.intersects(state.getBoundingBox(world, pos).offset(pos))) {
			return;
		}

		EnumDyeColor newColor = null;
		EntityItem dyeEntity = null;

		if (RPOConfig.NetherPortals.coloredPortals && RPOConfig.NetherPortals.dyeablePortals &&
				entity instanceof EntityItem) {
			dyeEntity = (EntityItem) entity;
			final ItemStack stack = dyeEntity.getItem();

			if (stack.getItem() == Items.DYE) {
				newColor = EnumDyeColor.byDyeDamage(stack.getMetadata());
			}
		}

		if (color == newColor) {
			if (RPOConfig.NetherPortals.consumeDyesEvenIfSameColor) {
				world.removeEntity(dyeEntity);
				return;
			}

			newColor = null;
		}

		if (world.isRemote) {
			if (newColor == null) {
				//On the client, the Nether portal logic is not changed
				entity.setPortal(pos);

				if (entity == Minecraft.getMinecraft().player) {
					RPOPortalRenderer.resetSprite(pos, this);
				}
			}

			return;
		}

		//Don't call findFrame because this method is called every tick an entity is in a portal
		final NetherPortal portal = RPOSavedData.get(world).getNetherPortalByInner(pos);
		final PortalType portalType =
				portal == null ? PortalTypes.getDefault(world) : portal.getType();

		if (newColor != null) {
			if (portalType.color.dyeBehavior == ColorData.DyeBehavior.DISABLE) {
				newColor = null;
			} else if (portalType.color.dyeBehavior == ColorData.DyeBehavior.ONLY_DEFINED_COLORS &&
					!ArrayUtils.contains(portalType.color.colors, newColor)) {
				if (RPOConfig.NetherPortals.consumeDyesEvenIfInvalidColor) {
					world.removeEntity(dyeEntity);
					return;
				}

				newColor = null;
			}
		}

		if (newColor == null) {
			NetherPortalTeleportHandler.setPortal(entity, portal, pos);
			return;
		}

		final List<BlockPos> relevantPositions = getRelevantPortalBlockPositions(
				world, pos, portal == null ? null : portal.getFrame()
		);

		final List<BlockPos> dyedPortalPositions = new ArrayList<>(relevantPositions.size());

		for (BlockPos portalPos : relevantPositions) {
			//Only replace blocks of the same color
			if (world.getBlockState(portalPos).getBlock() == this) {
				dyedPortalPositions.add(portalPos);
			}
		}

		final NetherPortalEvent.Dye.Pre event = new NetherPortalEvent.Dye.Pre(
				world, portal, dyedPortalPositions, color, newColor, dyeEntity
		);

		if (MinecraftForge.EVENT_BUS.post(event)) {
			return;
		}

		final IBlockState newState = getByColor(newColor).getDefaultState().
				withProperty(AXIS, state.getValue(AXIS)).
				withProperty(USER_PLACED, state.getValue(USER_PLACED));

		for (BlockPos portalPos : dyedPortalPositions) {
			world.setBlockState(portalPos, newState, 2);
		}

		world.removeEntity(entity);

		MinecraftForge.EVENT_BUS.post(new NetherPortalEvent.Dye.Post(
				world, portal, dyedPortalPositions, color, newColor, false
		));

		final String thrower = dyeEntity.getThrower();

		if (RPOConfig.Misc.advancements &&
				portalType.group.toString().equals(PortalTypes.VANILLA_NETHER_PORTAL_ID) &&
				thrower != null) {
			final EntityPlayerMP player =
					world.getMinecraftServer().getPlayerList().getPlayerByUsername(thrower);
			RPOCriteriaTriggers.DYED_NETHER_PORTAL.trigger(player, newColor, false);
		}
	}

	@Override
	public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
		return new ItemStack(this);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		final boolean userPlaced;

		if (meta > 2) {
			userPlaced = true;
			meta %= 3;
		} else {
			userPlaced = false;
		}

		return getDefaultState().
				withProperty(AXIS, meta == 1 ? EnumFacing.Axis.X : EnumFacing.Axis.Z).
				withProperty(USER_PLACED, userPlaced);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random random) {
		final int x = pos.getX();
		final int y = pos.getY();
		final int z = pos.getZ();

		if (random.nextInt(100) == 0) {
			world.playSound(
					x + 0.5,
					y + 0.5,
					z + 0.5,
					SoundEvents.BLOCK_PORTAL_AMBIENT,
					SoundCategory.BLOCKS,
					0.5F,
					random.nextFloat() * 0.4F + 0.8F,
					false
			);
		}

		for (int i = 0; i < 4; i++) {
			final double particleX;
			final double particleY = y + random.nextDouble();
			final double particleZ;

			final double xSpeed;
			final double ySpeed = (random.nextDouble() - 0.5) * 0.5;
			final double zSpeed;

			final int offset = random.nextInt(2) * 2 - 1;

			if (world.getBlockState(pos.west()).getBlock().getClass() != clazz &&
					world.getBlockState(pos.east()).getBlock().getClass() != clazz) {
				particleX = x + 0.5 + 0.25 * offset;
				particleZ = z + random.nextDouble();

				xSpeed = random.nextDouble() * 2.0 * offset;
				zSpeed = (random.nextDouble() - 0.5) * 0.5;
			} else {
				particleX = x + random.nextDouble();
				particleZ = z + 0.5 + 0.25 * offset;

				xSpeed = (random.nextDouble() - 0.5) * 0.5;
				zSpeed = random.nextDouble() * 2.0 * offset;
			}

			Minecraft.getMinecraft().effectRenderer.addEffect(new ParticleRPOPortal(
					world,
					particleX,
					particleY,
					particleZ,
					xSpeed,
					ySpeed,
					zSpeed,
					color
			));
		}
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		final int toAdd = state.getValue(USER_PLACED) ? 3 : 0;
		return super.getMetaFromState(state) + toAdd;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, AXIS, USER_PLACED);
	}

	@SuppressWarnings("deprecation")
	@Override
	public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
		return MapColor.getBlockColor(color);
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
		if (RPOConfig.NetherPortals.portalsContributeToBeaconColors && !world.isRemote) {
			BlockBeacon.updateColorAsync(world, pos);
		}
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		if (RPOConfig.NetherPortals.portalsContributeToBeaconColors && !world.isRemote) {
			BlockBeacon.updateColorAsync(world, pos);
		}
	}

	@Override
	public boolean onBlockActivated(
			World world, BlockPos pos, IBlockState state,
			EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY,
			float hitZ
	) {
		if (world.isRemote || !RPOConfig.NetherPortals.coloredPortals ||
				!RPOConfig.NetherPortals.dyeableSinglePortalBlocks) {
			return false;
		}

		final ItemStack stack = player.getHeldItem(hand);

		if (stack.getItem() != Items.DYE) {
			return false;
		}

		final EnumDyeColor newColor = EnumDyeColor.byDyeDamage(stack.getMetadata());

		if (color == newColor) {
			if (RPOConfig.NetherPortals.consumeDyesEvenIfInvalidColor &&
					!player.capabilities.isCreativeMode) {
				stack.shrink(1);
			}

			return false;
		}

		final Tuple<Boolean, NetherPortal> tuple = findFrame(world, pos);
		final NetherPortal portal = tuple == null ? null : tuple.getSecond();
		final PortalType portalType =
				portal == null ? PortalTypes.getDefault(world) : portal.getType();

		if (portalType.color.dyeBehavior == ColorData.DyeBehavior.DISABLE) {
			return false;
		}

		if (portalType.color.dyeBehavior == ColorData.DyeBehavior.ONLY_DEFINED_COLORS &&
				!ArrayUtils.contains(portalType.color.colors, newColor)) {
			if (RPOConfig.NetherPortals.consumeDyesEvenIfInvalidColor &&
					!player.capabilities.isCreativeMode) {
				stack.shrink(1);
			}

			return false;
		}

		final List<BlockPos> dyedPortalPositions = Lists.newArrayList(pos);

		final NetherPortalEvent.Dye.Pre event = new NetherPortalEvent.Dye.Pre(
				world, portal, dyedPortalPositions, color, newColor, null
		);

		if (MinecraftForge.EVENT_BUS.post(event)) {
			return false;
		}

		final IBlockState newState = getByColor(newColor).getDefaultState().
				withProperty(AXIS, state.getValue(AXIS)).
				withProperty(USER_PLACED, state.getValue(USER_PLACED));

		world.setBlockState(pos, newState, 2);

		if (RPOConfig.NetherPortals.consumeDyesEvenIfInvalidColor &&
				!player.capabilities.isCreativeMode) {
			stack.shrink(1);
		}

		MinecraftForge.EVENT_BUS.post(new NetherPortalEvent.Dye.Post(
				world, portal, dyedPortalPositions, color, newColor, true
		));

		if (RPOConfig.Misc.advancements &&
				portalType.group.toString().equals(PortalTypes.VANILLA_NETHER_PORTAL_ID)) {
			RPOCriteriaTriggers.DYED_NETHER_PORTAL.trigger((EntityPlayerMP) player, newColor,
					true);
		}

		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public IBlockState getStateForPlacement(
			World world, BlockPos pos, EnumFacing facing,
			float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer
	) {
		final EnumFacing.Axis axis = placer.getHorizontalFacing().getAxis();
		return getDefaultState().withProperty(
				AXIS,
				axis == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X
		);
	}

	@Override
	public boolean removedByPlayer(
			IBlockState state, World world, BlockPos pos,
			EntityPlayer player, boolean willHarvest
	) {
		final boolean actuallyRemoved =
				super.removedByPlayer(state, world, pos, player, willHarvest);

		if (actuallyRemoved && !world.isRemote) {
			RPOSavedData.get(world).removeNetherPortalByInner(pos);
		}

		return actuallyRemoved;
	}

	@Override
	public float[] getBeaconColorMultiplier(
			IBlockState state, World world, BlockPos pos,
			BlockPos beaconPos
	) {
		return RPOConfig.NetherPortals.portalsContributeToBeaconColors ?
				color.getColorComponentValues() : null;
	}

	public EnumFacing.Axis getEffectiveAxis(IBlockState state) {
		return state.getValue(AXIS);
	}

	public final EnumDyeColor getColor() {
		return color;
	}

	public BlockNetherPortal getByColor(EnumDyeColor color) {
		return get(color);
	}

	public static BlockNetherPortal get(EnumDyeColor color) {
		final BlockNetherPortal block = colors.get(color);
		return block == null ? (BlockNetherPortal) Blocks.PORTAL : block;
	}

	public static ImmutableList<BlockPos> getRelevantPortalBlockPositions(
			World world,
			BlockPos portalPos
	) {
		return getRelevantPortalBlockPositions(world, portalPos, null);
	}

	public static ImmutableList<BlockPos> getRelevantPortalBlockPositions(
			World world,
			BlockPos portalPos, Frame frame
	) {
		final IBlockState state = world.getBlockState(portalPos);
		final BlockNetherPortal block = (BlockNetherPortal) state.getBlock();
		final EnumFacing.Axis axis = block.getEffectiveAxis(state);

		if (frame == null) {
			final Tuple<Boolean, NetherPortal> tuple = findFrame(world, portalPos);

			if (tuple != null) {
				return tuple.getSecond().getFrame().getInnerBlockPositions();
			}
		} else {
			return frame.getInnerBlockPositions();
		}

		return getConnectedPortals(world, portalPos, block, axis, state.getValue(USER_PLACED));
	}

	public static Tuple<Boolean, NetherPortal> findFrame(World world, BlockPos portalPos) {
		return findFrame(NetherPortalFrames.FRAMES, world, portalPos);
	}

	public static Tuple<Boolean, NetherPortal> findFrame(
			FrameDetector detector, World world,
			BlockPos portalPos
	) {
		final RPOSavedData savedData = RPOSavedData.get(world);
		NetherPortal portal = savedData.getNetherPortalByInner(portalPos);

		if (portal != null) {
			return new Tuple<>(true, portal);
		}

		final IBlockState state = world.getBlockState(portalPos);
		final Block block = state.getBlock();

		if (!(state instanceof BlockNetherPortal)) {
			return null;
		}

		final EnumFacing.Axis axis = ((BlockNetherPortal) block).getEffectiveAxis(state);
		final EnumFacing frameDirection = axis == EnumFacing.Axis.Y ?
				EnumFacing.NORTH : EnumFacing.DOWN;

		final FrameType type = FrameType.fromAxis(axis);
		final FrameSize size = PortalTypes.getMaximumSize(type);
		final int maxSize = size.getMaxSize(frameDirection == EnumFacing.DOWN);

		final FrameStatePredicate portalMatcher = Matcher.ofType(type);

		BlockPos framePos = null;
		BlockPos checkPos = portalPos;

		for (int offset = 1; offset < maxSize - 1; offset++) {
			checkPos = checkPos.offset(frameDirection);

			final IBlockState checkState = world.getBlockState(checkPos);
			final Block checkBlock = checkState.getBlock();

			//If the frame block is a portal, the portal must be user-placed
			if (PortalTypes.getValidBlocks().test(world, checkPos, checkState) &&
					(!(checkBlock instanceof BlockNetherPortal) ||
							checkState.getValue(USER_PLACED))) {
				framePos = checkPos;
				break;
			}

			if (!portalMatcher.test(world, checkPos, checkState)) {
				break;
			}
		}

		if (framePos == null) {
			return null;
		}

		final Frame frame = detector.detectWithCondition(
				world, framePos, type,
				potentialFrame -> potentialFrame.isInnerBlock(portalPos)
		);

		if (frame == null) {
			return null;
		}

		portal = new NetherPortal(frame, null, PortalTypes.get(frame));
		savedData.addNetherPortal(portal, true);
		return new Tuple<>(false, portal);
	}

	private static ImmutableList<BlockPos> getConnectedPortals(
			World world, BlockPos portalPos,
			BlockNetherPortal block, EnumFacing.Axis axis, boolean userPlaced
	) {
		final List<BlockPos> positions = new ArrayList<>();
		final EnumFacing[] relevantFacings = getRelevantFacings(axis);

		positions.add(portalPos);
		int previousSize = 0;

		for (int i = 0; i < positions.size() || positions.size() != previousSize; i++) {
			previousSize = positions.size();
			final BlockPos removingPos = positions.get(i);

			for (EnumFacing facing : relevantFacings) {
				final BlockPos neighbor = removingPos.offset(facing);

				if (positions.contains(neighbor)) {
					continue;
				}

				final IBlockState neighborState = world.getBlockState(neighbor);

				if (neighborState.getBlock() == block &&
						block.getEffectiveAxis(neighborState) == axis &&
						neighborState.getValue(USER_PLACED) == userPlaced) {
					positions.add(neighbor);
				}
			}
		}

		return ImmutableList.copyOf(positions);
	}

	private static EnumFacing getIrrelevantFacing(EnumFacing.Axis axis) {
		if (axis == EnumFacing.Axis.X) {
			return EnumFacing.NORTH;
		}

		if (axis == EnumFacing.Axis.Y) {
			return EnumFacing.UP;
		}

		return EnumFacing.EAST;
	}

	private static EnumFacing[] getRelevantFacings(EnumFacing.Axis axis) {
		if (axis == EnumFacing.Axis.X) {
			return xRelevantFacings;
		}

		if (axis == EnumFacing.Axis.Y) {
			return yRelevantFacings;
		}

		return zRelevantFacings;
	}
}
