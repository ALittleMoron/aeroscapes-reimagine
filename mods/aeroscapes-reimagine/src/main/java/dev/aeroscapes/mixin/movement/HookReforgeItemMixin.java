package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yori3o.yo_hooks.common.hookregistry.HookDefinition;
import com.yori3o.yo_hooks.common.item.HookItem;
import com.yori3o.yo_hooks.common.util.PhysicVariables;
import dev.aeroscapes.movement.HookReforgeBonuses;
import dev.aeroscapes.movement.HookReforgeSpeed;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HookItem.class, remap = false)
public abstract class HookReforgeItemMixin {
  @Shadow @Final public HookDefinition hookDefinition;

  @ModifyExpressionValue(
      method = {"fire", "appendHoverText"},
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/yori3o/yo_hooks/common/item/HookItem;getBasicLength()I"),
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$range(int range, @Local(argsOnly = true) ItemStack stack) {
    return range + HookReforgeBonuses.of(stack).range();
  }

  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$castHunger(
      Player player, float exhaustion, Operation<Void> original, @Local ItemStack stack) {
    original.call(player, HookReforgeBonuses.of(stack).applyHungerSaving(exhaustion));
  }

  @Inject(method = "appendHoverText", at = @At("RETURN"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$showReforgeBonuses(
      ItemStack stack,
      Item.TooltipContext context,
      List<Component> tooltip,
      TooltipFlag flag,
      CallbackInfo callback) {
    if (!PhysicVariables.funnyMode) {
      var bonuses = HookReforgeBonuses.of(stack);
      if (bonuses.range() > 0) {
        addBonus(tooltip, "hook_range_bonus", bonuses.range());
        addBonus(tooltip, "hook_wear_saving", bonuses.wearSaving());
        if (!hookDefinition.doesNotConsumeHunger)
          addBonus(tooltip, "hook_hunger_saving", bonuses.hungerSaving());
      }
    }
    int up = HookReforgeSpeed.bonusPercent(stack, true);
    int down = HookReforgeSpeed.bonusPercent(stack, false);
    if (up == 0 && down == 0) return;
    var line =
        up == down
            ? Component.translatable(
                "aeroscapes_reimagine.equipment.hook_reeling_speed", signed(up))
            : Component.translatable(
                "aeroscapes_reimagine.equipment.hook_direction_speed", signed(up), signed(down));
    tooltip.add(line.withStyle(up > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
  }

  private static void addBonus(List<Component> tooltip, String key, int bonus) {
    tooltip.add(
        Component.translatable("aeroscapes_reimagine.equipment." + key, bonus)
            .withStyle(ChatFormatting.BLUE));
  }

  private static String signed(int bonus) {
    return bonus > 0 ? "+" + bonus : Integer.toString(bonus);
  }
}
