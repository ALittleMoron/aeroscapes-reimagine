package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.theillusivec4.curios.mixin.CuriosUtilMixinHooks;

@Mixin(PowderSnowBlock.class)
abstract class SterlingBootsTrainingMixin {
  @Unique
  private static final ResourceLocation AEROSCAPES$STERLING_BOOTS =
      ResourceLocation.fromNamespaceAndPath("galosphere", "sterling_boots");

  @WrapMethod(method = "canEntityWalkOnPowderSnow")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static boolean aeroscapes$trainedPowderSnowBoots(
      Entity wearer, Operation<Boolean> original) {
    if (wearer instanceof Player player) {
      var boots = player.getItemBySlot(EquipmentSlot.FEET);
      if (AEROSCAPES$STERLING_BOOTS.equals(BuiltInRegistries.ITEM.getKey(boots.getItem()))
          && ArmorProficiency.undertrained(player, boots)) {
        // Galosphere short-circuits the vanilla and Curios checks while Sterling boots are worn.
        return player.getType().is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS)
            || boots.canWalkOnPowderedSnow(player)
            || (ModList.get().isLoaded("curios")
                && CuriosUtilMixinHooks.canWalkOnPowderSnow(player));
      }
    }
    return original.call(wearer);
  }
}
