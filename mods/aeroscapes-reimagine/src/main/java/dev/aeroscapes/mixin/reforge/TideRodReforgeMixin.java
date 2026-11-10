package dev.aeroscapes.mixin.reforge;

import com.li64.tide.data.rods.BaitContents;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.RodReforges;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TideFishingHook.class, remap = false)
abstract class TideRodReforgeMixin {
  private static final String RETRIEVE =
      "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I";

  @Shadow
  private boolean isWaterFishing() {
    throw new AssertionError();
  }

  @ModifyExpressionValue(
      method = "catchingFish",
      at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$reduceBiteWait(int ticks) {
    var hook = (TideFishingHook) (Object) this;
    return RodReforges.biteWait(ticks, ReforgeProfiles.path(hook.rod()));
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
    return RodReforges.biteWindow(ticks, ReforgeProfiles.path(hook.rod()));
  }

  @WrapOperation(
      method = RETRIEVE,
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Event aeroscapes$checkCatchPermission(
      IEventBus bus,
      Event event,
      Operation<Event> original,
      @Share("aeroscapes$catchDenied") LocalBooleanRef denied) {
    Event result = original.call(bus, event);
    if (event instanceof ItemFishedEvent fishing) denied.set(fishing.isCanceled());
    return result;
  }

  @ModifyReturnValue(method = RETRIEVE, at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$saveRodWear(
      int amount,
      @Local(argsOnly = true) ItemStack reel,
      @Local(argsOnly = true) Player user,
      @Share("aeroscapes$catchDenied") LocalBooleanRef denied) {
    var hook = (TideFishingHook) (Object) this;
    if (denied.get()
        || !ReforgeProfiles.has(reel, "rod_exceptional_bite_with_conservation")
        || !ReforgeProfiles.has(hook.rod(), "rod_exceptional_bite_with_conservation"))
      return amount;
    return ToolReforges.modifyWear(user, amount, 15);
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
      @Local(argsOnly = true) ItemStack reel,
      @Share("aeroscapes$catchRolled") LocalBooleanRef rolled,
      @Share("aeroscapes$catchDenied") LocalBooleanRef denied) {
    var hook = (TideFishingHook) (Object) this;
    if (!rolled.get()
        && !denied.get()
        && entity instanceof ItemEntity junk
        && ReforgeProfiles.has(hook.rod(), "rod_legendary_salvager")
        && ReforgeProfiles.has(reel, "rod_legendary_salvager")
        && hook.getCatchType() == TideFishingHook.CatchType.ITEM
        && isWaterFishing()
        && hook.getPlayerOwner() instanceof ServerPlayer player
        && ToolReforges.isSurvivalPlayer(player)) {
      rolled.set(RodReforges.trySalvage(junk, hook.getRandom()));
    }
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
