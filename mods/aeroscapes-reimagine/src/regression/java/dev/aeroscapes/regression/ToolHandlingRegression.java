package dev.aeroscapes.regression;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.ProfessionProgression;
import harmonised.pmmo.api.APIUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.puffish.skillsmod.SkillsMod;

final class ToolHandlingRegression {
  private static final ProfessionDiscipline PICKS = ProfessionDiscipline.PICKAXE_HANDLING;

  private ToolHandlingRegression() {}

  static void verify(ServerLevel level, ServerPlayer player, BlockPos position) {
    var before = level.getBlockState(position);
    var beforeAbove = level.getBlockState(position.above());
    try {
      level.setBlockAndUpdate(position.above(), Blocks.AIR.defaultBlockState());
      practice(level, player, position);
      pickaxes(level, player, position);
      otherFamilies(level, player, position);
    } finally {
      level.setBlockAndUpdate(position, before);
      level.setBlockAndUpdate(position.above(), beforeAbove);
      level
          .getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(3))
          .forEach(ItemEntity::discard);
    }
  }

  private static void practice(ServerLevel level, ServerPlayer player, BlockPos position) {
    var tools =
        List.of(
            new Practice(PICKS, Items.WOODEN_PICKAXE, Blocks.STONE, false),
            new Practice(ProfessionDiscipline.AXE_HANDLING, Items.WOODEN_AXE, Blocks.OAK_LOG, true),
            new Practice(
                ProfessionDiscipline.SHOVEL_HANDLING,
                Items.WOODEN_SHOVEL,
                Blocks.GRASS_BLOCK,
                true),
            new Practice(ProfessionDiscipline.HOE_HANDLING, Items.WOODEN_HOE, Blocks.DIRT, true));
    APIUtils.setLevel("mining", player, 30);
    for (var practice : tools) {
      var tool = practice.item().getDefaultInstance();
      player.setItemInHand(InteractionHand.MAIN_HAND, tool);
      level.setBlockAndUpdate(position, practice.block().defaultBlockState());
      long previous = APIUtils.getXp(practice.tree().skill(), player);
      long picks = APIUtils.getXp(PICKS.skill(), player);
      if (practice.useOn()) {
        require(
            tool.useOn(context(player, position)).consumesAction(),
            "Tool transforms the target: " + practice.item());
      } else {
        require(player.gameMode.destroyBlock(position), "Personal mining succeeds");
      }
      require(
          APIUtils.getXp(practice.tree().skill(), player) > previous,
          "Only matching practice advances " + practice.tree().skill());
      if (practice.tree() != PICKS)
        require(
            APIUtils.getXp(PICKS.skill(), player) == picks,
            "Other actions teach no pickaxe handling");
    }
    require(APIUtils.getLevel("mining", player) >= 30, "Existing Gathering levels remain");
  }

  private static void pickaxes(ServerLevel level, ServerPlayer player, BlockPos position) {
    var stone = Blocks.STONE.defaultBlockState();
    var obsidian = Blocks.OBSIDIAN.defaultBlockState();
    player.setOnGround(true);
    require(
        speed(player, Items.WOODEN_PICKAXE, Blocks.STONE, position) == 2,
        "Wood starts at full speed");
    require(
        speed(player, Items.STONE_PICKAXE, Blocks.STONE, position) == 4,
        "Stone starts at full speed");

    ProgressionBridge.sync(player);
    require(
        speed(player, Items.IRON_PICKAXE, Blocks.STONE, position) == 3,
        "Gathering cannot train iron handling");
    require(!tryBuy(player, PICKS, "copper"), "A separate balance is required");
    APIUtils.setLevel(PICKS.skill(), player, 10);
    ProgressionBridge.sync(player);
    require(
        speed(player, Items.IRON_PICKAXE, Blocks.STONE, position) == 3,
        "Unspent points confer no speed");
    require(!tryBuy(player, PICKS, "diamond"), "Tier prerequisites prevent skipping ahead");

    verifyTier(
        player,
        position,
        "copper",
        List.of("overgeared:copper_pickaxe", "hybrid_aquatic:seashell_pickaxe"));
    verifyTier(
        player,
        position,
        "iron",
        List.of(
            "minecraft:iron_pickaxe", "minecraft:golden_pickaxe", "hybrid_aquatic:coral_pickaxe"));
    float trainedIron = speed(player, Items.IRON_PICKAXE, Blocks.STONE, position);
    float noviceDiamond = speed(player, Items.DIAMOND_PICKAXE, Blocks.STONE, position);
    require(trainedIron == 6 && noviceDiamond == 4, "Trained iron beats undertrained diamond");

    level.setBlockAndUpdate(position, obsidian);
    require(player.gameMode.destroyBlock(position), "Untrained diamond can break obsidian");
    var obsidianDrops =
        level.getEntitiesOfClass(
            ItemEntity.class, new AABB(position), drop -> drop.getItem().is(Items.OBSIDIAN));
    require(obsidianDrops.size() == 1, "Untrained diamond still harvests obsidian");

    var enchanted = Items.DIAMOND_PICKAXE.getDefaultInstance();
    enchanted.enchant(
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.EFFICIENCY),
        2);
    player.setItemInHand(InteractionHand.MAIN_HAND, enchanted);
    player.doTick();
    player.setOnGround(true);
    float noviceEnchantBonus = player.getDigSpeed(stone, position) - noviceDiamond;
    require(noviceEnchantBonus > 0, "Applied Efficiency works before training");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.doTick();
    player.setOnGround(true);

    verifyTier(player, position, "steel", List.of("overgeared:steel_pickaxe"));
    verifyTier(player, position, "diamond", List.of("minecraft:diamond_pickaxe"));
    verifyTier(player, position, "netherite", List.of("minecraft:netherite_pickaxe"));
    player.setItemInHand(InteractionHand.MAIN_HAND, enchanted);
    player.doTick();
    player.setOnGround(true);
    require(
        Math.abs(player.getDigSpeed(stone, position) - 8 - noviceEnchantBonus) < 0.001,
        "Learning the talent preserves the same Efficiency contribution");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.doTick();
    player.setOnGround(true);
  }

  private static void verifyTier(
      ServerPlayer player, BlockPos position, String talent, List<String> items) {
    var stone = Blocks.STONE.defaultBlockState();
    for (String id : items) {
      var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
      require(
          speed(player, item, Blocks.STONE, position)
              == item.getDefaultInstance().getDestroySpeed(stone) * 0.5F,
          "Untrained speed for " + id);
      require(speed(player, item, Blocks.DIRT, position) == 1, "Unrelated block speed for " + id);
    }
    require(tryBuy(player, PICKS, talent), "Purchase " + talent);
    for (String id : items) {
      var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
      require(
          speed(player, item, Blocks.STONE, position)
              == item.getDefaultInstance().getDestroySpeed(stone),
          "Trained speed for " + id);
    }
  }

  private static void otherFamilies(ServerLevel level, ServerPlayer player, BlockPos position) {
    for (var practice :
        List.of(
            new Practice(ProfessionDiscipline.AXE_HANDLING, Items.IRON_AXE, Blocks.OAK_LOG, true),
            new Practice(
                ProfessionDiscipline.SHOVEL_HANDLING, Items.IRON_SHOVEL, Blocks.GRASS_BLOCK, true),
            new Practice(
                ProfessionDiscipline.HOE_HANDLING, Items.IRON_HOE, Blocks.OAK_LEAVES, true))) {
      float untrained = speed(player, practice.item(), practice.block(), position);
      require(
          untrained
              == practice
                      .item()
                      .getDefaultInstance()
                      .getDestroySpeed(practice.block().defaultBlockState())
                  * 0.5F,
          "The pickaxe purchases do not train " + practice.item());
      APIUtils.setLevel(practice.tree().skill(), player, 2);
      ProgressionBridge.sync(player);
      require(
          tryBuy(player, practice.tree(), "copper") && tryBuy(player, practice.tree(), "iron"),
          "Purchase " + practice.tree().skill());
      require(
          speed(player, practice.item(), practice.block(), position) == untrained * 2,
          "The matching tree trains " + practice.item());
    }
    shears(level, player, position);
  }

  private static void shears(ServerLevel level, ServerPlayer player, BlockPos position) {
    var tree = ProfessionDiscipline.SHEARS_HANDLING;
    float novice = speed(player, Items.SHEARS, Blocks.WHITE_WOOL, position);
    var stack = player.getMainHandItem();
    var sheep = new Sheep(EntityType.SHEEP, level);
    sheep.setPos(Vec3.atCenterOf(position));
    try {
      long previous = APIUtils.getXp(tree.skill(), player);
      require(
          stack.interactLivingEntity(player, sheep, InteractionHand.MAIN_HAND).consumesAction()
              && sheep.isSheared(),
          "Untrained shears still shear");
      require(APIUtils.getXp(tree.skill(), player) > previous, "Shearing gives separate practice");
      require(player.getCooldowns().isOnCooldown(Items.SHEARS), "Untrained shearing adds a delay");

      APIUtils.setLevel(tree.skill(), player, 1);
      ProgressionBridge.sync(player);
      require(tryBuy(player, tree, "cutting"), "Purchase the shears talent");
      require(
          speed(player, Items.SHEARS, Blocks.WHITE_WOOL, position) > novice, "Shears regain speed");
    } finally {
      sheep.discard();
      player.getCooldowns().removeCooldown(Items.SHEARS);
    }
  }

  private static UseOnContext context(ServerPlayer player, BlockPos position) {
    var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
    return new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
  }

  private static float speed(ServerPlayer player, Item item, Block block, BlockPos position) {
    player.setItemInHand(InteractionHand.MAIN_HAND, item.getDefaultInstance());
    return player.getDigSpeed(block.defaultBlockState(), position);
  }

  private static boolean tryBuy(ServerPlayer player, ProfessionDiscipline tree, String talent) {
    SkillsMod.getInstance().tryUnlockSkill(player, tree.category(), talent, false);
    return ProfessionProgression.hasTalent(player, tree, talent);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private record Practice(ProfessionDiscipline tree, Item item, Block block, boolean useOn) {}
}
