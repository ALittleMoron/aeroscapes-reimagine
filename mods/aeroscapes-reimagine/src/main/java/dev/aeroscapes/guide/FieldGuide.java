package dev.aeroscapes.guide;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ProfessionProgression;
import java.util.List;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Hands out the same guide that the replacement recipe produces. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class FieldGuide {
  public static final ResourceLocation GUIDE =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "field_guide");
  private static final ResourceLocation BOOK =
      ResourceLocation.fromNamespaceAndPath("modonomicon", "modonomicon");
  private static final ResourceLocation BOOK_ID =
      ResourceLocation.fromNamespaceAndPath("modonomicon", "book_id");
  private static final String TEXT = "book.aeroscapes_reimagine.field_guide.";
  private static final String GRANTED = "aeroscapes_reimagine:field_guide_received";
  private static final ResourceLocation HEATED = ResourceLocation.parse("overgeared:heated");

  private FieldGuide() {}

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
    deliver(player, true);
    // A returning player's existing stacks may predate the hidden guide advancements.
    var inventory = player.getInventory();
    for (var milestone : player.server.getAdvancements().getAllAdvancements()) {
      if (!milestone.id().getNamespace().equals(GUIDE.getNamespace())
          || !milestone.id().getPath().startsWith("guide/checkpoints/")) continue;
      var criterion = milestone.value().criteria().get("done");
      if (criterion == null
          || !(criterion.triggerInstance()
              instanceof InventoryChangeTrigger.TriggerInstance possession)
          || possession.items().size() != 1) continue;
      if (inventory.contains(possession.items().getFirst()))
        player.getAdvancements().award(milestone, "done");
    }
  }

  @SubscribeEvent
  public static void retry(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || player instanceof FakePlayer
        || !player.isAlive()
        || player.tickCount % 20 != 0) return;
    deliver(player, false);
    for (var metal : List.of("iron", "steel", "diamond")) {
      var checkpoint = metal + "_talent";
      if (!completed(player, checkpoint)
          && ProfessionProgression.hasTalent(
              player, ProfessionDiscipline.SMITHING, "manufacture_" + metal + "_tools"))
        complete(player, checkpoint);
    }
    checkCooling(player, "copper", "copper_pick");
    checkCooling(player, "iron", "iron_pick");
    checkCooling(player, "steel", "steel_pick");
  }

  public static boolean completed(ServerPlayer player, String checkpoint) {
    return done(
        player,
        ResourceLocation.fromNamespaceAndPath(
            GUIDE.getNamespace(), "guide/checkpoints/" + checkpoint));
  }

  public static boolean done(ServerPlayer player, ResourceLocation id) {
    var advancement = player.server.getAdvancements().get(id);
    return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
  }

  public static void complete(ServerPlayer player, String checkpoint) {
    var advancement =
        player
            .server
            .getAdvancements()
            .get(
                ResourceLocation.fromNamespaceAndPath(
                    GUIDE.getNamespace(), "guide/checkpoints/" + checkpoint));
    if (advancement != null) player.getAdvancements().award(advancement, "done");
  }

  private static void checkCooling(ServerPlayer player, String metal, String tool) {
    if (completed(player, "quench_" + metal)) return;
    var head =
        BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("overgeared", metal + "_pickaxe_head"));
    var heated = BuiltInRegistries.DATA_COMPONENT_TYPE.get(HEATED);
    if (completed(player, tool)
        || heated != null
            && player
                .getInventory()
                .contains(stack -> stack.is(head) && !Boolean.TRUE.equals(stack.get(heated))))
      complete(player, "quench_" + metal);
  }

  private static void deliver(ServerPlayer player, boolean notifyIfFull) {
    if (player instanceof FakePlayer || !player.isAlive()) return;
    var data = player.getPersistentData();
    var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
    if (persisted.getBoolean(GRANTED)) return;

    var bookId = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_ID);
    var recipe = player.server.getRecipeManager().byKey(GUIDE).orElse(null);
    if (bookId == null || recipe == null) return;
    var guide = recipe.value().getResultItem(player.registryAccess());
    if (guide.getCount() != 1
        || !isGuide(guide, bookId)
        || !(guide.getHoverName().getContents() instanceof TranslatableContents title)
        || !title.getKey().equals(TEXT + "name")) return;

    var inventory = player.getInventory();
    boolean carried = inventory.contains(stack -> isGuide(stack, bookId));
    if (!carried && (inventory.getFreeSlot() < 0 || !inventory.add(guide.copy()))) {
      if (notifyIfFull)
        player.displayClientMessage(Component.translatable(TEXT + "grant_full"), false);
      return;
    }

    persisted.putBoolean(GRANTED, true);
    data.put(Player.PERSISTED_NBT_TAG, persisted);
    player.awardRecipes(List.of(recipe));
    player.displayClientMessage(
        Component.translatable(
            TEXT + (carried ? "grant_carried" : "grant_received"), guide.getHoverName()),
        false);
  }

  private static boolean isGuide(ItemStack stack, DataComponentType<?> bookId) {
    return !stack.isEmpty()
        && BOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
        && GUIDE.equals(stack.get(bookId));
  }
}
