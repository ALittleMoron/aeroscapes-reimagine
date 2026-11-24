package dev.aeroscapes.regression;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ProfessionEffects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.puffish.skillsmod.SkillsMod;

final class ProfessionFurnaceRegression {
  private ProfessionFurnaceRegression() {}

  static void verify(ServerLevel level) {
    var pos = level.getSharedSpawnPos().above(32);
    ItemStack cooked;
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "FurnaceCook", Vec3.atCenterOf(pos), null)) {
      var cook = session.player;
      ProgressionRegression.listPlayer(level, cook, true);
      try {
        var category = ProfessionDiscipline.COOKING.category();
        SkillsMod.getInstance().unlockSkill(cook, category, "hearty_meal");
        SkillsMod.getInstance().unlockSkill(cook, category, "quick_meal");
        cooked = verifyStation(level, pos, Blocks.FURNACE, cook, new ItemStack(Items.BEEF));
        verifyStation(level, pos, Blocks.SMOKER, cook, new ItemStack(Items.BEEF));
        for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING)) {
          if (recipe.value().getResultItem(level.registryAccess()).has(DataComponents.FOOD)) {
            var inputs = recipe.value().getIngredients().getFirst().getItems();
            if (inputs.length != 0)
              verifyStation(level, pos, Blocks.BLAST_FURNACE, cook, inputs[0]);
            break;
          }
        }
      } finally {
        ProgressionRegression.listPlayer(level, cook, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "FurnaceGuest", Vec3.atCenterOf(pos), null)) {
      var guest = session.player;
      var plain = new ItemStack(Items.COOKED_BEEF);
      require(
          cooked.getUseDuration(guest) * 4 == plain.getUseDuration(guest) * 3,
          "Transferred furnace meal eats faster");
      guest.getFoodData().setFoodLevel(8);
      guest.getFoodData().setSaturation(0);
      cooked.finishUsingItem(level, guest);
      require(
          Math.abs(
                  guest.getFoodData().getSaturationLevel()
                      - plain.get(DataComponents.FOOD).saturation()
                      - 1)
              < 0.001,
          "Guest receives the furnace cook's extra saturation");
    }
  }

  private static ItemStack verifyStation(
      ServerLevel level, BlockPos pos, Block block, ServerPlayer cook, ItemStack ingredient) {
    var previous = level.getBlockState(pos);
    try {
      level.setBlockAndUpdate(pos, block.defaultBlockState());
      var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
      var menu = (AbstractFurnaceMenu) furnace.createMenu(3, cook.getInventory(), cook);
      furnace.setItem(1, new ItemStack(Items.COAL, 4));
      cook.getInventory().setItem(0, ingredient.copyWithCount(2));
      menu.clicked(30, 0, ClickType.QUICK_MOVE, cook);
      require(
          furnace.getItem(0).getCount() == 2 && cook.getInventory().getItem(0).isEmpty(),
          "Shift-load two ingredients in " + block);
      require(
          HopperBlockEntity.addItem(null, furnace, ingredient.copyWithCount(1), Direction.UP)
              .isEmpty(),
          "Hopper appends a third ingredient");
      cook(level, pos, furnace, 600);
      ItemStack result = furnace.getItem(2).copy();
      require(
          result.getCount() >= 2
              && ProfessionEffects.preparedTraits(result) == 3
              && furnace.getItem(0).getCount() == 1,
          "Two personal portions cook; an ordinary portion waits behind marked output in " + block);
      furnace.setItem(2, new ItemStack(result.getItem(), result.getMaxStackSize()));
      cook(level, pos, furnace, 205);
      require(
          furnace.getItem(0).getCount() == 1
              && furnace.getItem(2).getCount() == result.getMaxStackSize(),
          "Full result slot blocks the next portion");
      furnace.setItem(2, ItemStack.EMPTY);
      cook(level, pos, furnace, 205);
      require(
          furnace.getItem(0).isEmpty()
              && furnace.getItem(2).is(result.getItem())
              && ProfessionEffects.preparedTraits(furnace.getItem(2)) == 0,
          "Hopper-fed serving has no personal bonus");
      menu.clicked(2, 0, ClickType.PICKUP, cook);
      require(
          menu.getCarried().is(result.getItem())
              && ProfessionEffects.preparedTraits(menu.getCarried()) == 0,
          "Cook collecting automated output cannot mark it");
      menu.setCarried(ingredient.copyWithCount(2));
      menu.clicked(0, 0, ClickType.PICKUP, cook);
      require(
          HopperBlockEntity.addItem(null, furnace, ingredient.copyWithCount(1), Direction.UP)
              .isEmpty(),
          "Append an ordinary ingredient to a personal batch before extraction");
      require(furnace.removeItem(0, 2).getCount() == 2, "Extract the supplied portions");
      require(
          HopperBlockEntity.addItem(null, furnace, ingredient.copyWithCount(2), Direction.UP)
              .isEmpty(),
          "Refill the extracted slots without a cook");
      cook(level, pos, furnace, 605);
      require(
          furnace.getItem(0).isEmpty() && ProfessionEffects.preparedTraits(furnace.getItem(2)) == 0,
          "Extracted personal ingredients cannot lend traits to a later automated refill");
      return result;
    } finally {
      if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)
        furnace.clearContent();
      level.setBlockAndUpdate(pos, previous);
    }
  }

  private static void cook(
      ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity furnace, int ticks) {
    for (int i = 0; i < ticks; i++)
      AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
