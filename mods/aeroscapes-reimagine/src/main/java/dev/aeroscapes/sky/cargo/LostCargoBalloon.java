package dev.aeroscapes.sky.cargo;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

public final class LostCargoBalloon extends Entity {
  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final String NAME = "lost_cargo_balloon";
  private static final int ATTEMPT_INTERVAL = 20 * 60;
  private static final int LIFETIME = 20 * 110;
  private static final DeferredRegister<EntityType<?>> TYPES =
      DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
  public static final DeferredHolder<EntityType<?>, EntityType<LostCargoBalloon>> TYPE =
      TYPES.register(
          NAME,
          () ->
              EntityType.Builder.of(LostCargoBalloon::new, MobCategory.MISC)
                  .sized(1.9F, 2.7F)
                  .clientTrackingRange(8)
                  .build(MOD_ID + ":" + NAME));
  private static final ResourceKey<LootTable> CARGO =
      ResourceKey.create(
          Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "entities/" + NAME));

  private int driftTicks;

  private LostCargoBalloon(EntityType<? extends LostCargoBalloon> type, Level level) {
    super(type, level);
  }

  public static void register(IEventBus bus) {
    TYPES.register(bus);
    NeoForge.EVENT_BUS.addListener(LostCargoBalloon::encounter);
  }

  private static void encounter(LevelTickEvent.Post event) {
    if (!(event.getLevel() instanceof ServerLevel level)
        || level.dimension() != Level.OVERWORLD
        || level.getGameTime() % ATTEMPT_INTERVAL != 183
        || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
        || level.players().isEmpty()) return;
    var random = level.getRandom();
    if (random.nextInt(7) != 0) return;
    var player = level.players().get(random.nextInt(level.players().size()));
    if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;
    var origin = Sable.HELPER.projectOutOfSubLevel(level, player.position());
    if (origin.y < 110 || origin.y > 340) return;
    var forward =
        Sable.HELPER
            .projectOutOfSubLevel(
                level, player.position().add(Vec3.directionFromRotation(0, player.getYRot())))
            .subtract(origin);
    if (!level
        .getEntitiesOfClass(LostCargoBalloon.class, new AABB(origin, origin).inflate(112))
        .isEmpty()) return;
    var balloon = TYPE.get().create(level);
    if (balloon == null) return;
    for (int attempt = 0; attempt < 3; attempt++) {
      var offset = forward.yRot((random.nextFloat() - 0.5F) * 1.7F);
      var point =
          origin.add(
              offset.x * (35 + 12 * random.nextDouble()),
              8 + random.nextInt(12),
              offset.z * (35 + 12 * random.nextDouble()));
      var pos = BlockPos.containing(point);
      if (!level.isPositionEntityTicking(pos) || !level.canSeeSky(pos)) continue;
      balloon.setPos(point);
      var bounds = balloon.getBoundingBox().inflate(2);
      if (loaded(level, bounds)
          && level.noCollision(balloon, bounds)
          && !level.containsAnyLiquid(bounds)) {
        level.addFreshEntity(balloon);
        return;
      }
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (!(level() instanceof ServerLevel level)) return;
    if (++driftTicks >= LIFETIME || getY() < level.getMinBuildHeight() + 4) {
      discard();
      return;
    }
    double heading = (getUUID().getLeastSignificantBits() & 1023L) * Math.TAU / 1024;
    var movement = new Vec3(0.055 * Math.sin(heading), -0.019, 0.055 * Math.cos(heading));
    if (loaded(level, getBoundingBox().expandTowards(movement))) move(MoverType.SELF, movement);
  }

  private static boolean loaded(ServerLevel level, AABB bounds) {
    for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
      for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
        if (!level.getChunkSource().hasChunk(x, z)) return false;
      }
    }
    return true;
  }

  @Override
  public boolean isPickable() {
    return isAlive();
  }

  @Override
  public InteractionResult interact(Player player, InteractionHand hand) {
    if (!isAlive()) return InteractionResult.PASS;
    if (level() instanceof ServerLevel level) {
      release(level, damageSources().playerAttack(player), player);
    }
    return InteractionResult.sidedSuccess(level().isClientSide());
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (!isAlive() || amount <= 0 || !source.is(DamageTypeTags.IS_PROJECTILE)) return false;
    if (level() instanceof ServerLevel level) release(level, source, null);
    return true;
  }

  private void release(ServerLevel level, DamageSource source, @Nullable Player collector) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .withParameter(LootContextParams.ORIGIN, position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
            .withOptionalParameter(
                LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
    if (source.getEntity() instanceof Player player) {
      params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
    }
    var table = level.getServer().reloadableRegistries().getLootTable(CARGO);
    discard();
    table.getRandomItems(
        params.create(LootContextParamSets.ENTITY),
        stack -> {
          if (collector != null) collector.addItem(stack);
          if (!stack.isEmpty()) spawnAtLocation(stack);
        });
    level.playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1, 0.8F);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {}

  @Override
  protected void readAdditionalSaveData(CompoundTag tag) {
    driftTicks = Math.clamp(tag.getInt("DriftTicks"), 0, LIFETIME);
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag tag) {
    tag.putInt("DriftTicks", driftTicks);
  }
}
