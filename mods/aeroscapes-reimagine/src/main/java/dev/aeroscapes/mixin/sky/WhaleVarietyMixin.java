package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleDecorationClearance;
import dev.aeroscapes.sky.WhaleEncounters;
import dev.aeroscapes.sky.WhaleHabitats;
import dev.aeroscapes.sky.WhalePreviewCommands;
import dev.aeroscapes.sky.WhaleVariantAccess;
import dev.aeroscapes.sky.WhaleVariety;
import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SkyWhale.class, remap = false)
public abstract class WhaleVarietyMixin extends PathfinderMob implements WhaleVariantAccess {
  @Unique
  private static final EntityDataAccessor<Integer> AEROSCAPES_VARIETY =
      SynchedEntityData.defineId(SkyWhale.class, EntityDataSerializers.INT);

  protected WhaleVarietyMixin(EntityType<? extends PathfinderMob> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(AEROSCAPES_VARIETY, WhaleVariety.STONE.ordinal());
  }

  @Override
  public WhaleVariety aeroscapes$variety() {
    return WhaleVariety.fromIndex(getEntityData().get(AEROSCAPES_VARIETY));
  }

  @Override
  public void aeroscapes$variety(WhaleVariety variety) {
    getEntityData().set(AEROSCAPES_VARIETY, variety.ordinal());
  }

  @Inject(method = "getBoundingBoxForCulling", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked through the pinned native render boundary.
  private void aeroscapes$includeAnimatedHabitat(CallbackInfoReturnable<AABB> callback) {
    callback.setReturnValue(
        callback
            .getReturnValue()
            .minmax(
                WhaleDecorationClearance.enclosing(aeroscapes$variety(), position(), yBodyRot)));
  }

  @Override
  protected Component getTypeName() {
    return Component.translatable(
        "entity.aeroscapes_reimagine.sky_whale." + aeroscapes$variety().id());
  }

  @Override
  protected float getSoundVolume() {
    return WhaleEncounters.VOICE_VOLUME;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag data) {
    super.addAdditionalSaveData(data);
    data.putString("AeroscapesWhaleVariety", aeroscapes$variety().id());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag data) {
    super.readAdditionalSaveData(data);
    aeroscapes$variety(
        data.contains("AeroscapesWhaleVariety")
            ? WhaleVariety.fromId(data.getString("AeroscapesWhaleVariety"))
            : level() instanceof ServerLevel server
                ? WhaleHabitats.at(server, blockPosition())
                : WhaleVariety.STONE);
    if (getPersistentData().hasUUID(WhalePreviewCommands.MARKER)) setSilent(false);
  }
}
