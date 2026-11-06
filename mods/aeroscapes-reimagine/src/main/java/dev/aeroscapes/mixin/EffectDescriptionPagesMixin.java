package dev.aeroscapes.mixin;

import dev.aeroscapes.atmosphere.EffectDescriptionPages;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "moe.prwk.emiffect.recipes.MobEffectInfo", remap = false)
public abstract class EffectDescriptionPagesMixin {
  @Shadow @Final private List<FormattedCharSequence> desc;
  @Shadow @Final private List<EmiIngredient> inputs;
  @Unique private List<FormattedCharSequence> aeroscapes$visibleDescription;
  @Unique private EffectDescriptionPages aeroscapes$pages;

  @Inject(method = "addWidgets", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the Mixin injector.
  private void aeroscapes$preparePages(WidgetHolder widgets, CallbackInfo callback) {
    var pages = new EffectDescriptionPages(desc.size(), widgets.getHeight(), !inputs.isEmpty());
    aeroscapes$pages = pages.pageCount() > 1 ? pages : null;
    aeroscapes$visibleDescription = desc;
    if (aeroscapes$pages == null) return;
    List<FormattedCharSequence> visible = new ArrayList<>();
    for (int row = 0; row < pages.capacity(); row++) {
      int line = row;
      visible.add(
          sink -> {
            int index = pages.lineIndex(line);
            return index < 0 || desc.get(index).accept(sink);
          });
    }
    visible.add(sink -> true);
    visible.add(sink -> true);
    aeroscapes$visibleDescription = visible;
  }

  @Redirect(
      method = "addWidgets",
      at =
          @At(
              value = "FIELD",
              target = "Lmoe/prwk/emiffect/recipes/MobEffectInfo;desc:Ljava/util/List;",
              opcode = Opcodes.GETFIELD),
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the Mixin redirect.
  private List<FormattedCharSequence> aeroscapes$visibleLines(@Coerce Object instance) {
    return aeroscapes$visibleDescription;
  }

  @Inject(method = "addWidgets", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the Mixin injector.
  private void aeroscapes$addPageButtons(WidgetHolder widgets, CallbackInfo callback) {
    var pages = aeroscapes$pages;
    if (pages == null) return;
    int y = 14 + pages.capacity() * 9;
    widgets.addButton(
        31,
        y,
        12,
        12,
        0,
        0,
        () -> pages.page() > 0,
        (mouseX, mouseY, button) -> pages.select(pages.page() - 1));
    widgets.addButton(
        128,
        y,
        12,
        12,
        12,
        0,
        () -> pages.page() + 1 < pages.pageCount(),
        (mouseX, mouseY, button) -> pages.select(pages.page() + 1));
    widgets.addTooltipText(
        List.of(Component.translatable("aeroscapes_reimagine.effect.previous_page")),
        31,
        y,
        12,
        12);
    widgets.addTooltipText(
        List.of(Component.translatable("aeroscapes_reimagine.effect.next_page")), 128, y, 12, 12);
  }
}
