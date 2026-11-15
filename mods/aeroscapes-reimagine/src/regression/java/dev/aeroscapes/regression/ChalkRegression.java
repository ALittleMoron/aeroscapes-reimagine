package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class ChalkRegression {
  private ChalkRegression() {}

  public static void verify(ServerLevel level) {
    var recipe =
        level.getRecipeManager().byKey(ResourceLocation.parse("chalk:chalk")).orElseThrow();
    var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.CALCITE)));
    require(recipe.value().getType() == RecipeType.CRAFTING, "Native calcite crafting recipe");
    var crafting = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    require(crafting.isPresent(), "Calcite has a survival crafting route");
    var chalk = BuiltInRegistries.ITEM.get(ResourceLocation.parse("chalk:chalk"));
    var stack = crafting.orElseThrow().value().assemble(input, level.registryAccess());
    require(stack.is(chalk) && stack.getCount() == 1, "One calcite yields one native chalk");
    for (var material : List.of("chalk_powder", "white_chalk")) {
      var ingredient =
          BuiltInRegistries.ITEM.get(ResourceLocation.parse("natures_spirit:" + material));
      var chalkInput = CraftingInput.of(1, 1, List.of(new ItemStack(ingredient)));
      var route =
          level
              .getRecipeManager()
              .getRecipeFor(RecipeType.CRAFTING, chalkInput, level)
              .orElseThrow();
      var result = route.value().assemble(chalkInput, level.registryAccess());
      require(
          material.equals("white_chalk")
              ? result.is(
                      BuiltInRegistries.ITEM.get(
                          ResourceLocation.parse("natures_spirit:chalk_powder")))
                  && result.getCount() == 4
              : result.is(chalk) && result.getCount() == 1,
          "Nature's Spirit block yields four powders; each powder yields one nonstackable chalk");
      require(
          route.value().getRemainingItems(chalkInput).stream().allMatch(ItemStack::isEmpty),
          "Raw chalk recipes have no returned material");
    }
    var stone = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 8);
    var face = stone.north();
    var previousStone = level.getBlockState(stone);
    var previousFace = level.getBlockState(face);
    try {
      level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(face, Blocks.AIR.defaultBlockState());
      var player =
          new FakePlayer(
              level,
              new GameProfile(
                  UUID.fromString("cd910277-a8df-4035-aed8-5245012a122e"), "ChalkMark"));
      player.setItemInHand(InteractionHand.MAIN_HAND, stack);
      var hit =
          new BlockHitResult(Vec3.atCenterOf(stone).add(0, 0, -0.5), Direction.NORTH, stone, false);
      stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
      require(
          BuiltInRegistries.BLOCK
              .getKey(level.getBlockState(face).getBlock())
              .equals(ResourceLocation.parse("chalk:mark")),
          "Using native chalk on a solid face places a physical mark");
      require(stack.getDamageValue() > 0, "Drawing consumes native chalk durability");
    } finally {
      level.setBlockAndUpdate(face, previousFace);
      level.setBlockAndUpdate(stone, previousStone);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
