package dev.aeroscapes.mixin.trees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.pandasystems.fallingtrees.FallingTreesRegistriesKt;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FallingTreesRegistriesKt.class, remap = false)
public abstract class PandaTreeSaveTypeMixin {
  @WrapOperation(
      method = "treeEntity$lambda$0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/EntityType$Builder;noSave()Lnet/minecraft/world/entity/EntityType$Builder;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pending harvested drops must survive a world save.
  private static EntityType.Builder<?> aeroscapes$saveTree(
      EntityType.Builder<?> builder, Operation<EntityType.Builder<?>> original) {
    return builder;
  }
}
