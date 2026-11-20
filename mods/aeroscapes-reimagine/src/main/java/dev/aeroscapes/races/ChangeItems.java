package dev.aeroscapes.races;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ChangeItems {
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems("aeroscapes_reimagine");
  public static final DeferredItem<Item> IDENTITY_SEAL =
      ITEMS.register("identity_seal", () -> new ChangeItem(Rarity.RARE));
  public static final DeferredItem<Item> KNOTTED_THREAD =
      ITEMS.register("knotted_thread", () -> new ChangeItem(Rarity.UNCOMMON));

  private ChangeItems() {}

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
    bus.addListener(ChangeItems::creativeContents);
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
      event.accept(IDENTITY_SEAL);
      event.accept(KNOTTED_THREAD);
    }
  }

  private static final class ChangeItem extends Item {
    private ChangeItem(Rarity rarity) {
      super(new Properties().rarity(rarity));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      var stack = player.getItemInHand(hand);
      if (!CharacterClass.creationAvailable()) return InteractionResultHolder.fail(stack);
      if (player instanceof ServerPlayer serverPlayer) ChangeNetwork.open(serverPlayer, hand);
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(getDescriptionId() + ".use"));
    }
  }
}
