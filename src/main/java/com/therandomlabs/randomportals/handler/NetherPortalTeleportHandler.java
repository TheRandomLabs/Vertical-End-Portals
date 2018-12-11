package com.therandomlabs.randomportals.handler;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.therandomlabs.randomportals.api.config.NetherPortalType;
import com.therandomlabs.randomportals.api.config.NetherPortalTypes;
import com.therandomlabs.randomportals.api.event.NetherPortalEvent;
import com.therandomlabs.randomportals.api.netherportal.NetherPortal;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class NetherPortalTeleportHandler {
	public static class TeleportData {
		private NetherPortal portal;
		private BlockPos pos;
		private EnumFacing originalEntityFacing;

		private TeleportData(NetherPortal portal, BlockPos pos, EnumFacing originalEntityFacing) {
			this.portal = portal;
			this.pos = pos;
			this.originalEntityFacing = originalEntityFacing;
		}

		public NetherPortal getPortal() {
			return portal;
		}

		public NetherPortalType getPortalType() {
			return portal == null ? NetherPortalTypes.getDefault() : portal.getType();
		}

		public BlockPos getPos() {
			return pos;
		}

		public EnumFacing getOriginalEntityFacing() {
			return originalEntityFacing;
		}
	}

	private static final Map<WeakReference<Entity>, TeleportData> entities = new HashMap<>();
	private static final Map<WeakReference<Entity>, NetherPortalType> types = new HashMap<>();

	public static TeleportData getTeleportData(Entity entity) {
		for(Map.Entry<WeakReference<Entity>, TeleportData> entry : entities.entrySet()) {
			if(entry.getKey().get() == entity) {
				return entry.getValue();
			}
		}

		return null;
	}

	public static void setPortal(Entity entity, NetherPortal portal, BlockPos pos) {
		final World world = entity.getEntityWorld();

		if(!world.getMinecraftServer().getAllowNether()) {
			return;
		}

		if(entity.timeUntilPortal > 0) {
			entity.timeUntilPortal = entity.getPortalCooldown();
			return;
		}

		boolean found = false;

		for(Map.Entry<WeakReference<Entity>, TeleportData> entry : entities.entrySet()) {
			final Entity referencedEntity = entry.getKey().get();

			if(referencedEntity == entity) {
				final TeleportData data = entry.getValue();

				data.portal = portal;
				data.pos = pos;

				found = true;
				break;
			}
		}

		entity.lastPortalPos = pos;

		if(!found) {
			entities.put(
					new WeakReference<>(entity),
					new TeleportData(portal, pos, entity.getHorizontalFacing())
			);
		}
	}

	public static NetherPortalType getPortalType(Entity entity) {
		final List<WeakReference<Entity>> toRemove = new ArrayList<>();
		NetherPortalType type = null;

		for(Map.Entry<WeakReference<Entity>, NetherPortalType> entry : types.entrySet()) {
			final WeakReference<Entity> entityReference = entry.getKey();
			final Entity referencedEntity = entityReference.get();

			if(referencedEntity == null) {
				toRemove.add(entityReference);
			} else if(referencedEntity == entity) {
				type = entry.getValue();
			}
		}

		types.keySet().removeAll(toRemove);

		return type;
	}

	public static void clearPortalType(Entity entity) {
		final List<WeakReference<Entity>> toRemove = new ArrayList<>();

		for(Map.Entry<WeakReference<Entity>, NetherPortalType> entry : types.entrySet()) {
			final WeakReference<Entity> entityReference = entry.getKey();
			final Entity referencedEntity = entityReference.get();

			if(referencedEntity == null || referencedEntity == entity) {
				toRemove.add(entityReference);
			}
		}

		types.keySet().removeAll(toRemove);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if(event.phase != TickEvent.Phase.END) {
			return;
		}

		for(Map.Entry<WeakReference<Entity>, TeleportData> entry : entities.entrySet()) {
			final Entity entity = entry.getKey().get();

			if(entity != null) {
				handle(entity, entry.getValue(), entity.getEntityWorld().provider.getDimension());
			}
		}

		entities.clear();
	}

	private static void handle(Entity entity, TeleportData data, int dimension) {
		if(entity.isRiding()) {
			return;
		}

		final int maxInPortalTime = entity.getMaxInPortalTime();

		if(entity.portalCounter++ < maxInPortalTime) {
			//Entity decrements this by 4 every tick because inPortal is false
			entity.portalCounter += 4;
			return;
		}

		entity.portalCounter += 4;

		entity.portalCounter = maxInPortalTime;
		entity.timeUntilPortal = entity.getPortalCooldown();

		final NetherPortalEvent.Teleport event = new NetherPortalEvent.Teleport(
				data.portal, entity, data.pos, data.originalEntityFacing
		);

		if(MinecraftForge.EVENT_BUS.post(event)) {
			return;
		}

		final NetherPortalType type = data.getPortalType();
		types.put(new WeakReference<>(entity), type);
		entity.changeDimension(dimension == type.dimensionID ? 0 : type.dimensionID);
	}
}