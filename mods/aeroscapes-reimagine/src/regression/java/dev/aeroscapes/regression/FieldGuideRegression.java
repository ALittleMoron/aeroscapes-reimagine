package dev.aeroscapes.regression;

import com.klikli_dev.modonomicon.book.entries.BookEntry;
import com.klikli_dev.modonomicon.book.page.BookCraftingRecipePage;
import com.klikli_dev.modonomicon.bookstate.BookUnlockStateManager;
import com.klikli_dev.modonomicon.data.BookDataManager;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_regression", value = Dist.DEDICATED_SERVER)
public final class FieldGuideRegression {
  private static final ResourceLocation GUIDE =
      ResourceLocation.parse("aeroscapes_reimagine:field_guide");
  private static final ResourceLocation BOOK = ResourceLocation.parse("modonomicon:modonomicon");
  private static final ResourceLocation BOOK_ID = ResourceLocation.parse("modonomicon:book_id");
  private static final ResourceLocation BOOK_OPEN = ResourceLocation.parse("modonomicon:book_open");

  private FieldGuideRegression() {}

  @SubscribeEvent
  public static void onStarted(ServerStartedEvent event) {
    if (!Boolean.getBoolean("aeroscapes.regression.fieldGuide")) return;
    var server = event.getServer();
    server.tell(new TickTask(server.getTickCount(), () -> verify(server.overworld())));
  }

  private static void verify(ServerLevel level) {
    buildBook(level);
    var pos = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
    var uuid = UUID.randomUUID();
    CompoundTag saved;
    try (var session = TestShipPlayerSession.create(level, uuid, "FieldGuideTest", pos, null)) {
      var player = session.player;
      login(player);
      require(countGuides(player) == 1, "A first login grants exactly one actual field guide");
      var inventory = player.getInventory();
      var guide =
          inventory.items.stream().filter(FieldGuideRegression::isGuide).findFirst().orElseThrow();
      int slot = inventory.items.indexOf(guide);
      inventory.setItem(slot, player.getMainHandItem());
      player.setItemInHand(InteractionHand.MAIN_HAND, guide);
      require(
          guide.getHoverName().getContents() instanceof TranslatableContents title
              && title.getKey().equals("book.aeroscapes_reimagine.field_guide.name"),
          "The native item resolves the loaded field guide title");
      var open = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_OPEN);
      require(
          open != null
              && guide.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
              && Boolean.TRUE.equals(guide.get(open)),
          "Using the granted item reaches Modonomicon's native open handler");
      verifyProgression(player);
      player.getInventory().clearContent();
      saved = player.saveWithoutId(new CompoundTag());
    }
    try (var session = TestShipPlayerSession.create(level, uuid, "FieldGuideTest", pos, saved)) {
      login(session.player);
      require(
          countGuides(session.player) == 0,
          "Relogging with a saved receipt never grants another guide");
      refreshBook(session.player);
      require(
          isUnlocked(session.player, "workshop/copper_ingots"),
          "Unlocked guide steps survive relog");
    }

    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "FieldGuideTest", pos, null)) {
      var player = session.player;
      var inventory = player.getInventory();
      for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
        inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
      }
      login(player);
      require(
          countGuides(player) == 0 && inventory.getFreeSlot() < 0,
          "A full inventory keeps the existing items and defers the guide");
      inventory.setItem(3, ItemStack.EMPTY);
      NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
      require(
          countGuides(player) == 1 && isGuide(inventory.getItem(3)),
          "Freeing a slot delivers the pending guide");
    }

    var input =
        CraftingInput.of(2, 1, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.STICK)));
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    var crafted = recipe.value().assemble(input, level.registryAccess());
    require(
        recipe.id().equals(GUIDE) && isGuide(crafted) && crafted.getCount() == 1,
        "Paper and stick craft one actual field guide");
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "FieldGuideTest", pos, null)) {
      var player = session.player;
      player.setItemInHand(InteractionHand.OFF_HAND, crafted);
      login(player);
      require(
          player.getOffhandItem() == crafted && countGuides(player) == 1,
          "An existing field guide in the offhand is recognized without duplication");
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
      NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
      require(countGuides(player) == 0, "Carrying an existing copy satisfies the one-time grant");
    }
    LogUtils.getLogger()
        .info(
            "Field guide login, inventory retry, native use and fallback recipe regression passed");
  }

  private static void buildBook(ServerLevel level) {
    try {
      Class<?> manager = Class.forName("com.klikli_dev.modonomicon.data.BookDataManager");
      Object data = manager.getMethod("get").invoke(null);
      // A synthetic login does not perform the native datapack synchronization.
      manager.getMethod("tryBuildBooks", Level.class).invoke(data, level);
      Class<?> errorManager =
          Class.forName("com.klikli_dev.modonomicon.book.error.BookErrorManager");
      Object errors = errorManager.getMethod("get").invoke(null);
      if (Boolean.TRUE.equals(
          errorManager.getMethod("hasErrors", ResourceLocation.class).invoke(errors, GUIDE))) {
        Object holder =
            errorManager.getMethod("getErrors", ResourceLocation.class).invoke(errors, GUIDE);
        throw new IllegalStateException(
            "Field guide has book errors: "
                + holder.getClass().getMethod("getErrors").invoke(holder));
      }
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Build guide through installed Modonomicon", failure);
    }
  }

  private static void verifyProgression(ServerPlayer player) {
    refreshBook(player);
    require(
        isUnlocked(player, "island/rock"), "Flint and stone instructions are open at the start");
    require(!isUnlocked(player, "island/knap"), "Knapping waits until two stones are collected");
    require(!isUnlocked(player, "workshop/copper_ingots"), "Copper waits until the stone pickaxe");
    require(!isUnlocked(player, "flight/preflight"), "Flight checks wait for a usable hull");
    require(isUnlocked(player, "flight/wreck_search"), "Wreck hunting is an independent route");
    require(
        isUnlocked(player, "magic/first_study") && isUnlocked(player, "magic/study_ranks"),
        "Both optional magic study guides are open from the start");
    var book = BookDataManager.get().getBook(GUIDE);
    var chapters = book.getCategoriesSorted();
    require(
        chapters.stream()
            .map(category -> category.getId().getPath())
            .toList()
            .equals(List.of("welcome", "island", "workshop", "flight", "magic")),
        "The welcome tab precedes the existing chapters and the optional Magic chapter");
    var welcome = chapters.getFirst();
    var overview = entry("welcome/overview");
    require(
        welcome.showCategoryButton()
            && overview.getId().equals(welcome.getEntryToOpen())
            && !welcome.openEntryToOpenOnlyOnce()
            && isUnlocked(player, "welcome/overview")
            && overview.getParents().isEmpty(),
        "The welcome article opens from its tab without progression on first and later visits");
    for (var chapter : book.getCategories().values()) {
      require(!chapter.getEntries().isEmpty(), "Each chapter starts with readable entries");
      for (var entry : chapter.getEntries().values()) {
        require(
            !entry.getDescription().isEmpty()
                && !entry.getPages().isEmpty()
                && entry.getUnlockedPagesFor(player).equals(entry.getPages()),
            "Future entries have readable pages and a summary: " + entry.getId());
        for (var page : entry.getPages()) {
          if (page instanceof BookCraftingRecipePage crafting) {
            require(
                crafting.getRecipe1() != null
                    && (crafting.getRecipeId2() == null || crafting.getRecipe2() != null),
                "Every crafting recipe slot resolves in the loaded entry: " + entry.getId());
          }
        }
      }
    }

    grantInventoryMilestone(player, "minecraft:oak_log", 1);
    grantInventoryMilestone(player, "minecraft:oak_planks", 4);
    grantInventoryMilestone(player, "minecraft:stick", 2);
    grantInventoryMilestone(player, "overgeared:knappable_rock", 1);
    grantInventoryMilestone(player, "overgeared:stone_pickaxe_head", 1);
    refreshBook(player);
    require(
        !isUnlocked(player, "island/knap") && !isUnlocked(player, "island/stone_pick"),
        "Vanilla actions and an out-of-order head do not skip the two-rock checkpoint");
    grantInventoryMilestone(player, "overgeared:knappable_rock", 2);
    refreshBook(player);
    require(
        isUnlocked(player, "island/knap") && isUnlocked(player, "island/stone_pick"),
        "Two stones open knapping and count an already obtained tool head");
    require(!isUnlocked(player, "workshop/copper_ingots"), "A tool head is not a finished pick");
    grantInventoryMilestone(player, "minecraft:stone_pickaxe", 1);
    refreshBook(player);
    require(isUnlocked(player, "workshop/copper_ingots"), "A finished pick opens copper");
    require(!isUnlocked(player, "flight/engine"), "The workshop route remains gated");

    for (String milestone : List.of("wreck_found", "salvage_parts", "restored_hull")) {
      finishCheckpoint(player, milestone);
    }
    refreshBook(player);
    require(isUnlocked(player, "flight/throttle_place"), "Salvaged parts open hull construction");
    require(isUnlocked(player, "flight/preflight"), "A restored hull opens the flight checks");
    require(
        !isUnlocked(player, "flight/first_flight"),
        "Restoring equipment alone does not count as passing preflight");
    require(
        !BookUnlockStateManager.get().isReadFor(player, entry("flight/first_flight")),
        "Readable pages do not mark a future step read automatically");
  }

  private static void grantInventoryMilestone(ServerPlayer player, String id, int count) {
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    var stack = new ItemStack(item, count);
    player.getInventory().setItem(8, stack);
    CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
  }

  private static void finishCheckpoint(ServerPlayer player, String checkpoint) {
    var id =
        ResourceLocation.fromNamespaceAndPath(
            GUIDE.getNamespace(), "guide/checkpoints/" + checkpoint);
    var advancement = player.server.getAdvancements().get(id);
    require(advancement != null, "Missing guide checkpoint " + id);
    player.getAdvancements().award(advancement, "done");
  }

  private static boolean isUnlocked(ServerPlayer player, String id) {
    return BookUnlockStateManager.get().isUnlockedFor(player, entry(id));
  }

  private static BookEntry entry(String id) {
    var book = BookDataManager.get().getBook(GUIDE);
    return Objects.requireNonNull(
        book.getEntry(ResourceLocation.fromNamespaceAndPath(GUIDE.getNamespace(), id)), id);
  }

  private static void refreshBook(ServerPlayer player) {
    BookUnlockStateManager.get().updateAndSyncFor(player);
  }

  private static void login(ServerPlayer player) {
    var level = player.serverLevel();
    ProgressionRegression.listPlayer(level, player, true);
    try {
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    } finally {
      ProgressionRegression.listPlayer(level, player, false);
    }
  }

  private static int countGuides(ServerPlayer player) {
    var inventory = player.getInventory();
    int count = 0;
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      if (isGuide(item)) count += item.getCount();
    }
    return count;
  }

  private static boolean isGuide(ItemStack stack) {
    var id = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_ID);
    return id != null
        && BOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
        && GUIDE.equals(stack.get(id));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
