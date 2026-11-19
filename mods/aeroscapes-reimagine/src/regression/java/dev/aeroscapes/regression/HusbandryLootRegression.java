package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.athisworkshop.animalhusbandry.config.ModConfig;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;

/** Kills native cattle to cover trait rewards both inside and before the normal loot event. */
public final class HusbandryLootRegression {
  private HusbandryLootRegression() {}

  public static void verify(ServerLevel level) {
    Vec3 position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 64, 0);
    float originalChance = ModConfig.INSTANCE.premiumGoldenBeefChance;
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "HusbandryLoot", position, null)) {
      for (String trait : List.of("", "heavyweight", "premium")) {
        ModConfig.INSTANCE.premiumGoldenBeefChance = 0;
        verifyDeath(
            level, session.player, position, trait, false, false, trait.equals("premium") ? 1 : 0);
        verifyDeath(
            level, session.player, position, trait, true, false, trait.equals("premium") ? 1 : 0);
        verifyDeath(
            level, session.player, position, trait, false, true, trait.equals("premium") ? 1 : 0);
      }
      ModConfig.INSTANCE.premiumGoldenBeefChance = 1;
      verifyDeath(level, session.player, position, "premium", false, false, 2);
      verifyDeath(level, session.player, position, "premium", true, false, 2);
      verifyDeath(level, session.player, position, "premium", false, true, 2);
    } finally {
      ModConfig.INSTANCE.premiumGoldenBeefChance = originalChance;
    }
    LogUtils.getLogger().info("Husbandry trait loot regression passed");
  }

  private static void verifyDeath(
      ServerLevel level,
      ServerPlayer attacker,
      Vec3 position,
      String trait,
      boolean burning,
      boolean magicFire,
      int golden) {
    Cow cow = EntityType.COW.create(level);
    require(cow != null, "Native cow creation");
    cow.setPos(position);
    cow.setAge(0);
    var genetics = ModAttachments.getGenetics(cow);
    var care = ModAttachments.getCareData(cow);
    require(genetics != null && care != null, "Native cattle genetics and care attachments");
    genetics.isInitialized = true;
    genetics.trait = trait;
    care.happiness = 0;
    care.isDomesticated = false;
    if (burning) cow.setRemainingFireTicks(60);
    require(level.addFreshEntity(cow), "Native cow is added to its disposable world");
    Set<UUID> previous =
        level.getEntitiesOfClass(ItemEntity.class, cow.getBoundingBox().inflate(4)).stream()
            .map(ItemEntity::getUUID)
            .collect(Collectors.toSet());
    var drops = new ArrayList<ItemStack>();
    Consumer<LivingDropsEvent> capture =
        event -> {
          if (event.getEntity() == cow) {
            for (ItemEntity drop : event.getDrops()) drops.add(drop.getItem().copy());
            event.setCanceled(true);
          }
        };
    NeoForge.EVENT_BUS.addListener(capture);
    try {
      DamageSource source =
          magicFire
              ? new DamageSource(
                  level.registryAccess().holderOrThrow(ISSDamageTypes.FIRE_MAGIC), attacker)
              : level.damageSources().playerAttack(attacker);
      require(
          cow.hurt(source, 10000) && cow.isDeadOrDying(), "Actual player-attributed cattle death");
      // The provider's die-HEAD reward predates vanilla's captureDrops scope.
      for (ItemEntity drop :
          level.getEntitiesOfClass(ItemEntity.class, cow.getBoundingBox().inflate(4))) {
        if (!previous.contains(drop.getUUID())) {
          drops.add(drop.getItem().copy());
          drop.discard();
        }
      }
      boolean cooked = burning || magicFire;
      require(
          count(drops, cooked ? "minecraft:beef" : "minecraft:cooked_beef") == 0,
          "Cattle food has the right cooked state: trait=" + trait);
      int beef = count(drops, cooked ? "minecraft:cooked_beef" : "minecraft:beef");
      require(
          beef >= (trait.equals("heavyweight") ? 2 : 1)
              && beef <= (trait.equals("heavyweight") ? 6 : 3),
          "Native beef count ranges survive");
      require(
          count(
                  drops,
                  cooked ? "animalhusbandry:cooked_golden_beef" : "animalhusbandry:golden_beef")
              == golden,
          "Guaranteed and probabilistic premium rewards use registered items and native recipes");
      require(
          count(
                  drops,
                  cooked ? "animalhusbandry:golden_beef" : "animalhusbandry:cooked_golden_beef")
              == 0,
          "Premium food has the right cooked state, including the die-HEAD reward");
      int leather = count(drops, "minecraft:leather");
      require(
          leather >= (trait.equals("heavyweight") ? 1 : 0)
              && leather <= (trait.equals("heavyweight") ? 4 : 2),
          "Non-food trait materials keep native counts");
    } finally {
      NeoForge.EVENT_BUS.unregister(capture);
      cow.discard();
    }
  }

  private static int count(List<ItemStack> drops, String id) {
    return drops.stream()
        .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id))
        .mapToInt(ItemStack::getCount)
        .sum();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
