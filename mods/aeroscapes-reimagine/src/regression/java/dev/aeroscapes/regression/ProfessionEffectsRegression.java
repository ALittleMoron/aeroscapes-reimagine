package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.ProfessionEffects;
import dev.aeroscapes.races.ProfessionPot;
import dev.aeroscapes.races.ProfessionProgression;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import dev.emi.emi.network.FillRecipeC2SPacket;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.block.entity.StoveBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

final class ProfessionEffectsRegression {
  private ProfessionEffectsRegression() {}

  static void verify(ServerLevel level) {
    var pos = level.getSharedSpawnPos().above(30);
    ItemStack meal;
    List<ItemStack> feasts;
    ItemStack cookedChicken;
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "TradeEffects", Vec3.atCenterOf(pos), null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.WARRIOR,
                "quick_block",
                Profession.COOK,
                "hearty_meal"),
            "Choose a cook");
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        var skills = SkillsMod.getInstance();
        var anvil = new ModAnvilMenu(1, player.getInventory());
        var damage = repair(anvil, Items.DIAMOND_PICKAXE, 1, 0);
        int netheriteDamage = repair(anvil, Items.NETHERITE_PICKAXE, 1, 0);
        int normalUnit = 1000 - damage;
        require(normalUnit > 0, "Untrained material repair restores durability");
        for (String id :
            List.of(
                "long_reach", "material_repair", "frugal_repair", "extra_harvest", "quick_meal")) {
          var category = ProfessionDiscipline.byLegacyNode(id).orElseThrow().category();
          skills.setPoints(
              player,
              category,
              ResourceLocation.parse("aeroscapes_reimagine:trade_effects_fixture"),
              2,
              true);
          skills.tryUnlockSkill(player, category, id, false);
          require(ProfessionProgression.learned(player, id), "Buy ordinary talent " + id);
        }
        require(
            Math.abs(player.blockInteractionRange() - blockRange - 1) < 0.001
                && player.entityInteractionRange() == entityRange,
            "Exactly one extra block of reach, unchanged melee range");
        require(
            repair(anvil, Items.DIAMOND_PICKAXE, 1, 0) == damage - normalUnit / 4
                && anvil.getCost() == 1,
            "One material repairs 25% more with a minimum one-level price");
        require(
            repair(anvil, Items.NETHERITE_PICKAXE, 1, 0)
                == netheriteDamage - (1000 - netheriteDamage) / 4,
            "Quark's diamond material repairs netherite with the same single 25% bonus");
        require(
            repair(anvil, Items.DIAMOND_PICKAXE, 3, 4) == 0
                && anvil.repairItemCountCost == 3
                && anvil.getCost() == 6,
            "Three-material repair caps durability and reduces XP once");
        player.giveExperienceLevels(10);
        int xp = player.experienceLevel;
        anvil.clicked(2, 0, ClickType.PICKUP, player);
        require(
            player.experienceLevel == xp - 6
                && anvil.getCarried().is(Items.DIAMOND_PICKAXE)
                && anvil.getSlot(1).getItem().isEmpty(),
            "Taking the repaired tool charges the quoted XP and actual materials");
        anvil.setCarried(ItemStack.EMPTY);
        anvil.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        anvil.setItemName("Fixture");
        require(anvil.getCost() == 0, "Free renaming keeps its native price");
        harvest(level, pos, player);
        meal = craftMeal(level, pos, player);
        automaticCraft(level, pos.east(3));
        pot(level, pos, player);
        feasts = ProfessionFeastRegression.prepare(level, player, pos.east(3));
        cookedChicken = hotSurfaces(level, pos, player);
        cut(level, pos, player, new ItemStack(Items.COOKED_CHICKEN), "cooked_chicken_cuts", 3);
        cut(level, pos, player, new ItemStack(Items.CHICKEN), "chicken_cuts", 0);
        cut(
            level,
            pos,
            FakePlayerFactory.getMinecraft(level),
            new ItemStack(Items.COOKED_CHICKEN),
            "cooked_chicken_cuts",
            0);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "MealGuest", Vec3.atCenterOf(pos), null)) {
      var guest = session.player;
      ProfessionFeastRegression.verifyGuest(level, guest, pos.east(3), feasts);
      cut(level, pos, guest, cookedChicken, "cooked_chicken_cuts", 3);
      require(!ProfessionProgression.learned(guest, "hearty_meal"), "Guest has no cooking talent");
      var ordinary = new ItemStack(Items.MUSHROOM_STEW);
      require(
          meal.getUseDuration(guest) == ordinary.getUseDuration(guest) * 3 / 4,
          "A guest eats the transferred dish 25% faster");
      float normal = eat(level, guest, ordinary);
      require(
          Math.abs(eat(level, guest, meal) - normal - 1) < 0.001,
          "A guest receives one actual extra saturation");
    }
  }

  private static int repair(
      ModAnvilMenu menu, net.minecraft.world.item.Item target, int count, int prior) {
    var tool = new ItemStack(target);
    tool.setDamageValue(1000);
    tool.set(DataComponents.REPAIR_COST, prior);
    menu.getSlot(1).set(ItemStack.EMPTY);
    menu.getSlot(0).set(tool);
    menu.getSlot(1).set(new ItemStack(Items.DIAMOND, count));
    menu.createResult();
    require(menu.getSlot(2).hasItem(), "Anvil offers the repaired tool");
    return menu.getSlot(2).getItem().getDamageValue();
  }

  private static void harvest(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var crop = (CropBlock) Blocks.WHEAT;
    int bonus = 0;
    player.getRandom().setSeed(67);
    for (int i = 0; i < 100; i++) {
      int wheat =
          countWheat(
              Block.getDrops(crop.getStateForAge(7), level, pos, null, player, ItemStack.EMPTY));
      require(wheat == 1 || wheat == 2, "A mature crop gains at most one primary product");
      bonus += wheat - 1;
      require(
          Block.getDrops(
                      Blocks.PUMPKIN.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY)
                  .stream()
                  .filter(stack -> stack.is(Items.PUMPKIN))
                  .mapToInt(ItemStack::getCount)
                  .sum()
              == 1,
          "A picked-up, placed pumpkin never multiplies");
      require(
          countWheat(
                  Block.getDrops(crop.getStateForAge(4), level, pos, null, player, ItemStack.EMPTY))
              == 0,
          "Immature crops yield no bonus");
      require(
          countWheat(
                  Block.getDrops(
                      crop.getStateForAge(7),
                      level,
                      pos,
                      null,
                      FakePlayerFactory.getMinecraft(level),
                      ItemStack.EMPTY))
              == 1,
          "Mechanical harvesting has ordinary yield");
    }
    require(bonus > 0 && bonus < 100, "Mature manual harvest sometimes grants a bonus");
  }

  private static int countWheat(List<ItemStack> drops) {
    return drops.stream()
        .filter(stack -> stack.is(Items.WHEAT))
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  private static ItemStack craftMeal(ServerLevel level, BlockPos pos, ServerPlayer cook) {
    var menu = new CraftingMenu(2, cook.getInventory(), ContainerLevelAccess.create(level, pos));
    menu.getSlot(1).set(new ItemStack(Items.BOWL));
    menu.getSlot(2).set(new ItemStack(Items.RED_MUSHROOM));
    menu.getSlot(4).set(new ItemStack(Items.BROWN_MUSHROOM));
    require(menu.getSlot(0).getItem().is(Items.MUSHROOM_STEW), "Personal crafting produces stew");
    menu.clicked(0, 0, ClickType.QUICK_MOVE, cook);
    for (var stack : cook.getInventory().items) {
      if (stack.is(Items.MUSHROOM_STEW)) {
        require(
            ProfessionEffects.preparedTraits(stack) == 3,
            "Shift-click transfers both cooking effects");
        return ItemStack.parse(level.registryAccess(), stack.save(level.registryAccess()))
            .orElseThrow();
      }
    }
    throw new IllegalStateException("Shift-click did not deliver the dish");
  }

  private static void automaticCraft(ServerLevel level, BlockPos pos) {
    var old = level.getBlockState(pos);
    try {
      var state = Blocks.CRAFTER.defaultBlockState();
      level.setBlockAndUpdate(pos, state);
      var crafter = (CrafterBlockEntity) level.getBlockEntity(pos);
      crafter.setItem(0, new ItemStack(Items.BOWL));
      crafter.setItem(1, new ItemStack(Items.RED_MUSHROOM));
      crafter.setItem(3, new ItemStack(Items.BROWN_MUSHROOM));
      state.tick(level, pos, level.random);
      var results =
          level.getEntitiesOfClass(
              ItemEntity.class,
              new AABB(pos).inflate(3),
              item -> item.getItem().is(Items.MUSHROOM_STEW));
      require(
          results.size() == 1
              && ProfessionEffects.preparedTraits(results.getFirst().getItem()) == 0,
          "A redstone crafter produces ordinary stew");
      results.getFirst().discard();
    } finally {
      level.setBlockAndUpdate(pos, old);
    }
  }

  private static float eat(ServerLevel level, ServerPlayer guest, ItemStack dish) {
    guest.getFoodData().setFoodLevel(8);
    guest.getFoodData().setSaturation(0);
    dish.finishUsingItem(level, guest);
    return guest.getFoodData().getSaturationLevel();
  }

  private static void pot(ServerLevel level, BlockPos pos, ServerPlayer player) {
    var before = level.getBlockState(pos);
    var below = level.getBlockState(pos.below());
    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:cooking_pot"));
    var rice = BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:rice"));
    try {
      level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
      level.setBlockAndUpdate(pos, block.defaultBlockState());
      var pot = (CookingPotBlockEntity) level.getBlockEntity(pos);
      require(pot.isHeated(), "Cooking pot has native heat");
      var menu = (CookingPotMenu) pot.createMenu(3, player.getInventory(), player);
      recipeViewerFill(menu, player, new ItemStack(rice));
      menu.getSlot(0).set(new ItemStack(rice, 2));
      player.getInventory().setItem(0, new ItemStack(rice, 2));
      menu.clicked(0, 0, ClickType.SWAP, player);
      require(
          ProfessionPot.traits(pot) == 3,
          "A hotbar swap supplies two personally loaded grains even when item and count match");
      menu.getSlot(0).set(ItemStack.EMPTY);
      menu.setCarried(new ItemStack(rice, 2));
      menu.clicked(0, 0, ClickType.PICKUP, player);
      var input = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
      require(
          input != null && input.insertItem(0, new ItemStack(rice), false).isEmpty(),
          "A hopper tops up the same pot slot while its two personally supplied portions are waiting");
      tickPot(level, pos, pot);
      var cooked = pot.useHeldItemOnMeal(new ItemStack(Items.BOWL));
      require(
          !cooked.isEmpty() && ProfessionEffects.preparedTraits(cooked) == 3,
          "Both improvements survive serving a personally loaded pot");
      require(
          pot.getMeal().getCount() == 1 && ProfessionEffects.preparedTraits(pot.getMeal()) == 3,
          "Both consecutive portions of the manually loaded batch keep their traits");
      menu.setCarried(new ItemStack(Items.BOWL, 2));
      menu.clicked(CookingPotBlockEntity.CONTAINER_SLOT, 0, ClickType.PICKUP, player);
      tickPot(level, pos, pot);
      var outputSlot = menu.getSlot(CookingPotBlockEntity.OUTPUT_SLOT);
      require(
          outputSlot.getItem().getCount() == 1
              && ProfessionEffects.preparedTraits(outputSlot.getItem()) == 3
              && pot.getMeal().getCount() == 1
              && ProfessionEffects.preparedTraits(pot.getMeal()) == 0,
          "The machine-supplied serving stays separate from the skilled cook's previous serving");
      menu.clicked(CookingPotBlockEntity.OUTPUT_SLOT, 0, ClickType.QUICK_MOVE, player);
      tickPot(level, pos, pot);
      require(
          !outputSlot.getItem().isEmpty()
              && ProfessionEffects.preparedTraits(outputSlot.getItem()) == 0,
          "Supplying bowls cannot claim automated food, and taking the preceding serving unblocks it");
      menu.clicked(CookingPotBlockEntity.OUTPUT_SLOT, 0, ClickType.PICKUP, player);
      require(
          !menu.getCarried().isEmpty() && ProfessionEffects.preparedTraits(menu.getCarried()) == 0,
          "Collecting a machine-prepared serving cannot improve it");
      menu.setCarried(ItemStack.EMPTY);
      player.getInventory().add(new ItemStack(Items.BONE));
      player.getInventory().add(new ItemStack(Items.RED_MUSHROOM));
      var recipe =
          level
              .getRecipeManager()
              .byKey(ResourceLocation.parse("farmersdelight:cooking/bone_broth"))
              .orElseThrow();
      player.awardRecipes(List.of(recipe));
      menu.handlePlacement(false, recipe, player);
      for (int i = 0; i < 700; i++)
        CookingPotBlockEntity.cookingTick(level, pos, pot.getBlockState(), pot);
      var broth = pot.getMeal().isEmpty() ? outputSlot.getItem() : pot.getMeal();
      require(
          broth.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:bone_broth")))
              && ProfessionEffects.preparedTraits(broth) == 3
              && broth.getUseDuration(player) == 24
              && new ItemStack(broth.getItem()).getUseDuration(player) == 32,
          "Custom drinkable broth uses the actual shorter consumption time");
    } finally {
      level.setBlockAndUpdate(pos, before);
      level.setBlockAndUpdate(pos.below(), below);
    }
  }

  private static void recipeViewerFill(
      CookingPotMenu menu, ServerPlayer player, ItemStack ingredient) {
    var previous = player.containerMenu;
    var inventorySlots = menu.slots.subList(9, 45);
    var cookingSlots = menu.slots.subList(0, 6);
    player.containerMenu = menu;
    try {
      if (ModList.get().isLoaded("emi")) EmiFill.verify(menu, player, ingredient);
      menu.getSlot(9).set(ingredient.copy());
      var operation =
          Class.forName("mezz.jei.common.transfer.TransferOperation")
              .getConstructor(int.class, int.class)
              .newInstance(9, 0);
      Class.forName("mezz.jei.common.transfer.BasicRecipeTransferHandlerServer")
          .getMethod(
              "setItems",
              Player.class,
              List.class,
              List.class,
              List.class,
              boolean.class,
              boolean.class)
          .invoke(null, player, List.of(operation), cookingSlots, inventorySlots, false, false);
      require(
          menu.getSlot(0).getItem().is(ingredient.getItem())
              && ProfessionPot.traits(menu.blockEntity) == 3,
          "JEI fills the pot from the cook's inventory and credits the ingredient");
      menu.getSlot(0).set(ItemStack.EMPTY);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(
          "Could not exercise the pinned JEI server recipe transfer", error);
    } finally {
      player.containerMenu = previous;
    }
  }

  private static final class EmiFill {
    private static void verify(CookingPotMenu menu, ServerPlayer player, ItemStack ingredient) {
      menu.getSlot(9).set(ingredient.copy());
      new FillRecipeC2SPacket(
              menu,
              0,
              menu.slots.subList(9, 45),
              menu.slots.subList(0, 6),
              null,
              List.of(ingredient.copy()))
          .apply(player);
      require(
          menu.getSlot(0).getItem().is(ingredient.getItem())
              && ProfessionPot.traits(menu.blockEntity) == 3,
          "EMI fills the pot from the cook's inventory and credits the ingredient");
      menu.getSlot(0).set(ItemStack.EMPTY);
    }
  }

  private static void tickPot(ServerLevel level, BlockPos pos, CookingPotBlockEntity pot) {
    for (int tick = 0; tick < 240; tick++) {
      CookingPotBlockEntity.cookingTick(level, pos, level.getBlockState(pos), pot);
    }
  }

  private static ItemStack hotSurfaces(ServerLevel level, BlockPos pos, ServerPlayer cook) {
    var before = level.getBlockState(pos);
    var below = level.getBlockState(pos.below());
    var above = level.getBlockState(pos.above());
    var ingredient = new ItemStack(Items.BEEF);
    try {
      level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
      var fireState = Blocks.CAMPFIRE.defaultBlockState();
      level.setBlockAndUpdate(pos, fireState);
      var fire = (CampfireBlockEntity) level.getBlockEntity(pos);
      require(
          fire.placeFood(cook, ingredient.copy(), 1) && fire.placeFood(null, ingredient.copy(), 1),
          "Separate player and dispenser campfire inputs");
      CampfireBlockEntity.cookTick(level, pos, fireState, fire);
      requireSteaks(level, pos, 1, 1);
      var stoveState =
          BuiltInRegistries.BLOCK
              .get(ResourceLocation.parse("farmersdelight:stove"))
              .defaultBlockState()
              .setValue(BlockStateProperties.LIT, true);
      level.setBlockAndUpdate(pos, stoveState);
      var stove = (StoveBlockEntity) level.getBlockEntity(pos);
      cook.setItemInHand(InteractionHand.MAIN_HAND, ingredient.copy());
      var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
      require(
          stoveState
              .useItemOn(cook.getMainHandItem(), level, cook, InteractionHand.MAIN_HAND, hit)
              .consumesAction(),
          "Player places a steak onto the stove");
      require(
          stove.addItem(
              ingredient.copy(),
              stove.getMatchingRecipe(ingredient).orElseThrow(),
              stove.getNextEmptySlot()),
          "Unattributed stove input occupies a separate position");
      for (int i = 0; i < 850; i++) StoveBlockEntity.cookingTick(level, pos, stoveState, stove);
      requireSteaks(level, pos, 1, 1);
      var panBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:skillet"));
      level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
      level.setBlockAndUpdate(pos, panBlock.defaultBlockState());
      var pan = (SkilletBlockEntity) level.getBlockEntity(pos);
      require(
          pan.addItemToCook(ingredient.copyWithCount(2), cook).isEmpty(),
          "Place two servings in a skillet");
      for (int i = 0; i < 1800; i++)
        SkilletBlockEntity.cookingTick(level, pos, pan.getBlockState(), pan);
      requireSteaks(level, pos, 2, 0);
      var heldPan = new ItemStack(panBlock);
      cook.setItemInHand(InteractionHand.MAIN_HAND, heldPan);
      cook.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CHICKEN));
      heldPan.use(level, cook, InteractionHand.MAIN_HAND);
      heldPan.finishUsingItem(level, cook);
      cook.stopUsingItem();
      return cook.getInventory().items.stream()
          .filter(
              stack ->
                  stack.is(Items.COOKED_CHICKEN) && ProfessionEffects.preparedTraits(stack) == 3)
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Holding a skillet over heat must personally cook the chicken in the other hand"))
          .copy();
    } finally {
      level.setBlockAndUpdate(pos, before);
      level.setBlockAndUpdate(pos.below(), below);
      level.setBlockAndUpdate(pos.above(), above);
    }
  }

  private static void cut(
      ServerLevel level,
      BlockPos pos,
      ServerPlayer cook,
      ItemStack ingredient,
      String expected,
      int traits) {
    var before = level.getBlockState(pos);
    try {
      var boardBlock =
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:cutting_board"));
      level.setBlockAndUpdate(pos, boardBlock.defaultBlockState());
      var board = (CuttingBoardBlockEntity) level.getBlockEntity(pos);
      var knife =
          new ItemStack(
              BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:flint_knife")));
      require(
          board.addItem(ingredient) && board.processStoredItemUsingTool(knife, cook),
          "Cut an ingredient with the native knife recipe");
      var item =
          BuiltInRegistries.ITEM.get(
              ResourceLocation.fromNamespaceAndPath("farmersdelight", expected));
      int portions = 0;
      for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
        if (drop.getItem().is(item)) {
          require(
              ProfessionEffects.preparedTraits(drop.getItem()) == traits,
              "Cut portions carry their cook's traits");
          portions += drop.getItem().getCount();
          drop.discard();
        } else if (drop.getItem().is(Items.BONE_MEAL)) drop.discard();
      }
      require(portions == 2, "Native cutting yield stays at two chicken cuts");
    } finally {
      level.setBlockAndUpdate(pos, before);
    }
  }

  private static void requireSteaks(
      ServerLevel level, BlockPos pos, int expectedSkilled, int expectedOrdinary) {
    int skilled = 0;
    int ordinary = 0;
    for (var entity :
        level.getEntitiesOfClass(
            ItemEntity.class,
            new AABB(pos).inflate(2),
            item -> item.getItem().is(Items.COOKED_BEEF))) {
      var steak = entity.getItem();
      if (ProfessionEffects.preparedTraits(steak) == 3) skilled += steak.getCount();
      else if (ProfessionEffects.preparedTraits(steak) == 0) ordinary += steak.getCount();
      entity.discard();
    }
    require(
        skilled == expectedSkilled && ordinary == expectedOrdinary,
        "Actual hot surface servings "
            + skilled
            + "/"
            + ordinary
            + ", expected "
            + expectedSkilled
            + "/"
            + expectedOrdinary);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
