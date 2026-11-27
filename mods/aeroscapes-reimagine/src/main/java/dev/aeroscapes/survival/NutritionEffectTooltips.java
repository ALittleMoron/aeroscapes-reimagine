package dev.aeroscapes.survival;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class NutritionEffectTooltips {
  private static final String TEXT = "aeroscapes_reimagine.survival.penalty.description.";
  private static final IClientMobEffectExtensions WIDE_ROWS =
      new IClientMobEffectExtensions() {
        @Override
        public boolean renderInventoryText(
            MobEffectInstance effect,
            EffectRenderingInventoryScreen<?> screen,
            GuiGraphics graphics,
            int left,
            int top,
            int blitOffset) {
          queueWideTooltip(effect, screen, left, top);
          return false;
        }
      };

  private NutritionEffectTooltips() {}

  @SubscribeEvent
  public static void register(RegisterClientExtensionsEvent event) {
    if (ModList.get().isLoaded("nutritionalbalance"))
      event.registerMobEffect(WIDE_ROWS, NutritionDeficits.attackEffect());
    event.registerMobEffect(WIDE_ROWS, NutritionDeficits.MOVEMENT, NutritionDeficits.MINING);
  }

  @SubscribeEvent
  public static void describe(GatherEffectScreenTooltipsEvent event) {
    var effect = event.getEffectInstance().getEffect();
    Component description;
    if (effect.is(NutritionDeficits.MOVEMENT.getKey())) {
      description = Component.translatable(TEXT + "movement", NutritionDeficits.MOVEMENT_PERCENT);
    } else if (effect.is(NutritionDeficits.MINING.getKey())) {
      description = Component.translatable(TEXT + "mining", NutritionDeficits.MINING_PERCENT);
    } else if (ModList.get().isLoaded("nutritionalbalance")
        && effect.equals(NutritionDeficits.attackEffect())) {
      description = Component.translatable(TEXT + "attack", NutritionPanel.attackReduction());
    } else {
      return;
    }
    event.getTooltip().add(description.copy().withStyle(ChatFormatting.GRAY));
  }

  private static void queueWideTooltip(
      MobEffectInstance effect, EffectRenderingInventoryScreen<?> screen, int left, int top) {
    var game = Minecraft.getInstance();
    if (game.player == null || game.level == null) return;
    var window = game.getWindow();
    int mouseX =
        (int) (game.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
    int mouseY =
        (int) (game.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
    int effectCount = game.player.getActiveEffects().size();
    int height = effectCount > 5 ? Math.min(32, 132 / (effectCount - 1)) : 32;
    if (mouseX < left || mouseX >= left + 120 || mouseY < top || mouseY >= top + height) return;
    // The native compact layout already gathers tooltips. Wide rows only render labels.
    var lines =
        ClientHooks.getEffectTooltip(
            screen,
            effect,
            List.of(
                effect.getEffect().value().getDisplayName(),
                MobEffectUtil.formatDuration(
                    effect, 1.0f, game.level.tickRateManager().tickrate())));
    screen.setTooltipForNextRenderPass(
        lines.stream().flatMap(line -> game.font.split(line, 220).stream()).toList());
  }
}
