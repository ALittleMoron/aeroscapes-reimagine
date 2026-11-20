package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import com.cyberday1.neoorigins.service.CreatorAccess;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.camera.ClimbableCameraFade;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import dev.aeroscapes.races.RaceTraits;
import harmonised.pmmo.api.APIUtils;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.api.stamina.Stamina;
import tictim.paraglider.api.vessel.VesselContainer;
import tictim.paraglider.contents.Contents;
import tictim.paraglider.impl.movement.PlayerMovement;

final class RaceFoundationRegression {
  private static final List<String> ORBS =
      List.of("orb_of_origin", "orb_of_class", "gold_orb", "pink_orb", "purple_orb", "teal_orb");

  private RaceFoundationRegression() {}

  static void verify(ServerLevel level) {
    var layer = LayerDataManager.INSTANCE.getLayer(ResourceLocation.parse("neoorigins:origin"));
    require(
        layer != null && layer.getAvailableOriginIds().isEmpty(),
        "No separate native picker entries");
    require(
        LayerDataManager.INSTANCE.getSortedLayers().size() == 1, "Only the race layer is active");
    require(
        OriginDataManager.INSTANCE.getOrigins().size() == Race.values().length,
        "Only pack races load");
    for (String orb : ORBS) {
      require(
          level
              .getRecipeManager()
              .byKey(ResourceLocation.fromNamespaceAndPath("neoorigins", orb))
              .isEmpty(),
          "Survival orb recipe stayed active: " + orb);
    }
    var start = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 16);
    verifyCreationProtection(level, start);
    var exposures = new EnumMap<Race, Float>(Race.class);
    for (Race race : Race.values()) exposures.put(race, verifyRace(level, start, race));
    close(
        exposures.get(Race.DWARF) - exposures.get(Race.HUMAN),
        1,
        "Dwarf cold tolerance on identical route");
    LogUtils.getLogger().info("Race foundation regression passed");
  }

  private static void verifyCreationProtection(ServerLevel level, BlockPos start) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "Chooser", Vec3.atBottomCenterOf(start), null)) {
      var player = session.player;
      PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
      var voidDamage = player.damageSources().fellOutOfWorld();
      player.addEffect(new MobEffectInstance(MobEffects.LUCK, 100));
      require(!player.hurt(voidDamage, 1000) && player.isAlive(), "Void damage during creation");
      require(
          player.isInvulnerableTo(
              player
                  .damageSources()
                  .mobAttack(new net.minecraft.world.entity.monster.Zombie(level))),
          "Mob damage during creation");
      require(
          !player.isInvulnerableTo(player.damageSources().genericKill()), "Admin command blocked");
      // Let vanilla's independent 60-tick respawn grace period expire.
      for (int tick = 0; tick < 65; tick++) {
        player.tick();
        player.doTick();
      }
      require(
          player.getEffect(MobEffects.LUCK).getDuration() == 100,
          "Survival time elapsed during creation");
      require(
          !RaceIdentity.choose(player, Race.HUMAN, 0, CharacterClass.MAGE, "quick_block"),
          "A starter from another class was accepted");
      require(
          RaceIdentity.race(player) == null && RaceIdentity.awaitingChoice(player),
          "Invalid class submission partially committed the race or lifted protection");
      require(
          RaceIdentity.choose(player, Race.HUMAN, 0, CharacterClass.WARRIOR, "quick_block"),
          "Could not finish protected creation");
      player.doTick();
      int remaining = player.getEffect(MobEffects.LUCK).getDuration();
      require(remaining == 99, "Survival time after confirmation: " + remaining);
      require(
          !player.isInvulnerableTo(voidDamage), "Chooser protection persisted after confirmation");
      require(
          player.hurt(player.damageSources().generic(), 2),
          "Live damage was rejected after confirmation");
    }
  }

  private static float verifyRace(ServerLevel level, BlockPos start, Race race) {
    var position = Vec3.atBottomCenterOf(start);
    var uuid = UUID.randomUUID();
    var name = race.id() + "Fixture";
    CompoundTag saved;
    float exposure;
    try (var session = TestShipPlayerSession.create(level, uuid, name, position, null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        var inventory = player.getInventory().save(new ListTag());
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        if (race == Race.HUMAN) AccessoryGuidesRegression.verify(player);
        require(!CreatorAccess.canUse(player), "Survival fixture has native editor privileges");
        require(
            player.getData(OriginAttachments.originData()).isHadAllOrigins(),
            "Native picker protection released");
        require(
            RaceIdentity.race(player) == null,
            "Existing or new unselected player is offered a choice");
        require(NeoOriginsAPI.powers(player).isEmpty(), "Power granted before choosing a race");
        if (race == Race.HUMAN) verifyOrbs(player);
        require(
            !RaceIdentity.choose(
                player, race, race.styleCount(), CharacterClass.WARRIOR, "quick_block"),
            "Rejected out-of-range appearance");
        ((PlayerMovement) Movement.get(player)).update();
        var originalSlot = player.getInventory().selected;
        var newSlot = (originalSlot + 1) % 9;
        var selectSlot =
            new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(newSlot);
        player.connection.handleSetCarriedItem(selectSlot);
        require(
            player.getInventory().selected == originalSlot, "World input before race confirmation");
        require(
            RaceIdentity.choose(player, race, 2, CharacterClass.WARRIOR, "quick_block"),
            "Could not choose " + race);
        player.connection.handleSetCarriedItem(selectSlot);
        require(player.getInventory().selected == newSlot, "World input blocked after choosing");
        player.connection.handleSetCarriedItem(
            new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(originalSlot));
        require(
            !RaceIdentity.choose(player, Race.HUMAN, 0, CharacterClass.WARRIOR, "quick_block"),
            "Repeat confirmation changed race or appearance");
        if (race == Race.HUMAN) verifyOrbs(player);
        verifyGrantedPowers(player, race);
        require(
            inventory.equals(player.getInventory().save(new ListTag())),
            "Race login or selection changed starter inventory for " + race);
        verifyBody(player, start, race);
        verifyClimbingVisibility(player, start);
        verifyPractice(player, race);
        exposure = verifyCapacityAndCold(player, start, race);
        AbilitiesRegression.verifyDeathClone(player, copy -> checkIdentity(copy, race));
        saved = player.saveWithoutId(new CompoundTag());
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session = TestShipPlayerSession.create(level, uuid, name, position, saved)) {
      checkIdentity(session.player, race);
      PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(session.player));
      verifyBody(session.player, start, race);
    }
    return exposure;
  }

  private static void checkIdentity(ServerPlayer player, Race race) {
    require(
        RaceIdentity.race(player) == race
            && RaceIdentity.style(player) == 2
            && RaceIdentity.characterClass(player) == CharacterClass.WARRIOR
            && RaceIdentity.starter(player).equals("quick_block"),
        "Saved identity changed for " + race);
  }

  private static void verifyOrbs(ServerPlayer player) {
    var data = player.getData(OriginAttachments.originData());
    var prior = RaceIdentity.race(player);
    var held = player.getOffhandItem();
    var hidden =
        TagKey.create(Registries.ITEM, ResourceLocation.parse("c:hidden_from_recipe_viewers"));
    try {
      for (var orb : ORBS) {
        var item =
            BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("neoorigins", orb))
                .orElseThrow();
        var stack = new ItemStack(item);
        require(stack.is(hidden), "Native orb visible in recipe viewers: " + orb);
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        require(
            player.gameMode.useItem(player, player.level(), stack, InteractionHand.OFF_HAND)
                == InteractionResult.FAIL,
            "External orb opened native selection: " + orb);
        require(
            RaceIdentity.race(player) == prior
                && !data.isPendingOrbCommit()
                && !data.isPendingLayerPickerCommit()
                && player.getOffhandItem().is(item)
                && player.getOffhandItem().getCount() == 1,
            "External orb mutated race, inventory or selection state: " + orb);
      }
    } finally {
      player.setItemInHand(InteractionHand.OFF_HAND, held);
    }
  }

  private static void verifyGrantedPowers(ServerPlayer player, Race race) {
    Set<String> expected =
        switch (race) {
          case HUMAN -> Set.of();
          case DWARF ->
              Set.of(
                  "dwarf_health", "dwarf_stamina", "dwarf_speed", "dwarf_stability", "dwarf_cold");
          case GOBLIN -> Set.of("goblin_health", "goblin_stamina", "goblin_sneaking");
          case ELF -> Set.of("elf_health", "elf_stamina", "elf_speed");
        };
    var ids = NeoOriginsAPI.powers(player).stream().map(power -> power.id()).toList();
    require(
        ids.size() == expected.size()
            && ids.stream().allMatch(id -> id.getNamespace().equals("aeroscapes_reimagine"))
            && ids.stream()
                .map(ResourceLocation::getPath)
                .collect(Collectors.toSet())
                .equals(expected),
        "Unexpected native grants for " + race + ": " + ids);
    var orig = player.getData(OriginAttachments.originData());
    var layer = LayerDataManager.INSTANCE.getLayer(ResourceLocation.parse("neoorigins:origin"));
    require(
        orig.getOrigins().size() == 1 && layer.getAvailableOriginIds(orig.getOrigins()).isEmpty(),
        "Native picker unlocked extra origins after choosing " + race);
  }

  private static void verifyBody(ServerPlayer player, BlockPos start, Race race) {
    var level = player.serverLevel();
    var roof = start.above();
    var savedRoof = level.getBlockState(roof);
    var floor = start.below();
    var savedFloor = level.getBlockState(floor);
    try {
      level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
      player.setPos(Vec3.atBottomCenterOf(start));
      player.setPose(Pose.STANDING);
      player.refreshDimensions();
      close(player.getBbWidth(), 0.6 * RaceTraits.width(race), race + " width");
      close(player.getBbHeight(), 1.8 * RaceTraits.height(race), race + " height");
      close(player.getEyeHeight(), 1.62 * RaceTraits.height(race), race + " eye height");
      close(player.blockInteractionRange(), 4.5, race + " survival reach");
      level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
      require(
          !level.noCollision(player, player.getBoundingBox()),
          "Standing body fits low hatch: " + race);
      player.setPose(Pose.CROUCHING);
      require(
          level.noCollision(player, player.getBoundingBox().deflate(1.0e-7))
              == (race == Race.GOBLIN),
          "Crouched clearance for " + race);
      level.setBlockAndUpdate(roof, savedRoof);
      player.setPose(Pose.STANDING);
      player.setDeltaMovement(Vec3.ZERO);
      player.setOnGround(true);
      player.jumpFromGround();
      // ServerPlayer normally receives motion from its client. Resolve the same native travel here.
      for (int i = 0; i < 7; i++) player.travel(Vec3.ZERO);
      require(
          player.getY() > start.getY() + 1,
          "Cannot jump a block: " + race + " at " + player.getY());
    } finally {
      level.setBlockAndUpdate(roof, savedRoof);
      level.setBlockAndUpdate(floor, savedFloor);
      player.setPos(Vec3.atBottomCenterOf(start));
      player.setDeltaMovement(Vec3.ZERO);
    }
  }

  private static void verifyClimbingVisibility(ServerPlayer player, BlockPos start) {
    var level = player.serverLevel();
    var rope =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse("supplementaries:rope"))
            .orElseThrow()
            .defaultBlockState()
            .setValue(BlockStateProperties.UP, true);
    var original = level.getBlockState(start);
    var position = Vec3.atBottomCenterOf(start);
    int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    try {
      player.setPos(Vec3.atBottomCenterOf(start.east()));
      level.setBlock(start, rope.setValue(BlockStateProperties.DOWN, false), flags);
      require(
          !player.onClimbable() && !ClimbableCameraFade.shouldFade(true, player),
          "Player faded standing beside the rope end");
      for (var climbable :
          List.of(
              rope.setValue(BlockStateProperties.DOWN, true),
              Blocks.LADDER.defaultBlockState(),
              Blocks.VINE.defaultBlockState())) {
        level.setBlock(start, climbable, flags);
        player.setPos(position);
        player.setDeltaMovement(0, 0.15, 0);
        player.travel(Vec3.ZERO);
        require(
            player.getY() > start.getY()
                && player.onClimbable()
                && !ClimbableCameraFade.shouldFade(true, player),
            "Player faded ascending " + climbable);
      }
      player.setPos(Vec3.atBottomCenterOf(start.east()));
      level.setBlock(start, Blocks.STONE.defaultBlockState(), flags);
      require(
          ClimbableCameraFade.shouldFade(true, player)
              && !ClimbableCameraFade.shouldFade(false, player),
          "Ordinary camera obstruction fade changed");
    } finally {
      level.setBlock(start, original, flags);
      player.setPos(position);
      player.setDeltaMovement(Vec3.ZERO);
    }
  }

  private static void verifyPractice(ServerPlayer player, Race race) {
    long magic = MagicPractice.credit(player);
    long archery = APIUtils.getXp("archery", player);
    for (int i = 0; i < 5; i++) {
      MagicPractice.award(player, 1);
      APIUtils.addXp("archery", player, 1);
    }
    int expected = race == Race.ELF ? 6 : 5;
    require(MagicPractice.credit(player) == magic + expected, race + " magic practice");
    require(APIUtils.getXp("archery", player) == archery + expected, race + " archery practice");
    APIUtils.addXp("mining", player, 5);
    require(APIUtils.getXp("mining", player) == 5, "Off-branch XP changed for " + race);
  }

  private static float verifyCapacityAndCold(ServerPlayer player, BlockPos start, Race race) {
    int health =
        switch (race) {
          case HUMAN -> 20;
          case DWARF -> 24;
          case GOBLIN -> 16;
          case ELF -> 18;
        };
    int stamina =
        switch (race) {
          case HUMAN -> 1000;
          case DWARF -> 1100;
          case GOBLIN -> 850;
          case ELF -> 900;
        };
    close(player.getMaxHealth(), health, race + " initial health");
    close(Stamina.get(player).maxStamina(), stamina, race + " initial stamina");
    close(
        player.getAttributeValue(Attributes.SNEAKING_SPEED),
        race == Race.GOBLIN ? 0.42 : 0.3,
        race + " crouched movement");
    close(
        player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),
        race == Race.DWARF ? 0.15 : 0,
        race + " stability");
    double speed = race == Race.DWARF ? 0.95 : race == Race.ELF ? 1.06 : 1;
    close(
        player.getAttributeValue(Attributes.MOVEMENT_SPEED),
        player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * speed,
        race + " speed");
    var vessels = VesselContainer.get(player);
    require(vessels.giveHeartContainers(40, false, false) == 20, "Native heart ceiling");
    require(vessels.giveStaminaVessels(20, false, false) == 10, "Native vessel ceiling");
    ((PlayerMovement) Movement.get(player)).update();
    close(player.getMaxHealth(), health + 40, race + " intrinsic health ceiling");
    close(Stamina.get(player).maxStamina(), stamina + 2000, race + " intrinsic stamina ceiling");

    // Comparable open cold exposure and a roofed, heated position at identical coordinates.
    var level = player.serverLevel();
    var fire = start.east();
    var ceiling = start.above(2);
    var originalFire = level.getBlockState(fire);
    var originalCeiling = level.getBlockState(ceiling);
    float bare;
    try {
      level.setBlockAndUpdate(fire, Blocks.SOUL_CAMPFIRE.defaultBlockState());
      bare = TemperatureUtil.getPlayerTargetTemperature(player);
      var tunic = new ItemStack(Items.LEATHER_CHESTPLATE);
      var bonus =
          new AttributeModifier(
              ResourceLocation.parse("aeroscapes_reimagine:test_capacity"),
              4,
              AttributeModifier.Operation.ADD_VALUE);
      tunic.set(
          DataComponents.ATTRIBUTE_MODIFIERS,
          ItemAttributeModifiers.EMPTY
              .withModifierAdded(Attributes.MAX_HEALTH, bonus, EquipmentSlotGroup.CHEST)
              .withModifierAdded(Contents.get().maxStamina(), bonus, EquipmentSlotGroup.CHEST));
      player.setItemSlot(EquipmentSlot.CHEST, tunic);
      player.doTick();
      player.setPos(Vec3.atBottomCenterOf(start));
      close(
          player.getMaxHealth(), health + 44, race + " worn health bonus beyond intrinsic ceiling");
      close(
          Stamina.get(player).maxStamina(),
          stamina + 2004,
          race + " worn stamina bonus beyond intrinsic ceiling");
      float clothed = TemperatureUtil.getPlayerTargetTemperature(player);
      close(clothed - bare, 1.5, race + " leather warmth in the cold");
      level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState());
      float sheltered = TemperatureUtil.getPlayerTargetTemperature(player);
      require(sheltered > clothed, "Heated shelter did not warm " + race);
      LogUtils.getLogger()
          .info(
              "Race thermal comparison {} open {}, leather {}, heated shelter {}",
              race,
              bare,
              clothed,
              sheltered);
    } finally {
      level.setBlockAndUpdate(fire, originalFire);
      level.setBlockAndUpdate(ceiling, originalCeiling);
      player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
      player.doTick();
    }
    close(player.getMaxHealth(), health + 40, race + " health after removing gear");
    close(Stamina.get(player).maxStamina(), stamina + 2000, race + " stamina after removing gear");
    return bare;
  }

  private static void close(double actual, double expected, String what) {
    require(Math.abs(actual - expected) < 1e-4, what + " expected " + expected + ", got " + actual);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }
}
