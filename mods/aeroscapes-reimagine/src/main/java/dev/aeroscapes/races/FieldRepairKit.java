package dev.aeroscapes.races;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FieldRepairKit extends Item {
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems("aeroscapes_reimagine");
  public static final DeferredItem<FieldRepairKit> ITEM =
      ITEMS.registerItem(
          "field_repair_kit", FieldRepairKit::new, new Item.Properties().stacksTo(16));

  private FieldRepairKit(Properties properties) {
    super(properties);
  }

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
    bus.addListener(FieldRepairKit::creativeContents);
    FieldRepair.register();
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ITEM);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    var stack = player.getItemInHand(hand);
    if (player instanceof ServerPlayer serverPlayer)
      return FieldRepair.begin(serverPlayer, hand)
          ? InteractionResultHolder.consume(stack)
          : InteractionResultHolder.fail(stack);
    if (level.isClientSide() && player.getItemInHand(FieldRepair.other(hand)).isDamaged()) {
      player.startUsingItem(hand);
      return InteractionResultHolder.consume(stack);
    }
    return InteractionResultHolder.fail(stack);
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    return UseAnim.BRUSH;
  }

  @Override
  public int getUseDuration(ItemStack stack, LivingEntity user) {
    return FieldRepair.USE_TICKS;
  }

  @Override
  public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingTicks) {
    if (user instanceof ServerPlayer player) FieldRepair.useTick(player, stack);
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
    if (user instanceof ServerPlayer player) FieldRepair.finish(player, stack);
    return stack;
  }

  @Override
  public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingTicks) {
    if (user instanceof ServerPlayer player) FieldRepair.released(player);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    tooltip.add(Component.translatable("tooltip.aeroscapes_reimagine.field_repair_kit"));
  }
}
