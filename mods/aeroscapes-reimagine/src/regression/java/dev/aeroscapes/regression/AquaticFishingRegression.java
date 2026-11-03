package dev.aeroscapes.regression;

import com.li64.tide.Tide;
import com.li64.tide.compat.seasons.Season;
import com.li64.tide.data.TideData;
import com.li64.tide.data.commands.TestType;
import com.li64.tide.data.fishing.CatchResult;
import com.li64.tide.data.fishing.FishData;
import com.li64.tide.data.fishing.FishingContext;
import com.li64.tide.data.fishing.mediums.FishingMedium;
import com.li64.tide.data.journal.JournalGroup;
import com.li64.tide.data.rods.CustomRodManager;
import com.li64.tide.registries.TideEntityTypes;
import com.li64.tide.registries.TideItems;
import com.li64.tide.registries.entities.misc.LootCrateEntity;
import com.li64.tide.registries.entities.misc.fishing.HookAccessor;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import com.li64.tide.registries.items.TideFishingRodItem;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.aquatic.AquaticWater;
import dev.aeroscapes.mixin.aquatic.TideHookAccess;
import dev.hybridlabs.aquatic.item.HAItems;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Checks the loaded Tide selector and the foreign loot-modifier boundary it actually calls. */
public final class AquaticFishingRegression {
  private AquaticFishingRegression() {}

  public static void run(ServerLevel level, BlockPos shallow, BlockPos deep) {
    require(
        level.getFluidState(shallow).is(FluidTags.WATER), "Fishing reward witness is not water");
    require(AquaticWater.catchCoordinate(level, shallow) > 40, "Reward witness is not shallow");
    Set<ResourceLocation> nativeFish = new HashSet<>();
    for (String category : new String[] {"small_fish", "medium_fish", "large_fish"}) {
      var tag =
          TagKey.create(
              Registries.ITEM, ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", category));
      for (var item : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
        var id = BuiltInRegistries.ITEM.getKey(item.value());
        if (!"hybrid_aquatic".equals(id.getNamespace())) continue;
        nativeFish.add(id);
        var data =
            FishData.get(item.value())
                .orElseThrow(
                    () ->
                        new IllegalStateException("Native fishing item missing from Tide: " + id));
        require(
            data.weight() > 0 && data.hasJournalEntry(),
            "Native fish has no Tide catch/journal route: " + id);
      }
    }
    require(nativeFish.size() == 52, "Native HA fishing-item roster changed: " + nativeFish.size());
    var groups =
        Map.of(
            "african_butterflyfish",
            JournalGroup.FRESHWATER,
            "trout",
            JournalGroup.FRESHWATER,
            "hagfish",
            JournalGroup.UNDERGROUND);
    for (var group : groups.entrySet()) {
      var item =
          BuiltInRegistries.ITEM.get(
              ResourceLocation.fromNamespaceAndPath("hybrid_aquatic", group.getKey()));
      var fish = FishData.get(item).orElseThrow();
      require(
          fish.profile().group() == group.getValue(),
          "Repaired fish lost native habitat journal category: " + group.getKey());
      for (BlockPos position : new BlockPos[] {shallow, deep}) {
        var water = context(level, position, "water");
        require(
            fish.shouldKeep(water),
            "Repaired fish acquired a nonnative water restriction: " + group.getKey());
        var selectable = Tide.FISHING_MANAGER.test(water, TestType.FISH);
        require(
            selectable.containsKey(fish) && selectable.get(fish) > 0,
            "Repaired fish is absent from the actual Tide selector: " + group.getKey());
        var result = fish.getResult(water);
        require(
            result.isFish() && result.items().size() == 1 && result.items().getFirst().is(item),
            "Repaired fish yields the wrong registered item");
        require(!fish.shouldKeep(context(level, position, "lava")), "Water fish accepted lava");
      }
    }
    rewards(level, shallow);
    hookPerks(level, shallow);
    crates(level, shallow);
    LogUtils.getLogger()
        .info(
            "Aquatic fishing roster passed: native HA fish={}, repaired={} shallow={} deep={}",
            nativeFish.size(),
            groups.keySet(),
            shallow,
            deep);
  }

  private static void hookPerks(ServerLevel level, BlockPos position) {
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack saved = player.getMainHandItem();
    Vec3 savedPosition = player.position();
    long savedTime = level.getDayTime();
    float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
    try {
      player.setPos(Vec3.atCenterOf(position.above()));
      level.setRainLevel(0);
      level.setThunderLevel(0);
      for (var item :
          new net.minecraft.world.item.Item[] {Items.FISHING_ROD, TideItems.STONE_FISHING_ROD}) {
        require(
            item instanceof TideFishingRodItem, "Ordinary rods no longer use unified Tide fishing");
        var rodItem = (TideFishingRodItem) item;
        for (long time : new long[] {6000, 18000}) {
          level.setDayTime(time);
          level.updateSkyBrightness();
          for (var attachment :
              new net.minecraft.world.item.Item[] {
                HAItems.INSTANCE.getBARBED_HOOK().get(),
                HAItems.INSTANCE.getGLOWING_HOOK().get(),
                HAItems.INSTANCE.getMAGNETIC_HOOK().get()
              }) {
            var rod = new ItemStack(item);
            CustomRodManager.setHook(rod, new ItemStack(attachment));
            player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            rodItem.castHook(rod, player, level, 1);
            var hook = HookAccessor.getHook(player);
            require(hook != null, "Native rod did not cast its attached hook");
            try {
              int speed =
                  (attachment == HAItems.INSTANCE.getBARBED_HOOK().get() && level.isDay())
                          || (attachment == HAItems.INSTANCE.getGLOWING_HOOK().get()
                              && level.isNight())
                      ? 1
                      : 0;
              int luck = attachment == HAItems.INSTANCE.getMAGNETIC_HOOK().get() ? 1 : 0;
              require(
                  hook.getLureSpeed() == speed && hook.getLuck() == luck,
                  "Registered HA hook lost its active Tide perk: "
                      + BuiltInRegistries.ITEM.getKey(attachment));
            } finally {
              hook.discard();
            }
          }
        }
      }
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, saved);
      player.setPos(savedPosition);
      level.setDayTime(savedTime);
      level.setRainLevel(rain);
      level.setThunderLevel(thunder);
      level.updateSkyBrightness();
    }
    LogUtils.getLogger()
        .info(
            "Aquatic native hook perks passed for ordinary/stone rods in day/night: barbed,"
                + " glowing, magnetic");
  }

  private static void crates(ServerLevel level, BlockPos position) {
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack saved = player.getMainHandItem();
    Vec3 savedPosition = player.position();
    var state = level.getBlockState(position);
    var area = new AABB(position).inflate(6);
    Set<java.util.UUID> before =
        level.getEntitiesOfClass(Entity.class, area).stream()
            .map(Entity::getUUID)
            .collect(java.util.stream.Collectors.toSet());
    TideFishingHook hook = null;
    try {
      var rod = new ItemStack(Items.FISHING_ROD);
      player.setItemInHand(InteractionHand.MAIN_HAND, rod);
      player.setPos(Vec3.atCenterOf(position.above()));
      hook = new TideFishingHook(TideEntityTypes.FISHING_BOBBER, player, level, 0, 0, 1, rod);
      hook.setPos(Vec3.atCenterOf(position));
      var context = context(level, position, "water");
      var crate =
          TideData.CRATES.get().values().stream()
              .filter(data -> data.associatedMods().contains("hybrid_aquatic"))
              .findFirst()
              .orElseThrow();
      require(crate.lootTable().isEmpty(), "HA crates acquired a fabricated inventory loot table");
      var result = crate.getResult(context);
      require(
          result.isCrate() && result.isPresent() && result.items().isEmpty(),
          "A block-only native crate is rejected as an empty catch");
      require(CatchResult.empty().isEmpty(), "The crate repair changed genuinely empty catches");
      require(
          Tide.FISHING_MANAGER.getCrateSelector().shouldKeep(context),
          "Top fishing selector rejects native block-only crates");
      require(
          Tide.FISHING_MANAGER.test(context, TestType.CRATES).containsKey(crate),
          "HA crates are absent from actual native crate selection");
      Set<ResourceLocation> blocks = new HashSet<>();
      var random = RandomSource.create(42);
      for (int sample = 0; sample < 512; sample++)
        blocks.add(
            BuiltInRegistries.BLOCK.getKey(
                crate.blockProvider().getState(random, position).getBlock()));
      require(
          blocks.size() == 11
              && blocks.stream().allMatch(id -> "hybrid_aquatic".equals(id.getNamespace())),
          "Native HA crate provider lost its eleven rewards");
      var access = (TideHookAccess) hook;
      access.aeroscapes$crateData(crate);
      access.aeroscapes$catchType(TideFishingHook.CatchType.CRATE);
      access.aeroscapes$hookedItems(result.items());
      access.aeroscapes$medium(FishingMedium.WATER);
      access.aeroscapes$fluidState(state.getFluidState());
      access.aeroscapes$nibble(20);
      require(
          hook.hasHookedItem(),
          "Native retrieval rejects a selected crate without loose item rewards");
      require(hook.retrieve(rod, level, player) == 1, "Native crate retrieval lost rod wear");
      var rewards =
          level.getEntitiesOfClass(Entity.class, area).stream()
              .filter(entity -> !before.contains(entity.getUUID()))
              .toList();
      require(
          rewards.size() == 1 && rewards.getFirst() instanceof LootCrateEntity,
          "Crate retrieval did not produce exactly one actual native flying crate");
      var entity = (LootCrateEntity) rewards.getFirst();
      var crateState = entity.getBlockState();
      require(
          blocks.contains(BuiltInRegistries.BLOCK.getKey(crateState.getBlock())),
          "Retrieved crate is not a native HA reward");
      var contents =
          Block.getDrops(crateState, level, position, null, player, new ItemStack(Items.IRON_AXE));
      require(
          !contents.isEmpty()
              && contents.stream().anyMatch(stack -> !stack.is(crateState.getBlock().asItem())),
          "Retrieved HA crate lost its native axe-opening treasure");
      require(
          level.getBlockState(position).equals(state),
          "Native crate retrieval changed its basin water");
      LogUtils.getLogger()
          .info(
              "Aquatic actual HA crate retrieval passed: block={} nativeAxeTreasure={}",
              BuiltInRegistries.BLOCK.getKey(crateState.getBlock()),
              contents);
    } finally {
      if (hook != null) hook.discard();
      level.getEntitiesOfClass(Entity.class, area).stream()
          .filter(entity -> !before.contains(entity.getUUID()))
          .forEach(Entity::discard);
      level.setBlockAndUpdate(position, state);
      player.setItemInHand(InteractionHand.MAIN_HAND, saved);
      player.setPos(savedPosition);
    }
  }

  private static void rewards(ServerLevel level, BlockPos shallow) {
    var context = context(level, shallow, "water");
    var params = context.createFishingLootParams();
    for (String table : new String[] {"junk", "treasure"}) {
      var id = ResourceLocation.parse("minecraft:gameplay/fishing/" + table);
      var entry =
          TideData.FISHING_LOOT.get().values().stream()
              .filter(data -> data.lootTable().location().equals(id))
              .findFirst()
              .orElseThrow();
      require(
          entry.shouldKeep(context) && entry.weight(context) > 0,
          "Tide no longer selects the native fishing subtable: " + id);
      var loot = level.getServer().reloadableRegistries().getLootTable(entry.lootTable());
      Set<ResourceLocation> results = new HashSet<>();
      var ink = ResourceLocation.parse("supplementaries:antique_ink");
      var sponge = ResourceLocation.parse("legendarysurvivaloverhaul:sponge");
      for (long seed = 1; seed <= 2048; seed++) {
        for (ItemStack stack : loot.getRandomItems(params, seed))
          results.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (results.contains(ink) && (!"treasure".equals(table) || results.contains(sponge))) break;
      }
      require(results.contains(ink), "Tide subtable lost Supplementaries antique ink: " + id);
      if ("treasure".equals(table))
        require(results.contains(sponge), "Tide treasure subtable lost LSO sponge");
    }
    LogUtils.getLogger()
        .info(
            "Aquatic native fishing subtable modifiers passed: antique ink from junk/treasure; LSO"
                + " sponge from treasure");
  }

  private static FishingContext context(ServerLevel level, BlockPos position, String medium) {
    var hook = new TideFishingHook(TideEntityTypes.FISHING_BOBBER, level);
    hook.setPos(Vec3.atCenterOf(position));
    return new FishingContext(
        level,
        hook,
        new ItemStack(Items.FISHING_ROD),
        RandomSource.create(9),
        Vec3.atCenterOf(position),
        position,
        0,
        medium,
        level.getBiome(position),
        level.getBiome(position),
        level.dimension(),
        0.5f,
        0,
        Season.SUMMER);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
