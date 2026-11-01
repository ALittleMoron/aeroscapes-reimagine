package dev.aeroscapes.atmosphere;

import dev.aeroscapes.mixin.atmosphere.SophisticatedInventoryFocusAccess;
import dev.aeroscapes.mixin.atmosphere.TomsPopupMenuAccess;
import dev.aeroscapes.mixin.atmosphere.TomsTerminalPopupAccess;
import dev.emi.emi.api.EmiApi;
import fi.dy.masa.malilib.gui.GuiBase;
import java.util.Map;
import me.pieking1215.invmove.InvMove;
import me.pieking1215.invmove.InvMoveConfig;
import me.pieking1215.invmove.module.Module;
import me.pieking1215.invmove.module.ModuleImpl;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Adds focus boundaries that the provider's screen-field reflection cannot discover. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class InventoryMovementCompatibility extends ModuleImpl {
  private InventoryMovementCompatibility() {
    register(GuiBase.class, AbstractConfigScreen.class, PonderUI.class)
        .movement(Module.Movement.FORCE_DISABLE);
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          for (var expected :
              Map.of(
                      "invmove", "0.9.3",
                      "emi", "1.1.22+1.21.1+neoforge",
                      "mafglib", "0.4.3+mc1.21.1",
                      "cloth_config", "15.0.140",
                      "ponder", "1.0.82+mc1.21.1",
                      "sophisticatedcore", "1.5.17",
                      "toms_storage", "2.4.2")
                  .entrySet()) {
            String actual =
                ModList.get()
                    .getModContainerById(expected.getKey())
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Missing inventory movement provider " + expected.getKey()))
                    .getModInfo()
                    .getVersion()
                    .toString();
            if (!expected.getValue().equals(actual)) {
              throw new IllegalStateException(
                  "Review inventory movement compatibility for "
                      + expected.getKey()
                      + " "
                      + actual);
            }
          }
          InvMove.registerModule(new InventoryMovementCompatibility());
        });
  }

  @Override
  public String getId() {
    return "aeroscapes";
  }

  @Override
  public Module.Movement shouldAllowMovement(Screen screen) {
    if (screen instanceof TomsTerminalPopupAccess terminal) {
      var popup = terminal.aeroscapes$getPopup();
      if (popup instanceof TomsPopupMenuAccess menu && menu.aeroscapes$getMenu() != null) {
        return Module.Movement.FORCE_DISABLE;
      }
    }
    if (InvMoveConfig.MOVEMENT.TEXT_FIELD_DISABLES.get()
        && (EmiApi.isSearchFocused()
            || (screen instanceof SophisticatedInventoryFocusAccess storage
                && (storage.aeroscapes$isTextBoxFocused()
                    || storage.aeroscapes$getModalOverlay() != null)))) {
      return Module.Movement.FORCE_DISABLE;
    }
    if (InventoryPonderFocus.holdsTutorial(screen)) {
      return Module.Movement.FORCE_DISABLE;
    }
    return super.shouldAllowMovement(screen);
  }
}
