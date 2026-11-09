package dev.aeroscapes.mixin.reforge;

import com.li64.tide.data.rods.BaitContents;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TideFishingHook.class, remap = false)
abstract class TideRodReforgeMixin {
  private static final String RETRIEVE =
      "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I";

  @ModifyExpressionValue(
      method = "catchingFish",
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$reduceBiteWait(int ticks) {
    var hook = (TideFishingHook) (Object) this;
    return switch (ReforgeProfiles.path(hook.rod())) {
      case "rod_poor" -> Math.round(ticks * 1.10F);
      case "rod_fine" -> Math.max(10, Math.round(ticks * 0.94F));
      case "rod_exceptional_bite" -> Math.max(10, Math.round(ticks * 0.88F));
      case "rod_epic_reel", "rod_epic_bait", "rod_legendary_catch" ->
          Math.max(10, Math.round(ticks * 0.92F));
      default -> ticks;
    };
  }

  @ModifyExpressionValue(
      method = "catchingFish",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I",
              ordinal = 0),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$extendBiteWindow(int ticks) {
    var hook = (TideFishingHook) (Object) this;
    return switch (ReforgeProfiles.path(hook.rod())) {
      case "rod_exceptional_window" -> Math.round(ticks * 1.18F);
      case "rod_epic_reel", "rod_legendary_catch" -> Math.round(ticks * 1.10F);
      default -> ticks;
    };
  }

  @WrapOperation(
      method = RETRIEVE,
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/li64/tide/data/rods/BaitContents$Mutable;shrinkAll()V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$saveBait(BaitContents.Mutable bait, Operation<Void> original) {
    var hook = (TideFishingHook) (Object) this;
    if (!ReforgeProfiles.has(hook.rod(), "rod_epic_bait") || hook.getRandom().nextFloat() >= 0.12F)
      original.call(bait);
  }

  @WrapOperation(
      method = RETRIEVE,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
              ordinal = 0),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$doubleCatch(
      Level level,
      Entity entity,
      Operation<Boolean> original,
      @Share("aeroscapes$catchRolled") LocalBooleanRef rolled) {
    var hook = (TideFishingHook) (Object) this;
    if (!rolled.get()
        && entity instanceof ItemEntity fish
        && ReforgeProfiles.has(hook.rod(), "rod_legendary_catch")
        && hook.getCatchType() == TideFishingHook.CatchType.FISH
        && fish.getItem().is(ItemTags.FISHES)
        && !fish.getItem()
            .is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("tide", "legendary_fish")))
        && fish.getItem().getCount() < fish.getItem().getMaxStackSize()) {
      rolled.set(true);
      if (hook.getRandom().nextFloat() < 0.10F) {
        var stack = fish.getItem();
        fish.setItem(stack.copyWithCount(stack.getCount() + 1));
      }
    }
    return original.call(level, entity);
  }
}
