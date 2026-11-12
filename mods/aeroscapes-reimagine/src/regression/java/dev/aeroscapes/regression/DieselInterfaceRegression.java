package dev.aeroscapes.regression;

import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.CDGMenuTypes;
import com.jesz.createdieselgenerators.content.entity_filter.EntityFilterMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Real menu initialization and shift-clicking the provider's two-slot ghost inventory. */
public final class DieselInterfaceRegression {
  private DieselInterfaceRegression() {}

  public static void verify(ServerLevel level) {
    var player = FakePlayerFactory.getMinecraft(level);
    try {
      var constructor =
          EntityFilterMenu.class.getDeclaredConstructor(
              MenuType.class, int.class, Inventory.class, ItemStack.class);
      require(constructor.trySetAccessible(), "Native filter constructor is accessible to fixture");
      var menu =
          constructor.newInstance(
              CDGMenuTypes.ENTITY_FILTER.get(),
              1,
              player.getInventory(),
              CDGItems.ENTITY_FILTER.asStack());
      var label = menu.getSlot(37).getItem().get(DataComponents.CUSTOM_NAME);
      require(
          label != null
              && label.getContents() instanceof TranslatableContents text
              && text.getKey().equals("createdieselgenerators.gui.entity_filter.selected_tags"),
          "Native filter sample label uses the translated component");
      menu.getSlot(36).set(new ItemStack(Items.IRON_INGOT));
      require(
          menu.quickMoveStack(player, 36).isEmpty() && menu.getSlot(36).getItem().isEmpty(),
          "Shift-click clears the actual ghost sample without out-of-bounds access");
      require(!menu.getSlot(37).getItem().isEmpty(), "Read-only selected-tags slot remains intact");
      menu.removed(player);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Native entity-filter menu regression failed", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
