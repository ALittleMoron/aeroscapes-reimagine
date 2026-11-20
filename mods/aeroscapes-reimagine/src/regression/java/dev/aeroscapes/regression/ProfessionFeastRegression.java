package dev.aeroscapes.regression;

import dev.aeroscapes.races.ProfessionEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import vectorwing.farmersdelight.common.block.FeastBlock;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;

final class ProfessionFeastRegression {
  private ProfessionFeastRegression() {}

  static List<ItemStack> prepare(ServerLevel level, ServerPlayer cook, BlockPos pos) {
    var original = level.getBlockState(pos);
    var support = level.getBlockState(pos.below());
    var platters = new ArrayList<ItemStack>();
    try {
      for (String name :
          List.of(
              "roast_chicken_block",
              "honey_glazed_ham_block",
              "shepherds_pie_block",
              "rice_roll_medley_block",
              "stuffed_pumpkin_block")) {
        boolean potRecipe = name.equals("stuffed_pumpkin_block");
        var recipe =
            level
                .getRecipeManager()
                .byKey(id((potRecipe ? "cooking/" : "") + name))
                .orElseThrow()
                .value();
        var ingredients = recipe.getIngredients();
        var cooked =
            potRecipe
                ? cookPumpkin(level, cook, pos, ingredients)
                : craftFeast(level, cook, pos, ingredients);
        require(
            cooked.is(BuiltInRegistries.ITEM.get(id(name)))
                && ProfessionEffects.preparedTraits(cooked) == 3,
            "Personal preparation marks the whole " + name);
        platters.add(cooked);
      }
      return platters;
    } finally {
      level.setBlockAndUpdate(pos, original);
      level.setBlockAndUpdate(pos.below(), support);
    }
  }

  static void verifyGuest(
      ServerLevel level, ServerPlayer guest, BlockPos pos, List<ItemStack> platters) {
    var original = level.getBlockState(pos);
    var support = level.getBlockState(pos.below());
    try {
      level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
      for (var cooked : platters) {
        serve(level, guest, pos, cooked, 3);
        serve(level, guest, pos, cooked.getItem().getDefaultInstance(), 0);
      }
    } finally {
      level.setBlockAndUpdate(pos, original);
      level.setBlockAndUpdate(pos.below(), support);
    }
  }

  private static ItemStack craftFeast(
      ServerLevel level, ServerPlayer cook, BlockPos pos, List<Ingredient> ingredients) {
    var menu = new CraftingMenu(4, cook.getInventory(), ContainerLevelAccess.create(level, pos));
    for (int i = 0; i < ingredients.size(); i++)
      menu.getSlot(i + 1).set(ingredients.get(i).getItems()[0].copyWithCount(1));
    menu.clicked(0, 0, ClickType.PICKUP, cook);
    return menu.getCarried().copy();
  }

  private static ItemStack cookPumpkin(
      ServerLevel level, ServerPlayer cook, BlockPos pos, List<Ingredient> ingredients) {
    level.setBlockAndUpdate(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
    level.setBlockAndUpdate(
        pos, BuiltInRegistries.BLOCK.get(id("cooking_pot")).defaultBlockState());
    var pot = (CookingPotBlockEntity) level.getBlockEntity(pos);
    var menu = (CookingPotMenu) pot.createMenu(4, cook.getInventory(), cook);
    for (int i = 0; i < ingredients.size(); i++) {
      menu.setCarried(ingredients.get(i).getItems()[0].copyWithCount(1));
      menu.clicked(i, 0, ClickType.PICKUP, cook);
    }
    for (int tick = 0; tick < 420; tick++)
      CookingPotBlockEntity.cookingTick(level, pos, level.getBlockState(pos), pot);
    return pot.useHeldItemOnMeal(new ItemStack(Items.PUMPKIN));
  }

  private static void serve(
      ServerLevel level, ServerPlayer guest, BlockPos pos, ItemStack platter, int traits) {
    level.removeBlock(pos, false);
    guest.getInventory().clearContent();
    var blockItem = (BlockItem) platter.getItem();
    var feast = (FeastBlock) blockItem.getBlock();
    var support = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
    var hand = InteractionHand.MAIN_HAND;
    guest.setItemInHand(hand, platter.copy());
    require(
        blockItem
            .place(new BlockPlaceContext(guest, hand, guest.getItemInHand(hand), support))
            .consumesAction(),
        "Place feast");
    var state = level.getBlockState(pos);
    var serialized = NbtUtils.writeBlockState(state);
    if (traits == 0)
      serialized.getCompound("Properties").remove(ProfessionEffects.FEAST_TRAITS.getName());
    state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), serialized);
    level.setBlockAndUpdate(pos, state);
    require(
        state.is(feast) && state.getValue(ProfessionEffects.FEAST_TRAITS) == traits,
        "Saved feast retains prepared traits, legacy state defaults to ordinary");
    var drop =
        Block.getDrops(state, level, pos, null, guest, ItemStack.EMPTY).stream()
            .filter(stack -> stack.is(blockItem))
            .findFirst()
            .orElseThrow();
    require(ProfessionEffects.preparedTraits(drop) == traits, "Intact feast can be moved");
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    for (int count = feast.getMaxServings(); count > 0; count--) {
      var current = level.getBlockState(pos);
      require(
          current.getValue(feast.getServingsProperty()) == count, "A serving consumes one portion");
      var portion = feast.getServingItem(current);
      guest.getInventory().clearContent();
      guest.setItemInHand(hand, portion.getCraftingRemainingItem());
      current.useItemOn(guest.getItemInHand(hand), level, guest, hand, hit);
      require(
          guest.getInventory().items.stream()
              .anyMatch(
                  stack ->
                      stack.is(portion.getItem())
                          && stack.getCount() == 1
                          && ProfessionEffects.preparedTraits(stack) == traits),
          "Guest receives the current native serving with the cook's traits");
    }
    var empty = level.getBlockState(pos);
    require(
        !empty.is(feast) || empty.getValue(feast.getServingsProperty()) == 0,
        "All servings consumed exactly once");
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath("farmersdelight", name);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
