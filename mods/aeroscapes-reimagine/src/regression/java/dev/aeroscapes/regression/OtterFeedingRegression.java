package dev.aeroscapes.regression;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

final class OtterFeedingRegression {
  private OtterFeedingRegression() {}

  static void verify(ServerLevel level, ServerPlayer player) {
    GameType previous = player.gameMode.getGameModeForPlayer();
    try {
      for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
        player.setGameMode(mode);
        feed(level, player, mode, 3);
        feed(level, player, mode, 1);
      }
    } finally {
      player.setGameMode(previous);
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
  }

  private static void feed(ServerLevel level, ServerPlayer player, GameType mode, int count) {
    var type =
        BuiltInRegistries.ENTITY_TYPE
            .getOptional(ResourceLocation.parse("crittersandcompanions:otter"))
            .orElseThrow();
    var entity = type.create(level);
    require(entity instanceof Animal, "Missing native otter");
    Animal otter = (Animal) entity;
    var clam =
        BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.parse("crittersandcompanions:clam"))
            .orElseThrow();
    try {
      otter.setAge(6000);
      otter.moveTo(player.position());
      require(otter.getMainHandItem().isEmpty(), "Fresh otter already holds food");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(clam, count));
      otter.interact(player, InteractionHand.MAIN_HAND);
      int expected = mode == GameType.CREATIVE ? count : count - 1;
      require(
          player.getMainHandItem().getCount() == expected,
          "Otter feeding lost or duplicated player food: mode=" + mode + " stack=" + count);
      require(
          otter.getMainHandItem().is(clam) && otter.getMainHandItem().getCount() == 1,
          "Otter feeding did not transfer exactly one food item");
      require(
          mode == GameType.CREATIVE
              || player.getMainHandItem().getCount() + otter.getMainHandItem().getCount() == count,
          "Survival feeding did not conserve food");
    } finally {
      otter.discard();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
