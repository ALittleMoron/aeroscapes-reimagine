package dev.aeroscapes.regression;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.herobrot.tieredneo.reforge.ReforgeMenu;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.combat.RangedReforges;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.IPresetSpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.item.weapons.AutoloaderCrossbow;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;

final class ReforgeRegression {
  private ReforgeRegression() {}

  static void verify(ServerLevel level) {
    var pickaxe = new ItemStack(Items.IRON_PICKAXE);
    var silkTouch =
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH);
    pickaxe.enchant(silkTouch, 1);
    pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Cliff mine"));
    var legacy = profile("pick_legendary_tunnel");
    var stoneEdge = profile("tool_fine_with_stone_edge");
    ModifierUtils.applyTierToStack(pickaxe, legacy, TieredNeoAPI.getTierAttribute(legacy));

    var base = TieredNeoAPI.getTierAttribute(profile("tool_fine"));
    var variant = TieredNeoAPI.getTierAttribute(stoneEdge);
    require(variant != null && variant.isValid(Items.IRON_PICKAXE), "Stone-edge pick is valid");
    require(!variant.isValid(Items.IRON_SHOVEL), "Stone edge does not leak to shovels");
    require(
        variant.getAttributes().stream()
            .anyMatch(
                attribute ->
                    attribute.getAttributeTypeID().equals("tieredneo:generic.dig_speed")
                        && attribute.getEntityAttributeModifier().amount()
                            == base.getAttributes()
                                .getFirst()
                                .getEntityAttributeModifier()
                                .amount()),
        "Good stone-edge pick keeps the established mining speed");

    for (Item item :
        List.of(
            Items.IRON_PICKAXE,
            Items.IRON_SHOVEL,
            Items.IRON_AXE,
            Items.IRON_HOE,
            Items.SHEARS,
            Items.FISHING_ROD,
            Items.IRON_SWORD,
            Items.BOW,
            Items.CROSSBOW,
            registered("irons_spellbooks:autoloader_crossbow"),
            Items.TRIDENT,
            Items.MACE,
            Items.SHIELD,
            registered("farmersdelight:iron_knife"),
            registered("hybrid_aquatic:coral_pickaxe"),
            registered("supplementaries:slingshot"),
            registered("tide:starlight_bow"))) {
      verifyPool(item, 2);
    }
    for (String weapon :
        List.of(
            "irons_spellbooks:magehunter",
            "irons_spellbooks:spellbreaker",
            "irons_spellbooks:keeper_flamberge",
            "irons_spellbooks:legionnaire_flamberge",
            "irons_spellbooks:decrepit_scythe",
            "irons_spellbooks:hellrazor",
            "irons_spellbooks:boreal_blade",
            "irons_spellbooks:twilight_gale",
            "irons_spellbooks:misery",
            "create:cardboard_sword",
            "create:potato_cannon",
            "wetland_whimsy:dagger",
            "tide:sailfish",
            "tide:swordfish",
            "tide:blazing_swordfish",
            "quark:pickarang",
            "quark:flamerang",
            "immersive_weathering:ice_sickle",
            "farmersdelight:skillet",
            "spawn:coastal_crab_claw")) {
      var item = registered(weapon);
      verifyPool(item, 1);
      require(!TieredNeoAPI.getReforgeBaseItems(item).isEmpty(), weapon + " has a mapped material");
    }
    for (String gear :
        List.of(
            "minecraft:iron_helmet",
            "minecraft:iron_chestplate",
            "minecraft:iron_leggings",
            "minecraft:iron_boots",
            "hybrid_aquatic:diving_helmet",
            "hybrid_aquatic:diving_suit",
            "hybrid_aquatic:diving_leggings",
            "hybrid_aquatic:diving_boots",
            "irons_spellbooks:wandering_magician_chestplate",
            "irons_spellbooks:graybeard_staff",
            "irons_spellbooks:iron_spell_book",
            "irons_spellbooks:mana_ring",
            "yo_hooks:copper_grappling_hook",
            "paraglider:paraglider",
            "minecraft:brush",
            "betterarcheology:iron_brush",
            "naturalist:capture_net",
            "hapi:creature_net",
            "spawn:casting_net",
            "legendarysurvivaloverhaul:canteen",
            "legendarysurvivaloverhaul:medkit",
            "quark:seed_pouch",
            "create:extendo_grip",
            "create_aeronautics_throwable_rope_connector:rope_connector_launcher",
            "minecraft:spyglass",
            "exposure:camera",
            "crittersandcompanions:pearl_necklace_1",
            "minecraft:wolf_armor")) {
      verifyPool(registered(gear), 1);
    }
    require(
        ReforgeProfiles.paidGrades(registered("irons_spellbooks:claymore")).isEmpty(),
        "Test claymore has no paid pool");

    var pickPool =
        ReforgeProfiles.paidGrades(Items.IRON_PICKAXE).stream()
            .flatMap(grade -> grade.outcomes().stream())
            .map(ReforgeProfiles.Outcome::id)
            .toList();
    require(
        pickPool.containsAll(List.of(legacy, stoneEdge, profile("pick_legendary_collector"))),
        "Both established and new picks appear in the paid pool");
    ModifierUtils.removeItemStackAttribute(pickaxe);
    ModifierUtils.setItemStackAttribute(null, pickaxe, true);
    require(
        pickPool.contains(TieredNeoAPI.getTierId(pickaxe)), "Rerolled pick has a listed profile");
    require(pickaxe.getEnchantmentLevel(silkTouch) == 1, "Reroll keeps Silk Touch");
    require(
        pickaxe.getHoverName().getString().equals("Cliff mine"), "Reroll keeps the custom name");
    require(!pickaxe.isDamaged(), "A clean item stays clean after the reroll");

    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("48c83873-855c-48f6-a450-a6ead17eef2d"),
            "ReforgeCheck",
            Vec3.atBottomCenterOf(level.getSharedSpawnPos()),
            null)) {
      var player = session.player;
      var basePickaxe = ranked(Items.IRON_PICKAXE, "tool_exceptional");
      var orePickaxe = ranked(Items.IRON_PICKAXE, "tool_exceptional_with_ore_edge");
      require(
          breakSpeed(player, orePickaxe, Blocks.IRON_ORE)
              > breakSpeed(player, basePickaxe, Blocks.IRON_ORE),
          "Ore profile adds mining speed through the gameplay event");
      require(
          breakSpeed(player, orePickaxe, Blocks.STONE)
              == breakSpeed(player, basePickaxe, Blocks.STONE),
          "Ore profile keeps the base speed when mining stone");

      var ordinaryBow = ranked(Items.BOW, "ranged_ordinary");
      var fineBow = ranked(Items.BOW, "ranged_fine_with_bow_tempo");
      var poorBow = ranked(Items.BOW, "ranged_poor_with_bow_drag");
      int normalCharge = looseArrow(player, ordinaryBow);
      require(looseArrow(player, fineBow) > normalCharge, "Fine bow charges faster");
      require(looseArrow(player, poorBow) < normalCharge, "Poor bow charges slower");

      verifyCollector(player);
      verifyAutoloader(player);
      verifyWeaponSpell(player, "spellbreaker", SpellRegistry.COUNTERSPELL_SPELL.get(), 1);
      verifyWeaponSpell(player, "hellrazor", SpellRegistry.RAISE_HELL_SPELL.get(), 3);
      verifyWeaponSpell(player, "boreal_blade", SpellRegistry.FROSTBITE_SPELL.get(), 3);
      verifyWeaponSpell(player, "twilight_gale", SpellRegistry.VOLT_STRIKE_SPELL.get(), 5);
      verifyWeaponSpell(player, "misery", SpellRegistry.WITHER_SKULL_SPELL.get(), 8);
      var claw = registered("spawn:coastal_crab_claw").getDefaultInstance();
      var charge = new CompoundTag();
      charge.putInt("ShotsLeft", 8);
      charge.putBoolean("Charging", true);
      claw.set(DataComponents.CUSTOM_DATA, CustomData.of(charge));
      paidReforge(player, claw);
      require(
          CustomData.of(charge).equals(claw.get(DataComponents.CUSTOM_DATA)),
          "Paid reforging preserves the claw's native reservoir and charging flag");
      verifyFieldStorage(player);
      verifyTheurgy(player);
      verifySharedDefense(player);
      verifyPaidCasting(player);
    }
  }

  private static void verifyTheurgy(ServerPlayer player) {
    var rod = registered("theurgy:divination_rod_t1");
    verifyPool(rod, 1);
    require(
        TieredNeoAPI.getReforgeBaseItems(rod).contains(Items.GLASS),
        "The glass divination rod accepts glass in the paid reforge menu");
    var ordinary = ranked(rod, "divination_ordinary");
    var slow = ranked(rod, "divination_poor_slow");
    var fast = ranked(rod, "divination_legendary_depth");
    int duration = ordinary.getUseDuration(player);
    require(
        slow.getUseDuration(player) > duration && fast.getUseDuration(player) < duration,
        "Divination rank changes the native charge time");
    var wand = registered("theurgy:mercurial_wand");
    verifyPool(wand, 1);
    require(
        TieredNeoAPI.getReforgeBaseItems(wand).contains(registered("theurgy:mercury_shard")),
        "The Mercurial Wand accepts a mercury shard in the paid reforge menu");
  }

  private static void verifyAutoloader(ServerPlayer player) {
    var item = (AutoloaderCrossbow) registered("irons_spellbooks:autoloader_crossbow");
    var normal = ranked(item, "ranged_ordinary");
    var fast = ranked(item, "ranged_fine_with_crossbow_tempo");
    var slow = ranked(item, "ranged_poor_with_crossbow_drag");
    int duration = AutoloaderCrossbow.getChargeDuration(fast, player);
    require(
        duration < AutoloaderCrossbow.getChargeDuration(normal, player)
            && AutoloaderCrossbow.getChargeDuration(slow, player)
                > AutoloaderCrossbow.getChargeDuration(normal, player)
            && AutoloaderCrossbow.getChargeDuration(normal, player)
                == 3 * CrossbowItem.getChargeDuration(new ItemStack(Items.CROSSBOW), player),
        "Reforging changes the auto charge without removing Iron's native multiplier");
    require(
        TieredNeoAPI.getReforgeBaseItems(item).contains(Items.IRON_INGOT),
        "The autoloader uses an iron ingot");
    var level = player.serverLevel();
    player.getInventory().clearContent();
    player.setItemInHand(InteractionHand.MAIN_HAND, fast);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 3));
    item.use(level, player, InteractionHand.MAIN_HAND);
    require(AutoloaderCrossbow.isLoading(fast), "Native use starts automatic loading");
    item.inventoryTick(fast, level, player, 0, true);
    player.setPose(Pose.CROUCHING);
    item.use(level, player, InteractionHand.MAIN_HAND);
    player.setPose(Pose.STANDING);
    require(
        !AutoloaderCrossbow.isLoading(fast) && player.getOffhandItem().getCount() == 3,
        "Native cancellation keeps all ammunition");
    item.use(level, player, InteractionHand.MAIN_HAND);
    item.inventoryTick(fast, level, player, 0, true);
    fast = ItemStack.parse(level.registryAccess(), fast.save(level.registryAccess())).orElseThrow();
    player.setItemInHand(InteractionHand.MAIN_HAND, fast);
    require(
        AutoloaderCrossbow.isLoading(fast)
            && AutoloaderCrossbow.getLoadingTicks(fast) == 1
            && ReforgeProfiles.has(fast, "ranged_fine_with_crossbow_tempo"),
        "Saving the ranked autoloader preserves an in-progress charge");
    for (int tick = 1; tick <= duration; tick++) item.inventoryTick(fast, level, player, 0, true);
    require(
        !CrossbowItem.isCharged(fast) && player.getOffhandItem().getCount() == 3,
        "The autoloader does not consume ammunition before the native completion tick");
    item.inventoryTick(fast, level, player, 0, true);
    require(
        CrossbowItem.isCharged(fast) && player.getOffhandItem().getCount() == 2,
        "The autoloader loads exactly one arrow");
    var charged = fast.get(DataComponents.CHARGED_PROJECTILES);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    paidReforge(player, fast);
    require(
        charged != null && charged.equals(fast.get(DataComponents.CHARGED_PROJECTILES)),
        "Paid reforging preserves the loaded projectile component");
    ModifierUtils.removeItemStackAttribute(fast);
    ModifierUtils.applyTierToStack(
        fast,
        profile("crossbow_epic_with_trajectory"),
        TieredNeoAPI.getTierAttribute(profile("crossbow_epic_with_trajectory")));
    player.setItemInHand(InteractionHand.MAIN_HAND, fast);
    item.use(level, player, InteractionHand.MAIN_HAND);
    var arrows =
        level.getEntitiesOfClass(
            AbstractArrow.class,
            player.getBoundingBox().inflate(4),
            arrow -> arrow.getOwner() == player);
    try {
      require(
          arrows.size() == 1 && RangedReforges.handled(arrows.getFirst()),
          "Native auto shot spawns one arrow with the reforge launch data");
      require(
          !CrossbowItem.isCharged(fast)
              && AutoloaderCrossbow.isLoading(fast)
              && player.getOffhandItem().getCount() == 2,
          "The shot starts the next load and does not spend its ammunition early");
    } finally {
      arrows.forEach(AbstractArrow::discard);
    }
  }

  private static void verifyWeaponSpell(
      ServerPlayer player, String weapon, AbstractSpell spell, int level) {
    var item = registered("irons_spellbooks:" + weapon);
    var stack = item.getDefaultInstance();
    ((IPresetSpellContainer) item).initializeSpellContainer(stack);
    paidReforge(player, stack);
    var builtIn = ISpellContainer.getOrCreate(stack).getSpellAtIndex(0);
    require(
        builtIn.getSpell() == spell && builtIn.getLevel() == level && builtIn.isLocked(),
        weapon + " keeps its locked native spell and level after paid reforging");
  }

  private static void paidReforge(ServerPlayer player, ItemStack stack) {
    var material = TieredNeoAPI.getReforgeBaseItems(stack.getItem());
    require(!material.isEmpty(), "Fixture has an explicit reforge material: " + stack.getItem());
    var menu = new ReforgeMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
    require(menu.getSlot(1).getMaxStackSize() == 1, "One piece of equipment per paid attempt");
    menu.getSlot(0).set(new ItemStack(material.getFirst(), 2));
    menu.getSlot(1).set(stack);
    menu.getSlot(2).set(new ItemStack(Items.LAPIS_LAZULI, 2));
    menu.reforge();
    var actual = TieredNeoAPI.getTierId(stack);
    require(
        ReforgeProfiles.paidGrades(stack.getItem()).stream()
            .flatMap(grade -> grade.outcomes().stream())
            .anyMatch(outcome -> outcome.id().equals(actual)),
        "Paid roll belongs to the EMI outcome pool");
    require(
        menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
        "Reforge spends exactly one material and one catalyst");
  }

  private static void verifyFieldStorage(ServerPlayer player) {
    var canteen = registered("legendarysurvivaloverhaul:canteen").getDefaultInstance();
    ThirstUtil.setCapacityTag(canteen, 2);
    ThirstUtil.setHydrationEnumTag(canteen, HydrationEnum.PURIFIED);
    paidReforge(player, canteen);
    require(
        ThirstUtil.getCapacityTag(canteen) == 2
            && ThirstUtil.getHydrationEnumTag(canteen) == HydrationEnum.PURIFIED,
        "Paid reforging preserves water quantity and purity");
    var item = registered("quark:seed_pouch");
    var pouch = ranked(item, "seed_pouch_legendary_row");
    var seeds = new SimpleContainer(new ItemStack(Items.WHEAT_SEEDS, 8));
    require(
        item.overrideStackedOnOther(pouch, new Slot(seeds, 0, 0, 0), ClickAction.SECONDARY, player)
            && seeds.isEmpty(),
        "Quark loads the actual seeds into the pouch");
    var storedItem =
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("quark:stored_item"));
    require(storedItem != null && pouch.has(storedItem), "The pouch contains a native seed type");
    var seed = pouch.get(storedItem);
    paidReforge(player, pouch);
    require(
        Integer.valueOf(8).equals(seedCount(pouch)) && seed.equals(pouch.get(storedItem)),
        "Paid reforging preserves the stored seeds");
    var tier = profile("seed_pouch_legendary_row");
    ModifierUtils.removeItemStackAttribute(pouch);
    ModifierUtils.applyTierToStack(pouch, tier, TieredNeoAPI.getTierAttribute(tier));
    verifySeedRow(player, pouch);
  }

  private static Object seedCount(ItemStack pouch) {
    var count =
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("quark:item_count"));
    return count == null ? null : pouch.get(count);
  }

  private static void verifySeedRow(ServerPlayer player, ItemStack pouch) {
    var level = player.serverLevel();
    Direction forward = player.getDirection();
    BlockPos start = player.blockPosition().relative(forward);
    int surface = Integer.MIN_VALUE;
    for (int step = 0; step < 3; step++) {
      BlockPos column = start.relative(forward, step);
      surface =
          Math.max(
              surface,
              level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()));
    }
    BlockPos origin = new BlockPos(start.getX(), surface, start.getZ());
    Vec3 previousPosition = player.position();
    var positions = List.of(origin, origin.relative(forward), origin.relative(forward, 2));
    List<BlockState> soil = positions.stream().map(level::getBlockState).toList();
    List<BlockState> top = positions.stream().map(pos -> level.getBlockState(pos.above())).toList();
    try {
      player.setPos(Vec3.atBottomCenterOf(origin.relative(forward.getOpposite()).above()));
      for (BlockPos pos : positions) {
        level.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
      }
      var obstacle = positions.getLast().above();
      level.setBlockAndUpdate(obstacle, Blocks.STONE.defaultBlockState());
      require(
          Blocks.WHEAT.defaultBlockState().canSurvive(level, positions.get(1).above()),
          "The next crop has native growing conditions before testing the obstacle");
      player.setItemInHand(InteractionHand.MAIN_HAND, pouch);
      var hit =
          new BlockHitResult(Vec3.atCenterOf(origin).add(0, 0.5, 0), Direction.UP, origin, false);
      require(
          pouch.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))
              == InteractionResult.CONSUME,
          "Seed pouch places through Quark's native use");
      require(
          Integer.valueOf(6).equals(seedCount(pouch))
              && level.getBlockState(origin.above()).is(Blocks.WHEAT)
              && level.getBlockState(positions.get(1).above()).is(Blocks.WHEAT)
              && level.getBlockState(obstacle).is(Blocks.STONE),
          "A legendary row spends one stored seed per crop and stops at the obstacle: count="
              + seedCount(pouch)
              + " crops="
              + positions.stream().map(pos -> level.getBlockState(pos.above())).toList());
    } finally {
      player.setPos(previousPosition);
      for (int index = 0; index < positions.size(); index++) {
        level.setBlockAndUpdate(positions.get(index), soil.get(index));
        level.setBlockAndUpdate(positions.get(index).above(), top.get(index));
      }
    }
  }

  private static void verifySharedDefense(ServerPlayer player) {
    player.setItemSlot(
        EquipmentSlot.HEAD,
        ranked(Items.IRON_HELMET, "armor_head_legendary_air_with_second_impact"));
    player.setItemSlot(
        EquipmentSlot.CHEST,
        ranked(
            registered("irons_spellbooks:wandering_magician_chestplate"),
            "magic_armor_chest_legendary_ward_with_return_ward"));
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    var arrow = new Arrow(EntityType.ARROW, player.serverLevel());
    var arrowHit = new DamageContainer(player.damageSources().arrow(arrow, null), 8.0F);
    var spellHit = new DamageContainer(player.damageSources().magic(), 8.0F);
    NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(player, arrowHit));
    NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(player, spellHit));
    var reduced = NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(player, arrowHit));
    var blocked = NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(player, spellHit));
    require(
        reduced.getNewDamage() == 6.0F && blocked.getNewDamage() == 8.0F,
        "Physical and magic defensive responses share one activation budget");
    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
  }

  private static void verifyPaidCasting(ServerPlayer player) {
    var level = player.serverLevel();
    var spell = SpellRegistry.MAGIC_MISSILE_SPELL.get();
    var regeneration = player.getAttribute(AttributeRegistry.MANA_REGEN);
    require(regeneration != null, "The fixture has Iron's mana regeneration attribute");
    regeneration.setBaseValue(0.0);
    require(
        player.getAttributeValue(AttributeRegistry.MANA_REGEN) == 0.0,
        "Cast-cost measurement excludes passive regeneration");
    var staff =
        ranked(
            registered("irons_spellbooks:graybeard_staff"),
            "staff_epic_combat_with_combat_economy");
    player.setItemInHand(InteractionHand.MAIN_HAND, staff);
    var book = ranked(registered("irons_spellbooks:iron_spell_book"), "spellbook_legendary_refund");
    var spells = ISpellContainer.create(1, false, true).mutableCopy();
    require(spells.addSpell(spell, 4, false), "Fixture book accepts the spell");
    ISpellContainer.set(book, spells.toImmutable());
    var magic = MagicData.getPlayerMagicData(player);
    magic.setMana(100.0F);
    player.setItemInHand(InteractionHand.OFF_HAND, book);
    require(
        spell.attemptInitiateCast(book, 4, level, player, CastSource.SPELLBOOK, true, "offhand"),
        "Native spellbook combat cast starts");
    ((MagicManager) MagicHelper.MAGIC_MANAGER).tick(level);
    double expected = 100.0F - Math.ceil(spell.getManaCost(4) * 0.92);
    require(
        magic.getMana() == expected && magic.getPlayerCooldowns().isOnCooldown(spell),
        "The staff discounts a paid spell and keeps its native cooldown: "
            + magic.getMana()
            + " expected "
            + expected);
    float paidMana = magic.getMana();
    var source = SpellDamageSource.source(player, spell);
    NeoForge.EVENT_BUS.post(new LivingDeathEvent(new Zombie(EntityType.ZOMBIE, level), source));
    require(magic.getMana() == paidMana + 8.0F, "The legendary book refunds one paid kill");
    NeoForge.EVENT_BUS.post(new LivingDeathEvent(new Zombie(EntityType.ZOMBIE, level), source));
    require(magic.getMana() == paidMana + 8.0F, "A second kill cannot repeat the refund");
    magic.getPlayerCooldowns().removeCooldown(spell.getSpellId());
    var scroll = ItemRegistry.SCROLL.get().getDefaultInstance();
    ISpellContainer.createScrollContainer(spell, 4, scroll);
    player.setItemInHand(InteractionHand.OFF_HAND, scroll);
    float before = magic.getMana();
    require(
        spell.attemptInitiateCast(scroll, 4, level, player, CastSource.SCROLL, true, "offhand"),
        "Native scroll combat cast starts");
    ((MagicManager) MagicHelper.MAGIC_MANAGER).tick(level);
    NeoForge.EVENT_BUS.post(new LivingDeathEvent(new Zombie(EntityType.ZOMBIE, level), source));
    require(magic.getMana() == before && !magic.isCasting(), "A scroll remains a free cast");
    var missiles =
        level.getEntities(
            EntityRegistry.MAGIC_MISSILE_PROJECTILE.get(),
            player.getBoundingBox().inflate(2),
            projectile -> projectile.getOwner() == player);
    require(missiles.size() == 2, "Each cast creates one native projectile");
    missiles.forEach(Entity::discard);
  }

  private static void verifyCollector(ServerPlayer player) {
    var inventory = player.getInventory();
    inventory.clearContent();
    var collector = ranked(Items.IRON_PICKAXE, "pick_legendary_collector");
    player.setItemInHand(InteractionHand.MAIN_HAND, collector);
    var collected = dropStone(player, collector);
    require(
        collected.isRemoved() && inventory.countItem(Items.COBBLESTONE) == 3,
        "Collector picks up the actual stack once");
    for (int slot = 0; slot < inventory.items.size(); slot++) {
      if (slot != inventory.selected) inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
    }
    var remainder = dropStone(player, collector);
    try {
      require(
          remainder.isAlive()
              && remainder.getItem().getCount() == 3
              && inventory.countItem(Items.COBBLESTONE) == 0,
          "Collector leaves the original drop when the inventory is full");
    } finally {
      remainder.discard();
    }
  }

  private static ItemEntity dropStone(ServerPlayer player, ItemStack tool) {
    var level = player.serverLevel();
    var pos = player.blockPosition();
    var drop =
        new ItemEntity(
            level,
            player.getX(),
            player.getY(),
            player.getZ(),
            new ItemStack(Items.COBBLESTONE, 3));
    drop.setDefaultPickUpDelay();
    CommonHooks.handleBlockDrops(
        level,
        pos,
        Blocks.STONE.defaultBlockState(),
        null,
        new ArrayList<>(List.of(drop)),
        player,
        tool);
    return drop;
  }

  private static float breakSpeed(ServerPlayer player, ItemStack stack, Block block) {
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    return NeoForge.EVENT_BUS
        .post(
            new PlayerEvent.BreakSpeed(
                player, block.defaultBlockState(), 10.0F, player.blockPosition()))
        .getNewSpeed();
  }

  private static ItemStack ranked(Item item, String path) {
    var stack = new ItemStack(item);
    var id = profile(path);
    var tier = TieredNeoAPI.getTierAttribute(id);
    require(tier != null && tier.isValid(item), "Missing fixture profile " + id);
    ModifierUtils.applyTierToStack(stack, id, tier);
    return stack;
  }

  private static int looseArrow(ServerPlayer player, ItemStack bow) {
    player.setItemInHand(InteractionHand.MAIN_HAND, bow);
    return NeoForge.EVENT_BUS
        .post(new ArrowLooseEvent(player, bow, player.serverLevel(), 20, true))
        .getCharge();
  }

  private static void verifyPool(Item item, int minimumLegendary) {
    var grades = ReforgeProfiles.paidGrades(item);
    String name = BuiltInRegistries.ITEM.getKey(item).toString();
    require(
        grades.stream()
            .map(ReforgeProfiles.PaidGrade::weight)
            .toList()
            .equals(List.of(12, 47, 22, 11, 5, 3)),
        name + " preserves all six paid grade chances");
    var paid = new HashSet<ResourceLocation>();
    var legendary = new HashSet<ResourceLocation>();
    for (var grade : grades) {
      for (var outcome : grade.outcomes()) {
        require(outcome.profile().isValid(item) && paid.add(outcome.id()), name + " paid profile");
        if (grade.key().equals("legendary")) legendary.add(outcome.id());
      }
    }
    require(legendary.size() >= minimumLegendary, name + " offers its legendary outcomes");
    for (int attempt = 0; attempt < 96; attempt++) {
      require(
          paid.contains(ModifierUtils.getRandomAttributeIDFor(null, item, true)),
          name + " actual paid rolls stay in the displayed pool");
      var natural = ModifierUtils.getRandomAttributeIDFor(null, item, false);
      require(paid.contains(natural) && !legendary.contains(natural), name + " natural pool");
    }
  }

  private static Item registered(String id) {
    var key = ResourceLocation.parse(id);
    require(BuiltInRegistries.ITEM.containsKey(key), "Missing " + id);
    return BuiltInRegistries.ITEM.get(key);
  }

  private static ResourceLocation profile(String path) {
    return ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
