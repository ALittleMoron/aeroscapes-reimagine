package dev.aeroscapes.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import top.theillusivec4.curios.common.network.client.CPacketOpenVanilla;

public final class InventoryNavigation {
  private InventoryNavigation() {}

  public static boolean openInventory() {
    var client = Minecraft.getInstance();
    var player = client.player;
    if (player == null || client.level == null || !player.isAlive() || player.isRemoved()) {
      client.setScreen(null);
      return false;
    }
    var previous = player.containerMenu;
    if (previous instanceof CuriosContainer) {
      ItemStack carried = previous.getCarried();
      previous.setCarried(ItemStack.EMPTY);
      player.containerMenu = player.inventoryMenu;
      client.setScreen(new InventoryScreen(player));
      player.inventoryMenu.setCarried(carried);
      PacketDistributor.sendToServer(new CPacketOpenVanilla(carried));
    } else {
      if (previous != player.inventoryMenu) player.closeContainer();
      client.setScreen(new InventoryScreen(player));
    }
    return true;
  }
}
