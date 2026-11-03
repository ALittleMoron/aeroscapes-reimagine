package dev.aeroscapes.mixin.aquatic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.aquatic.AquaticWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = {
      "dev.hybridlabs.aquatic.entity.cephalopod.ColossalSquidEntity$Companion",
      "dev.hybridlabs.aquatic.entity.cephalopod.FireflySquidEntity$Companion",
      "dev.hybridlabs.aquatic.entity.cephalopod.GiantSquidEntity$Companion",
      "dev.hybridlabs.aquatic.entity.cephalopod.NautilusEntity$Companion",
      "dev.hybridlabs.aquatic.entity.cephalopod.UmbrellaOctopusEntity$Companion",
      "dev.hybridlabs.aquatic.entity.crustacean.CoconutCrabEntity$Companion",
      "dev.hybridlabs.aquatic.entity.crustacean.GhostCrabEntity$Companion",
      "dev.hybridlabs.aquatic.entity.crustacean.HorseshoeCrabEntity$Companion",
      "dev.hybridlabs.aquatic.entity.crustacean.SpiderCrabEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.CarpEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.DragonfishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.FlashlightFishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.FlyingFishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.GardenEelEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.HagfishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.OarfishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.OceanSunfishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.SquirrelfishEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.TetraEntity$Companion",
      "dev.hybridlabs.aquatic.entity.fish.TripodFishEntity$Companion",
      "dev.hybridlabs.aquatic.world.WorldHelper",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseCephalopodEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseCritterEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseCrustaceanEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseDolphinEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseFishEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseJellyfishEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseOctopusEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseSharkEntity$Companion",
      "dev.hybridlabs.hapi.entity.base.aquatic.BaseSirenianEntity$Companion",
      "dev.hybridlabs.hapi.world.WorldHelper"
    },
    remap = false)
public abstract class AquaticSpawnMixin {
  @ModifyExpressionValue(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve native depth bands, day/night and substrate tests.
  private int aeroscapes$localWaterline(
      int nativeSea,
      @Local(argsOnly = true) ServerLevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return AquaticWater.seaLevel(nativeSea, level, position);
  }
}
