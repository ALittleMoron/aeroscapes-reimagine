package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class ProfessionToolsRegression {
  private ProfessionToolsRegression() {}

  static void verify(ServerLevel level) {
    var position = level.getSharedSpawnPos().above(24);
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "TradeTools", Vec3.atCenterOf(position), null)) {
      ServerPlayer player = session.player;
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
                Profession.FARMER,
                "careful_tilling"),
            "Choose a farmer's second starter");
        var state = level.getBlockState(position);
        var above = level.getBlockState(position.above());
        try {
          level.setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState());
          var hoe = new ItemStack(Items.DIAMOND_HOE);
          player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
          var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
          for (int i = 0; i < 96; i++) {
            level.setBlockAndUpdate(position, Blocks.DIRT.defaultBlockState());
            hoe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            require(
                level.getBlockState(position).is(Blocks.FARMLAND), "Personal tilling succeeded");
          }
          require(
              hoe.getDamageValue() > 0 && hoe.getDamageValue() < 96, "Some tilling wear is saved");
        } finally {
          level.setBlockAndUpdate(position, state);
          level.setBlockAndUpdate(position.above(), above);
        }
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
        var planks = Blocks.OAK_PLANKS.defaultBlockState();
        var log = Blocks.OAK_LOG.defaultBlockState();
        float plankSpeed = player.getDigSpeed(planks, position);
        float logSpeed = player.getDigSpeed(log, position);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float handSpeed = player.getDigSpeed(planks, position);
        var skills = SkillsMod.getInstance();
        var category = ResourceLocation.parse(ProgressionBridge.PROFESSION_CATEGORY);
        skills.setPoints(
            player,
            category,
            ResourceLocation.parse("aeroscapes_reimagine:tools_fixture"),
            1,
            true);
        skills.tryUnlockSkill(player, category, "quick_dismantling", false);
        require(
            Math.abs(player.getDigSpeed(planks, position) - handSpeed) < 0.001,
            "Bare hands retain ordinary speed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
        require(
            Math.abs(player.getDigSpeed(planks, position) - plankSpeed * 1.15) < 0.001
                && Math.abs(player.getDigSpeed(log, position) - logSpeed) < 0.001,
            "An ordinary cross-profession purchase improves dismantling but not logging");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
