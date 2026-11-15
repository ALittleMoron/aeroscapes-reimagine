package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleCarcasses;
import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SkyWhale.class, remap = false)
public abstract class WhaleCarcassStorageMixin {
  @Redirect(
      method = "tickDeath",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/dacite/skywhales/corpse/WhaleCorpseSpawner;spawnCorpse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;F)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native staging otherwise erases occupied terrain.
  private void aeroscapes$reservedCarcassStorage(ServerLevel level, Vec3 center, float deathYaw) {
    WhaleCarcasses.spawn(level, center, deathYaw);
  }
}
