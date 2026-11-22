package dev.aeroscapes.regression;

import dev.aeroscapes.expedition.ExpeditionDiscoveries;
import dev.aeroscapes.magic.PreparedAbilities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class ExpeditionStudyRegression {
  private ExpeditionStudyRegression() {}

  static void verify(ServerLevel level) {
    verifyPools(level);
    BlockPos floor = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 16);
    Vec3 position = Vec3.atBottomCenterOf(floor.above());
    var breath = scroll("deep_breath_scroll");
    var distraction = scroll("distraction_throw_scroll");
    UUID identity = UUID.randomUUID();
    CompoundTag saved;
    try (var session =
        TestShipPlayerSession.create(level, identity, "StudyFixture", position, null)) {
      var player = session.player;
      PreparedAbilities.edit(player, 0, PreparedAbilities.DISTRACTION);
      require(
          PreparedAbilities.preparation(player).slots().getFirst().isEmpty(),
          "Locked technique cannot be assigned");
      double originalOxygen = player.getAttributeValue(Attributes.OXYGEN_BONUS);
      player.setItemInHand(InteractionHand.MAIN_HAND, breath);
      breath.getItem().use(level, player, InteractionHand.MAIN_HAND);
      require(breath.getCount() == 1, "Learning consumes one Deep Breath scroll");
      require(
          player.getAttributeValue(Attributes.OXYGEN_BONUS) == originalOxygen + 0.25,
          "Deep Breath slows air loss using the native oxygen attribute");
      breath.getItem().use(level, player, InteractionHand.MAIN_HAND);
      require(breath.getCount() == 1, "Duplicate Deep Breath remains tradable");

      player.setItemInHand(InteractionHand.MAIN_HAND, distraction);
      distraction.getItem().use(level, player, InteractionHand.MAIN_HAND);
      distraction.getItem().use(level, player, InteractionHand.MAIN_HAND);
      require(distraction.getCount() == 1, "Duplicate technique remains tradable");
      require(ExpeditionDiscoveries.knowsDistraction(player), "Learned technique is available");
      PreparedAbilities.edit(player, 0, PreparedAbilities.DISTRACTION);
      PreparedAbilities.select(player, 0);
      PreparedAbilities.cast(player);
      require(ExpeditionDiscoveries.distractionCooldownTicks(player) == 0, "No snowball spent");
      var ammunition = new ItemStack(Items.SNOWBALL, 3);
      player.setItemInHand(InteractionHand.OFF_HAND, ammunition);
      PreparedAbilities.cast(player);
      PreparedAbilities.cast(player);
      require(ammunition.getCount() == 2, "One snowball spent and rapid repeat blocked");
      var snowball =
          level.getEntitiesOfClass(Snowball.class, new AABB(position, position).inflate(4)).stream()
              .filter(projectile -> projectile.getOwner() == player)
              .findFirst()
              .orElseThrow();
      try {
        verifyImpact(level, floor, snowball);
      } finally {
        snowball.discard();
      }
      saved = player.saveWithoutId(new CompoundTag());
    }

    try (var session =
        TestShipPlayerSession.create(level, identity, "StudyFixture", position, saved)) {
      var player = session.player;
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
      require(ExpeditionDiscoveries.knowsDistraction(player), "Technique survives relogging");
      require(
          player
              .getAttribute(Attributes.OXYGEN_BONUS)
              .hasModifier(ResourceLocation.parse("aeroscapes_reimagine:deep_breath")),
          "Deep Breath is restored on relog");
    }
  }

  private static ItemStack scroll(String path) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path)),
        2);
  }

  private static void verifyPools(ServerLevel level) {
    for (String table :
        List.of(
            "irons_spellbooks:chests/catacombs/dead_king_vault",
            "irons_spellbooks:chests/citadel/citadel_vault",
            "nova_structures:chests/lone_citadel/c_vault_boss",
            "irons_spellbooks:entities/dead_king",
            "irons_spellbooks:entities/fire_boss")) {
      require(hasStudyPool(level, table), "Missing shared loot pool: " + table);
    }
    for (String table :
        List.of(
            "irons_spellbooks:entities/dead_king_ominous",
            "irons_spellbooks:entities/dead_king_per_player",
            "irons_spellbooks:entities/dead_king_per_player_ominous",
            "irons_spellbooks:entities/fire_boss_ominous",
            "irons_spellbooks:entities/fire_boss_per_player",
            "irons_spellbooks:entities/fire_boss_per_player_ominous",
            "minecraft:chests/simple_dungeon")) {
      require(!hasStudyPool(level, table), "Unexpected study loot pool: " + table);
    }
  }

  private static boolean hasStudyPool(ServerLevel level, String location) {
    ResourceKey<LootTable> key =
        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(location));
    return level
            .getServer()
            .reloadableRegistries()
            .getLootTable(key)
            .getPool("aeroscapes_reimagine:expedition_study")
        != null;
  }

  private static void verifyImpact(ServerLevel level, BlockPos floor, Snowball snowball) {
    for (BlockPos pos : BlockPos.betweenClosed(floor.offset(1, 0, -2), floor.offset(12, 0, 2))) {
      level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }
    BlockPos impactBlock = floor.offset(12, 1, 0);
    level.setBlockAndUpdate(impactBlock, Blocks.STONE.defaultBlockState());
    BlockHitResult hit =
        new BlockHitResult(Vec3.atCenterOf(impactBlock), Direction.WEST, impactBlock, false);
    var zombie = EntityType.ZOMBIE.create(level);
    if (zombie == null) throw new IllegalStateException("Missing fixture zombie");
    Vec3 feet = Vec3.atBottomCenterOf(floor.offset(7, 1, 0));
    zombie.setPos(feet);
    zombie.setOnGround(true);
    require(level.addFreshEntity(zombie), "Add zombie to test platform");
    try {
      var ordinary = new Snowball(level, snowball.getOwner().getX(), feet.y, feet.z);
      NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(ordinary, hit));
      require(zombie.getNavigation().getPath() == null, "Ordinary snowballs do not distract");
      zombie.setTarget((net.minecraft.world.entity.LivingEntity) snowball.getOwner());
      NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(snowball, hit));
      zombie.goalSelector.tick();
      require(zombie.getTarget() == snowball.getOwner(), "An engaged zombie keeps its target");
      zombie.setTarget(null);
      zombie.getNavigation().stop();
      NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(snowball, hit));
      zombie.goalSelector.tick();
      var path = zombie.getNavigation().getPath();
      require(
          path != null && path.canReach() && path.getTarget().equals(impactBlock.west()),
          "An idle zombie paths to the reachable side of the impact");
      zombie.setTarget((net.minecraft.world.entity.LivingEntity) snowball.getOwner());
      zombie.goalSelector.tick();
      require(zombie.getTarget() == snowball.getOwner(), "The reaction permits normal detection");
    } finally {
      zombie.discard();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
