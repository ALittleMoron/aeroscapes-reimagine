package dev.aeroscapes.regression;

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;

final class HexQualityRegression {
  private HexQualityRegression() {}

  static void verify(ServerLevel level) {
    var staves =
        BuiltInRegistries.ITEM.keySet().stream()
            .filter(
                id -> id.getNamespace().equals("hexcasting") && id.getPath().startsWith("staff/"))
            .toList();
    require(staves.size() == 14, "Complete Hex staff inventory");
    for (var id : staves)
      catalogue(BuiltInRegistries.ITEM.get(id), "hex_staff", item("charged_amethyst"));
    for (String id : List.of("cypher", "trinket", "artifact"))
      catalogue(item(id), "hex_carrier", item("charged_amethyst"));
    catalogue(item("jeweler_hammer"), "hex_hammer", Items.IRON_INGOT);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "HexQuality",
            Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0),
            null)) {
      session.player.setGameMode(GameType.SURVIVAL);
      staff(session);
      carrier(session.player);
      hammer(session.player);
    }
    LogUtils.getLogger().info("Hex equipment quality regression passed");
  }

  private static void catalogue(Item item, String family, Item material) {
    var grades = ReforgeProfiles.paidGrades(item);
    require(
        grades.size() == 6
            && grades.stream()
                .allMatch(
                    grade ->
                        grade.outcomes().size() == 1
                            && grade
                                .outcomes()
                                .getFirst()
                                .id()
                                .getPath()
                                .equals(family + "_" + grade.key())),
        "Six exclusive equipment outcomes for " + item);
    require(
        TieredNeoAPI.getReforgeBaseItems(item).equals(List.of(material)),
        "Reforge material for " + item);
  }

  private static void staff(TestShipPlayerSession session) {
    var player = session.player;
    var hand = InteractionHand.MAIN_HAND;
    var stack = ranked("staff/oak", "hex_staff_legendary");
    player.setItemInHand(hand, stack);
    var vm = CastingVM.empty(new StaffCastEnv(player, hand));
    vm.queueExecuteAndWrapIota(
        new PatternIota(HexActions.GET_CASTER.prototype()), player.serverLevel());
    require(session.takeHexReport() == null, "A successful stroke gives no quality hint");
    vm.queueExecuteAndWrapIota(
        new PatternIota(HexActions.CONJURE_LIGHT.prototype()), player.serverLevel());
    var report = session.takeHexReport();
    require(
        report != null
            && report.getContents() instanceof TranslatableContents text
            && text.getKey().endsWith(".position")
            && text.getArgs()[0].equals(2)
            && report.getSiblings().stream()
                .anyMatch(
                    line ->
                        line.getContents() instanceof TranslatableContents previous
                            && previous.getKey().endsWith(".last_at")
                            && previous.getArgs()[0].equals(1)),
        "Native mishap identifies failed step and earlier successful stroke");
    IXplatAbstractions.INSTANCE.clearCastingData(player);
    CastingVM.empty(new StaffCastEnv(player, hand))
        .queueExecuteAndWrapIota(
            new PatternIota(HexActions.CONJURE_LIGHT.prototype()), player.serverLevel());
    var first = session.takeHexReport();
    require(
        first != null
            && first.getContents() instanceof TranslatableContents text
            && text.getArgs()[0].equals(1)
            && first.getSiblings().stream()
                .anyMatch(
                    line ->
                        line.getContents() instanceof TranslatableContents previous
                            && previous.getKey().endsWith(".no_previous")),
        "Clearing the drawing clears its private execution history");
  }

  private static void carrier(ServerPlayer player) {
    var stack = ranked("trinket", "hex_carrier_legendary");
    var item = (ItemPackagedHex) stack.getItem();
    item.writeHex(
        stack, List.of(new PatternIota(HexActions.CONJURE_LIGHT.prototype())), null, 12345);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    item.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
    var lines = new ArrayList<Component>();
    item.appendHoverText(stack, Item.TooltipContext.of(player.level()), lines, TooltipFlag.NORMAL);
    require(
        lines.stream()
            .anyMatch(
                line ->
                    line.getContents() instanceof TranslatableContents text
                        && text.getKey().endsWith(".units")
                        && text.getArgs()[2].toString().equals("12345")),
        "Exact native charge survives a malformed spell");
    require(
        lines.stream()
            .anyMatch(
                line ->
                    line.getContents() instanceof TranslatableContents text
                        && text.getKey().endsWith(".mishap")
                        && text.getArgs()[0] instanceof Component),
        "The native localizable reason is remembered on the real carrier");
    require(
        player.getCooldowns().isOnCooldown(item),
        "Native item cooldown survives mishap diagnostics");
  }

  private static void hammer(ServerPlayer player) {
    var stack = ranked("jeweler_hammer", "hex_hammer_legendary");
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    player.getRandom().setSeed(680027L);
    for (int i = 0; i < 40; i++)
      stack.mineBlock(
          player.level(),
          Blocks.AMETHYST_CLUSTER.defaultBlockState(),
          player.blockPosition(),
          player);
    require(
        stack.getDamageValue() > 0 && stack.getDamageValue() < 40,
        "Ordinary mining durability can be saved while jeweling");
    var drop = collect(player, stack, Blocks.AMETHYST_CLUSTER);
    require(
        drop.isRemoved() && player.getInventory().countItem(Items.AMETHYST_SHARD) == 3,
        "Real cluster loot is picked up exactly once");
    var stone = collect(player, stack, Blocks.STONE);
    try {
      require(
          stone.isAlive()
              && stone.getItem().getCount() == 3
              && player.getInventory().countItem(Items.AMETHYST_SHARD) == 3,
          "Full blocks do not qualify");
    } finally {
      stone.discard();
    }
  }

  private static ItemEntity collect(ServerPlayer player, ItemStack tool, Block block) {
    var drop =
        new ItemEntity(
            player.level(),
            player.getX(),
            player.getY(),
            player.getZ(),
            new ItemStack(Items.AMETHYST_SHARD, 3));
    drop.setDefaultPickUpDelay();
    CommonHooks.handleBlockDrops(
        player.serverLevel(),
        player.blockPosition(),
        block.defaultBlockState(),
        null,
        new ArrayList<>(List.of(drop)),
        player,
        tool);
    return drop;
  }

  private static Item item(String path) {
    return BuiltInRegistries.ITEM
        .getOptional(ResourceLocation.fromNamespaceAndPath("hexcasting", path))
        .orElseThrow();
  }

  private static ItemStack ranked(String itemPath, String profile) {
    var stack = new ItemStack(item(itemPath));
    var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", profile);
    ModifierUtils.applyTierToStack(stack, id, TieredNeoAPI.getTierAttribute(id));
    return stack;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
