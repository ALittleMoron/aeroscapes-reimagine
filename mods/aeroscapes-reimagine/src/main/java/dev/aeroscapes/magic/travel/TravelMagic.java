package dev.aeroscapes.magic.travel;

import dev.aeroscapes.mixin.magic.travel.PocketDimensionManagerAccess;
import dev.aeroscapes.mixin.magic.travel.PortalSpellAccess;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity;
import io.redspace.ironsspellbooks.capabilities.magic.PocketDimensionManager;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalData;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalEntity;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.spells.ender.PortalSpell;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class TravelMagic {
  private static final String ROOT = "aeroscapes_reimagine_travel_magic";
  private static final String WINGS_READY = "wings_ready_at";
  private static final String WINGS_EXPIRES = "wings_expires_at";
  private static final String PORTAL_READY = "portal_ready_at";
  private static final String PORTAL_EXPIRES = "aeroscapes_reimagine:portal_expires_at";

  private TravelMagic() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(TravelMagic::checkCast);
    NeoForge.EVENT_BUS.addListener(TravelMagic::clonePlayer);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TravelMagic::checkWingsApplication);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TravelMagic::recordWingsApplication);
    NeoForge.EVENT_BUS.addListener(TravelMagic::expireWings);
  }

  public static boolean canCast(
      AbstractSpell spell, ServerPlayer player, MagicData magicData, boolean feedback) {
    if (spell != SpellRegistry.ANGEL_WINGS_SPELL.get() && spell != SpellRegistry.PORTAL_SPELL.get())
      return true;
    if (cooldownTicks(player, spell) > 0)
      return reject(player, "message.aeroscapes_reimagine.travel_cooldown", feedback);
    if (spell instanceof PortalSpell portal) {
      String failure = portalFailure(portal, player, magicData);
      if (failure != null) return reject(player, failure, feedback);
    }
    return true;
  }

  public static int cooldownTicks(ServerPlayer player, AbstractSpell spell) {
    String key;
    if (spell == SpellRegistry.ANGEL_WINGS_SPELL.get()) key = WINGS_READY;
    else if (spell == SpellRegistry.PORTAL_SPELL.get()) key = PORTAL_READY;
    else return 0;
    return TravelMagicPolicy.remainingTicks(time(player.serverLevel()), state(player).getLong(key));
  }

  public static void portalPlaced(ServerPlayer player, PortalData data, int pendingDurationTicks) {
    ServerLevel level = player.serverLevel();
    if (data.isBlock || data.globalPos1 == null || data.portalEntityId1 == null) return;
    Entity first = level.getEntity(data.portalEntityId1);
    if (!(first instanceof PortalEntity) || first.isRemoved()) return;
    if (data.globalPos2 == null || data.portalEntityId2 == null) {
      first.getPersistentData().putLong(PORTAL_EXPIRES, time(level) + pendingDurationTicks);
      return;
    }
    Entity second = level.getEntity(data.portalEntityId2);
    if (!(second instanceof PortalEntity) || second.isRemoved()) return;
    long now = time(level);
    long expiresAt = now + Math.clamp(data.ticksToLive, 0, TravelMagicPolicy.PORTAL_DURATION_TICKS);
    first.getPersistentData().putLong(PORTAL_EXPIRES, expiresAt);
    second.getPersistentData().putLong(PORTAL_EXPIRES, expiresAt);
    write(player, PORTAL_READY, now + TravelMagicPolicy.PORTAL_COOLDOWN_TICKS);
  }

  public static boolean portalExpired(PortalEntity portal) {
    if (!(portal.level() instanceof ServerLevel level)) return false;
    CompoundTag data = portal.getPersistentData();
    return data.contains(PORTAL_EXPIRES) && data.getLong(PORTAL_EXPIRES) <= time(level);
  }

  public static boolean canUsePortal(UUID portalId, Entity traveler) {
    if (!(traveler.level() instanceof ServerLevel level)) return false;
    PortalData data = PortalManager.INSTANCE.getPortalData(portalId);
    if (data != null && isPersonalRoomExit(portalId, traveler, level, data)) return true;
    if (data == null
        || data.isBlock
        || data.globalPos1 == null
        || data.globalPos2 == null
        || !TravelMagicPolicy.portalLinkAllowed(
            data.globalPos1.dimension().equals(data.globalPos2.dimension()),
            data.globalPos1.pos().distanceToSqr(data.globalPos2.pos()))
        || !level.dimension().equals(data.globalPos1.dimension())) return false;
    Entity source = level.getEntity(portalId);
    return source instanceof PortalEntity
        && !source.isRemoved()
        && source.getPersistentData().contains(PORTAL_EXPIRES)
        && source.getPersistentData().getLong(PORTAL_EXPIRES) > time(level);
  }

  private static boolean isPersonalRoomExit(
      UUID portalId, Entity traveler, ServerLevel level, PortalData data) {
    if (!(traveler instanceof ServerPlayer player)
        || !level.dimension().equals(PocketDimensionManager.POCKET_DIMENSION)
        || data.isBlock
        || !player.getUUID().equals(data.portalEntityId1)
        || !portalId.equals(data.portalEntityId2)
        || data.globalPos1 == null
        || data.globalPos2 == null
        || data.globalPos1.dimension().equals(PocketDimensionManager.POCKET_DIMENSION)
        || !data.globalPos2.dimension().equals(PocketDimensionManager.POCKET_DIMENSION)
        || level.getServer().getLevel(data.globalPos1.dimension()) == null) return false;
    PocketDimensionManager rooms = PocketDimensionManager.INSTANCE;
    var owners = ((PocketDimensionManagerAccess) rooms).aeroscapes$pocketIds();
    if (!owners.containsKey(player.getUUID())) return false;
    int roomId = owners.getInt(player.getUUID());
    if (roomId < 0) return false;
    BlockPos framePosition = rooms.findPortalForStructure(level, rooms.structurePosForId(roomId));
    // Pocket Dimension records the owner as its origin endpoint, not a temporary portal entity.
    return data.globalPos2.pos().equals(framePosition.getBottomCenter())
        && level.getBlockState(framePosition).is(BlockRegistry.POCKET_PORTAL_FRAME.get())
        && level.getBlockEntity(framePosition) instanceof PortalFrameBlockEntity frame
        && portalId.equals(frame.getUUID());
  }

  private static String portalFailure(PortalSpell spell, ServerPlayer player, MagicData magicData) {
    ServerLevel level = player.serverLevel();
    RecastInstance recast =
        magicData.getPlayerRecasts().hasRecastForSpell(spell)
            ? magicData.getPlayerRecasts().getRecastInstance(spell.getSpellId())
            : null;
    float reach =
        ((PortalSpellAccess) spell)
            .aeroscapes$castDistance(magicData.getCastingSpellLevel(), player);
    BlockHitResult hit = PortalSpell.getTargetBlock(level, player, ClipContext.Fluid.NONE, reach);
    if (recast == null) {
      if (hit.getType() != HitResult.Type.MISS
          && level.getBlockEntity(hit.getBlockPos()) instanceof PortalFrameBlockEntity frame
          && !frame.isPortalConnected())
        return "message.aeroscapes_reimagine.portal_frame_disabled";
      return null;
    }
    if (!(recast.getCastData() instanceof PortalData data) || data.isBlock)
      return "message.aeroscapes_reimagine.portal_frame_disabled";
    if (data.globalPos1 == null
        || data.portalEntityId1 == null
        || data.globalPos2 != null
        || data.portalEntityId2 != null)
      return "message.aeroscapes_reimagine.portal_missing_endpoint";
    if (!level.dimension().equals(data.globalPos1.dimension()))
      return "message.aeroscapes_reimagine.portal_local_only";
    Entity first = level.getEntity(data.portalEntityId1);
    if (!(first instanceof PortalEntity portal)
        || first.isRemoved()
        || portal.getPortalConnected()
        || portalExpired(portal)
        || PortalManager.INSTANCE.getPortalData(data.portalEntityId1) == null)
      return "message.aeroscapes_reimagine.portal_missing_endpoint";
    Vec3 destination = portalLocation(level, player, hit);
    return TravelMagicPolicy.portalLinkAllowed(
            true, data.globalPos1.pos().distanceToSqr(destination))
        ? null
        : "message.aeroscapes_reimagine.portal_local_only";
  }

  private static Vec3 portalLocation(Level level, Player player, BlockHitResult hit) {
    // Match the provider's actual endpoint placement, rather than its 48-block aiming ray.
    Vec3 hitPosition =
        hit.getLocation().subtract(player.getForward().normalize().multiply(0.25, 0, 0.25));
    return level
        .clip(
            new ClipContext(
                hitPosition,
                hitPosition.add(0, -player.getBbHeight() - 1, 0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player))
        .getLocation()
        .add(0, 0.076, 0);
  }

  private static void checkCast(SpellPreCastEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && !canCast(
            SpellRegistry.getSpell(event.getSpellId()),
            player,
            MagicData.getPlayerMagicData(player),
            true)) event.setCanceled(true);
  }

  private static void checkWingsApplication(MobEffectEvent.Applicable event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getEffectInstance().is(MobEffectRegistry.ANGEL_WINGS)
        && state(player).getLong(WINGS_READY) > time(player.serverLevel()))
      event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
  }

  private static void recordWingsApplication(MobEffectEvent.Added event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getEffectInstance().is(MobEffectRegistry.ANGEL_WINGS)
        && state(player).getLong(WINGS_READY) <= time(player.serverLevel()))
      activateWings(player, event.getEffectInstance());
  }

  private static void expireWings(PlayerTickEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    MobEffectInstance wings = player.getEffect(MobEffectRegistry.ANGEL_WINGS);
    if (wings == null) return;
    CompoundTag data = state(player);
    if (!data.contains(WINGS_EXPIRES)) activateWings(player, wings);
    if (state(player).getLong(WINGS_EXPIRES) <= time(player.serverLevel()))
      player.removeEffect(MobEffectRegistry.ANGEL_WINGS);
  }

  private static void activateWings(ServerPlayer player, MobEffectInstance effect) {
    long now = time(player.serverLevel());
    int duration =
        effect.isInfiniteDuration()
            ? TravelMagicPolicy.WINGS_DURATION_TICKS
            : Math.clamp(effect.getDuration(), 0, TravelMagicPolicy.WINGS_DURATION_TICKS);
    write(player, WINGS_EXPIRES, now + duration);
    write(player, WINGS_READY, now + TravelMagicPolicy.WINGS_COOLDOWN_TICKS);
  }

  private static void clonePlayer(PlayerEvent.Clone event) {
    CompoundTag data = state(event.getOriginal());
    if (data.isEmpty()) return;
    CompoundTag persisted =
        event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    persisted.put(ROOT, data.copy());
    event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private static CompoundTag state(Player player) {
    return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
  }

  private static void write(Player player, String key, long value) {
    CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    CompoundTag data = persisted.getCompound(ROOT);
    data.putLong(key, value);
    persisted.put(ROOT, data);
    player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
  }

  private static long time(ServerLevel level) {
    return level.getServer().overworld().getGameTime();
  }

  private static boolean reject(ServerPlayer player, String message, boolean feedback) {
    if (feedback) player.displayClientMessage(Component.translatable(message), true);
    return false;
  }
}
