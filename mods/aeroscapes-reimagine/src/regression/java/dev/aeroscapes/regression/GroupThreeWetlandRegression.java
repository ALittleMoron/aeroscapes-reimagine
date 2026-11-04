package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import uwu.juni.wetland_whimsy.content.WetlandWhimsyBlocks;
import uwu.juni.wetland_whimsy.content.WetlandWhimsyEntityTypes;
import uwu.juni.wetland_whimsy.content.WetlandWhimsyItems;
import uwu.juni.wetland_whimsy.content.blocks.AncientBrazierBlock;
import uwu.juni.wetland_whimsy.content.blocks.AncientPotBlock;
import uwu.juni.wetland_whimsy.content.blocks.entities.AncientBrazierBlockEntity;
import uwu.juni.wetland_whimsy.content.blocks.entities.AncientPotBlockEntity;
import uwu.juni.wetland_whimsy.datagen.loot.WetlandWhimsyMiscLoot;

/** Native archaeology and ritual interactions in a restored disposable server fixture. */
final class GroupThreeWetlandRegression {
  private GroupThreeWetlandRegression() {}

  static void run(ServerLevel level, Path output) {
    BlockPos center = level.getSharedSpawnPos().offset(512, 0, 512).atY(350);
    AABB area = new AABB(center).inflate(11, 4, 11);
    Map<BlockPos, BlockState> originals = new HashMap<>();
    Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
    Set<UUID> existing = new HashSet<>();
    level.getEntitiesOfClass(Entity.class, area).forEach(entity -> existing.add(entity.getUUID()));
    FakePlayer player =
        FakePlayerFactory.get(
            level,
            new GameProfile(
                UUID.fromString("128f1464-0a7e-4647-981b-bca70e03a44f"), "WetlandCheck"));
    player.setGameMode(GameType.SURVIVAL);
    player.moveTo(Vec3.atCenterOf(center), 0, 0);
    JsonObject report = new JsonObject();
    try {
      for (BlockPos position :
          BlockPos.betweenClosed(center.offset(-10, -1, -10), center.offset(10, 4, 10))) {
        BlockPos key = position.immutable();
        originals.put(key, level.getBlockState(key));
        BlockEntity entity = level.getBlockEntity(key);
        if (entity != null)
          blockEntities.put(key, entity.saveWithFullMetadata(level.registryAccess()));
        level.setBlock(
            key,
            position.getY() == center.getY() - 1
                ? Blocks.STONE.defaultBlockState()
                : Blocks.AIR.defaultBlockState(),
            2);
      }
      level.getEntitiesOfClass(Entity.class, area).stream()
          .filter(entity -> !existing.contains(entity.getUUID()))
          .forEach(Entity::discard);
      registry(level, report);
      brush(level, player, center, true, report);
      brush(level, player, center, false, report);
      pot(level, player, center, report);
      fauna(level, player, center, report);
      rituals(level, player, center, area, existing, report);
      report.addProperty("complete", true);
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      LogUtils.getLogger().info("Group three Wetland Whimsy regression passed: {}", report);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write Wetland Whimsy report", failure);
    } finally {
      level.getEntitiesOfClass(Entity.class, area).stream()
          .filter(entity -> !existing.contains(entity.getUUID()))
          .forEach(Entity::discard);
      originals.forEach((position, state) -> level.setBlock(position, state, 2));
      blockEntities.forEach(
          (position, tag) -> {
            BlockEntity restored = level.getBlockEntity(position);
            if (restored != null) restored.loadWithComponents(tag, level.registryAccess());
          });
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }
  }

  private static void registry(ServerLevel level, JsonObject report) {
    JsonArray entries = new JsonArray();
    for (var type :
        List.of(
            WetlandWhimsyEntityTypes.CRANE.get(),
            WetlandWhimsyEntityTypes.CRAWFISH.get(),
            WetlandWhimsyEntityTypes.FILTH.get(),
            WetlandWhimsyEntityTypes.INFECTED_SPIDER.get())) {
      Entity entity = type.create(level);
      require(entity != null && entity instanceof Mob, "Wetland creature cannot be instantiated");
      entries.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
      entity.discard();
    }
    report.add("creatures", entries);
  }

  private static void brush(
      ServerLevel level, FakePlayer player, BlockPos position, boolean common, JsonObject report) {
    level.setBlock(position, WetlandWhimsyBlocks.SUSSY_MUD.get().defaultBlockState(), 2);
    require(
        level.getBlockEntity(position) instanceof BrushableBlockEntity,
        "Suspicious mud has no native brushable entity");
    var entity = (BrushableBlockEntity) level.getBlockEntity(position);
    entity.setLootTable(
        common ? WetlandWhimsyMiscLoot.SUSSY_MUD_COMMON : WetlandWhimsyMiscLoot.SUSSY_MUD_UNCOMMON,
        common ? 127 : 293);
    boolean complete = false;
    for (int stroke = 0; stroke < 10; stroke++)
      complete = entity.brush(level.getGameTime() + stroke * 10L, player, Direction.UP);
    require(
        complete && level.getBlockState(position).is(Blocks.MUD),
        "Suspicious mud did not complete brushing into mud");
    List<ItemEntity> drops =
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2));
    require(
        !drops.isEmpty() && drops.stream().noneMatch(item -> item.getItem().isEmpty()),
        "Suspicious mud returned empty archaeology loot");
    report.addProperty(
        common ? "common_mud" : "uncommon_mud", drops.getFirst().getItem().toString());
    drops.forEach(Entity::discard);
    level.removeBlock(position, false);
  }

  private static void pot(
      ServerLevel level, FakePlayer player, BlockPos position, JsonObject report) {
    BlockState state =
        WetlandWhimsyBlocks.ANCIENT_POT
            .get()
            .defaultBlockState()
            .setValue(AncientPotBlock.FACING, Direction.NORTH);
    level.setBlock(position, state, 2);
    player.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(WetlandWhimsyItems.ANCIENT_COIN.get(), 3));
    for (int count = 0; count < 2; count++) use(level, player, position);
    var pot = (AncientPotBlockEntity) level.getBlockEntity(position);
    require(
        pot.lootQuality() == 3 && player.getMainHandItem().getCount() == 1,
        "Ancient pot coin cost or quality changed");
    CompoundTag saved = pot.saveWithFullMetadata(level.registryAccess());
    BlockEntity restored = BlockEntity.loadStatic(position, state, saved, level.registryAccess());
    require(
        restored instanceof AncientPotBlockEntity
            && ((AncientPotBlockEntity) restored).lootQuality() == 3,
        "Ancient pot quality did not survive save/load");
    level.setBlockEntity(restored);
    state.getBlock().playerWillDestroy(level, position, state, player);
    level.removeBlock(position, false);
    List<ItemEntity> drops =
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2));
    require(!drops.isEmpty(), "Improved ancient pot dropped no reward");
    report.addProperty("pot_quality", 3);
    report.addProperty("pot_reward_stacks", drops.size());
    drops.forEach(Entity::discard);
  }

  private static void fauna(
      ServerLevel level, FakePlayer player, BlockPos position, JsonObject report) {
    var crane = WetlandWhimsyEntityTypes.CRANE.get().create(level);
    require(crane != null, "Crane missing");
    crane.moveTo(Vec3.atCenterOf(position), 0, 0);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BEETROOT_SEEDS, 3));
    crane.mobInteract(player, InteractionHand.MAIN_HAND);
    require(
        crane.befriended && player.getMainHandItem().getCount() == 2,
        "Feeding did not befriend crane");
    crane.setHealth(crane.getMaxHealth() - 4);
    crane.mobInteract(player, InteractionHand.MAIN_HAND);
    require(
        crane.getHealth() == crane.getMaxHealth() && player.getMainHandItem().getCount() == 1,
        "Feeding did not heal befriended crane");
    CompoundTag craneSave = crane.saveWithoutId(new CompoundTag());
    var restoredCrane = WetlandWhimsyEntityTypes.CRANE.get().create(level);
    require(restoredCrane != null, "Restored crane missing");
    restoredCrane.load(craneSave);
    require(restoredCrane.befriended, "Crane friendship did not survive save/load");
    crane.discard();
    restoredCrane.discard();
    var spider = WetlandWhimsyEntityTypes.INFECTED_SPIDER.get().create(level);
    require(spider != null, "Infected spider missing");
    spider.moveTo(Vec3.atCenterOf(position), 0, 0);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
    spider.interact(player, InteractionHand.MAIN_HAND);
    require(
        spider.isSheared() && player.getMainHandItem().getDamageValue() == 1,
        "Infected spider shearing did not consume tool durability");
    List<ItemEntity> drops =
        level.getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(2));
    require(
        !drops.isEmpty()
            && drops.stream()
                .allMatch(item -> item.getItem().is(WetlandWhimsyBlocks.FELLCAP_MUSHROOM.asItem())),
        "Infected spider shearing did not yield its mushrooms");
    spider.interact(player, InteractionHand.MAIN_HAND);
    require(player.getMainHandItem().getDamageValue() == 1, "Infected spider can be sheared twice");
    CompoundTag saved = spider.saveWithoutId(new CompoundTag());
    var restoredSpider = WetlandWhimsyEntityTypes.INFECTED_SPIDER.get().create(level);
    require(restoredSpider != null, "Restored spider missing");
    restoredSpider.load(saved);
    require(restoredSpider.isSheared(), "Spider shearing did not survive save/load");
    drops.forEach(Entity::discard);
    spider.discard();
    restoredSpider.discard();
    report.addProperty("crane_friendship_healing_and_spider_shearing", true);
  }

  private static void rituals(
      ServerLevel level,
      FakePlayer player,
      BlockPos position,
      AABB area,
      Set<UUID> existing,
      JsonObject report) {
    level.setBlock(position, WetlandWhimsyBlocks.ANCIENT_BRAZIER.get().defaultBlockState(), 2);
    JsonArray results = new JsonArray();
    for (var incense :
        List.of(
            WetlandWhimsyItems.BOILING_INCENSE.get(),
            WetlandWhimsyItems.BRINE_INCENSE.get(),
            WetlandWhimsyItems.ROT_INCENSE.get(),
            WetlandWhimsyItems.WEBBED_INCENSE.get())) {
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(incense, 2));
      use(level, player, position);
      var brazier = (AncientBrazierBlockEntity) level.getBlockEntity(position);
      require(
          brazier.hasIncense() && player.getMainHandItem().getCount() == 1,
          "Incense did not enter native brazier");
      require(
          brazier.getIncense(level).orElseThrow().item() == incense,
          "Brazier resolved the wrong incense definition");
      CompoundTag active = brazier.saveWithFullMetadata(level.registryAccess());
      BlockEntity restored =
          BlockEntity.loadStatic(
              position, level.getBlockState(position), active, level.registryAccess());
      require(
          restored instanceof AncientBrazierBlockEntity
              && ((AncientBrazierBlockEntity) restored).hasIncense(),
          "Active incense did not survive save/load");
      level.setBlockEntity(restored);
      brazier = (AncientBrazierBlockEntity) restored;
      use(level, player, position);
      require(player.getMainHandItem().getCount() == 1, "Active brazier consumed a second incense");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
      use(level, player, position);
      require(
          level.getBlockState(position).getValue(AncientBrazierBlock.FLAME)
                  == AncientBrazierBlock.Flame.LIT
              && player.getMainHandItem().getDamageValue() == 1,
          "Native ignition or tool cost failed");
      int ticks = 0;
      for (; ticks < 6000 && brazier.hasIncense(); ticks++)
        AncientBrazierBlockEntity.serverTick(
            level, position, level.getBlockState(position), brazier);
      require(
          !brazier.hasIncense()
              && level.getBlockState(position).getValue(AncientBrazierBlock.FLAME)
                  == AncientBrazierBlock.Flame.SMOLDERING,
          "Native ritual did not finish: "
              + incense
              + " "
              + brazier.saveWithFullMetadata(level.registryAccess()));
      List<Mob> mobs =
          level.getEntitiesOfClass(Mob.class, area).stream()
              .filter(entity -> !existing.contains(entity.getUUID()))
              .toList();
      List<ItemEntity> loot =
          level.getEntitiesOfClass(ItemEntity.class, area).stream()
              .filter(entity -> !existing.contains(entity.getUUID()))
              .toList();
      require(
          !mobs.isEmpty() && !loot.isEmpty(), "Ritual spawned no encounter or reward: " + incense);
      CompoundTag complete = brazier.saveWithFullMetadata(level.registryAccess());
      require(
          complete.getList("used_incenses", Tag.TAG_STRING).stream()
              .anyMatch(
                  value ->
                      value
                          .getAsString()
                          .equals(BuiltInRegistries.ITEM.getKey(incense).toString())),
          "Used incense was not saved");
      BlockEntity finished =
          BlockEntity.loadStatic(
              position, level.getBlockState(position), complete, level.registryAccess());
      require(finished instanceof AncientBrazierBlockEntity, "Finished brazier did not reload");
      level.setBlockEntity(finished);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(incense));
      use(level, player, position);
      require(
          player.getMainHandItem().getCount() == 1
              && !((AncientBrazierBlockEntity) finished).hasIncense(),
          "Used incense can be repeated after save/load");
      JsonObject result = new JsonObject();
      result.addProperty("incense", BuiltInRegistries.ITEM.getKey(incense).toString());
      result.addProperty("simulated_native_ticks", ticks);
      result.addProperty("mobs", mobs.size());
      result.addProperty("reward", loot.getFirst().getItem().toString());
      results.add(result);
      mobs.forEach(Entity::discard);
      loot.forEach(Entity::discard);
    }
    report.add("rituals", results);
  }

  private static void use(ServerLevel level, FakePlayer player, BlockPos position) {
    level
        .getBlockState(position)
        .useItemOn(
            player.getMainHandItem(),
            level,
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
