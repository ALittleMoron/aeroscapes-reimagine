package dev.aeroscapes.mixin.movement;

import com.yori3o.yo_hooks.common.item.HookItem;
import dev.aeroscapes.movement.HookReforgeSpeed;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HookItem.class, remap = false)
public abstract class HookReforgeTooltipMixin {
  @Inject(method = "appendHoverText", at = @At("RETURN"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$showReelingSpeed(
      ItemStack stack,
      Item.TooltipContext context,
      List<Component> tooltip,
      TooltipFlag flag,
      CallbackInfo callback) {
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

  private static String signed(int bonus) {
    return bonus > 0 ? "+" + bonus : Integer.toString(bonus);
  }
}
