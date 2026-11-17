package dev.aeroscapes.progression.emi;

import dev.aeroscapes.progression.client.InventorySkillsPanel;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.fml.ModList;

/** Moves EMI's left favorites sidebar aside while the inventory skills panel is shown. */
@EmiEntrypoint
public final class SkillPanelEmiPlugin implements EmiPlugin {
  @Override
  public void register(EmiRegistry registry) {
    if (!ModList.get().isLoaded("pmmo")) return;
    registry.addExclusionArea(
        InventoryScreen.class,
        (screen, area) -> {
          for (var child : screen.children()) {
            if (child instanceof InventorySkillsPanel panel && panel.visible) {
              area.accept(
                  new Bounds(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight()));
            }
          }
        });
  }
}
