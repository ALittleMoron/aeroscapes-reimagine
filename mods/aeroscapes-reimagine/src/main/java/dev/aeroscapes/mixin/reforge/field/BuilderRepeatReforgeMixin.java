package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.BuilderActions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
abstract class BuilderRepeatReforgeMixin {
  private static final ResourceLocation MERCURIAL_WAND =
      ResourceLocation.fromNamespaceAndPath("theurgy", "mercurial_wand");

  @Shadow @Final private Minecraft minecraft;

  @WrapOperation(
      method = "performUseItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$constructionCadence(
      ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
    String rank = ReforgeProfiles.path(stack);
    InteractionResult result = original.call(stack, context);
    BuilderActions.apply(minecraft, rank, context, result, true);
    return result;
  }

  @WrapOperation(
      method = "performUseItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;onItemUseFirst(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$wandCadence(
      ItemStack stack, UseOnContext context, Operation<InteractionResult> original) {
    boolean wand = MERCURIAL_WAND.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    String rank = wand ? ReforgeProfiles.path(stack) : "";
    InteractionResult result = original.call(stack, context);
    BuilderActions.apply(minecraft, rank, context, result, false);
    return result;
  }
}
