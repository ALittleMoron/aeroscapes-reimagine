package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.FieldReforges;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BrushableBlockEntity.class)
abstract class BrushProgressReforgeMixin {
  @Shadow private ItemStack item;
  @Shadow private ResourceKey<LootTable> lootTable;
  @Unique private boolean aeroscapes$hasNativeLoot;

  @WrapOperation(
      method = "brush",
      at =
          @At(
              value = "FIELD",
              opcode = Opcodes.PUTFIELD,
              target = "Lnet/minecraft/world/level/block/entity/BrushableBlockEntity;brushCount:I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$efficientStroke(
      BrushableBlockEntity block,
      int step,
      Operation<Void> original,
      @Local(argsOnly = true) Player player) {
    original.call(
        block, FieldReforges.brushProgress(player.getUseItem(), block.getBlockState(), step));
  }

  @ModifyConstant(method = "brush", constant = @Constant(longValue = 40L))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private long aeroscapes$retainProgress(long delay, @Local(argsOnly = true) Player player) {
    aeroscapes$hasNativeLoot |= lootTable != null || !item.isEmpty();
    return aeroscapes$hasNativeLoot
            && ReforgeProfiles.has(player.getUseItem(), "brush_legendary_memory")
        ? delay + 60L
        : delay;
  }
}
