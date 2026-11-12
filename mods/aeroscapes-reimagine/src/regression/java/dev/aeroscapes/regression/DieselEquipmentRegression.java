package dev.aeroscapes.regression;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileEntity;
import com.jesz.createdieselgenerators.content.tools.FueledToolItem;
import com.simibubi.create.AllItems;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.engineering.DieselReforges;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;

/** Real native pulses, scanner startup and completed manual recipes with quality applied. */
public final class DieselEquipmentRegression {
  private static final List<String> GRADES =
      List.of("poor", "ordinary", "fine", "exceptional", "epic", "legendary");

  private DieselEquipmentRegression() {}

  public static void verify(ServerLevel level) {
    FakePlayer player = FakePlayerFactory.getMinecraft(level);
    var savedInventory = player.getInventory().save(new net.minecraft.nbt.ListTag());
    var position = player.position();
    float pitch = player.getXRot();
    float yaw = player.getYRot();
    try {
      player.getInventory().clearContent();
      var spawn = level.getSharedSpawnPos();
      player.setPos(
          spawn.getX() + 4,
          Math.min(spawn.getY() + 16, level.getMaxBuildHeight() - 4),
          spawn.getZ() + 4);
      player.setXRot(0);
      player.setYRot(0);
      catalog();
      processing(level, player);
      scanner(level, player);
      sprayers(level, player);
    } finally {
      player.stopUsingItem();
      player.getInventory().load(savedInventory);
      player.setPos(position);
      player.setXRot(pitch);
      player.setYRot(yaw);
    }
  }

  private static void catalog() {
    for (String id :
        List.of(
            "hammer",
            "wire_cutters",
            "chemical_sprayer",
            "chemical_sprayer_lighter",
            "oil_scanner")) {
      var item = stack(id).getItem();
      require(ReforgeProfiles.paidGrades(item).size() == 6, "Six paid Diesel grades: " + id);
      require(
          TieredNeoAPI.getReforgeBaseItems(item).size() == 1,
          "One reachable reforge material: " + id);
    }
    for (String id : List.of("lighter", "track_layers_bag", "canister"))
      require(
          ReforgeProfiles.paidGrades(stack(id).getItem()).isEmpty(),
          "Utility or container stays outside reforge pool: " + id);
  }

  private static void processing(ServerLevel level, FakePlayer player) {
    int[] durations = {108, 90, 81, 72, 63, 54};
    for (String id : List.of("hammer", "wire_cutters")) {
      for (int index = 0; index < GRADES.size(); index++) {
        var tool = ranked(stack(id), "diesel_processing_" + GRADES.get(index));
        require(
            tool.getUseDuration(player) == durations[index],
            "Native processing duration: " + id + GRADES.get(index));
      }
    }
    completedRecipes(
        level, player, "hammer", new ItemStack(Items.IRON_INGOT), AllItems.IRON_SHEET.get(), 1);
    for (String metal : List.of("steel", "zinc", "netherite", "bronze")) {
      var sheet =
          BuiltInRegistries.ITEM.get(
              ResourceLocation.parse(
                  metal.equals("steel")
                      ? "aeroscapes_reimagine:steel_sheet"
                      : "vintageimprovements:" + metal + "_sheet"));
      var wire =
          BuiltInRegistries.ITEM.get(
              ResourceLocation.parse("vintageimprovements:" + metal + "_wire"));
      completedRecipes(level, player, "wire_cutters", new ItemStack(sheet), wire, 2);
    }
  }

  private static void completedRecipes(
      ServerLevel level, FakePlayer player, String id, ItemStack input, Item output, int count) {
    int previousWear = 257;
    for (String grade : GRADES) {
      var hammer = ranked(stack(id), "diesel_processing_" + grade);
      int wear = 0;
      for (int trial = 0; trial < 256; trial++) {
        player.getInventory().clearContent();
        hammer.setDamageValue(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        player.setItemInHand(InteractionHand.OFF_HAND, input.copy());
        player.getRandom().setSeed(trial);
        hammer.getItem().use(level, player, InteractionHand.MAIN_HAND);
        require(
            player.getOffhandItem().isEmpty(),
            "Native manual processing reserves exactly one input");
        hammer.getItem().finishUsingItem(hammer, level, player);
        require(
            player.getInventory().countItem(output) == count,
            "Native manual processing preserves recipe yield at every quality: " + id);
        require(
            !hammer.has(CDGDataComponents.PROCESSING_ITEM),
            "Completed recipe clears its pending input");
        wear += hammer.getDamageValue();
        player.stopUsingItem();
      }
      if (grade.equals("poor") || grade.equals("ordinary"))
        require(wear == 256, "Baseline native completed-craft wear");
      else
        require(
            wear < previousWear && wear > 0, "Progressive completed-craft wear saving: " + grade);
      previousWear = wear;
    }
    var hammer = ranked(stack(id), "diesel_processing_legendary");
    player.getInventory().clearContent();
    player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
    player.setItemInHand(InteractionHand.OFF_HAND, input.copy());
    hammer.getItem().use(level, player, InteractionHand.MAIN_HAND);
    hammer.getItem().releaseUsing(hammer, level, player, 20);
    require(
        player.getInventory().countItem(input.getItem()) == 1,
        "Interrupted native processing returns its actual input once");
    require(
        player.getInventory().countItem(output) == 0, "Interrupted processing produces no output");
    require(hammer.getDamageValue() == 0, "Interrupted processing charges no completed-craft wear");
    player.stopUsingItem();
  }

  private static void scanner(ServerLevel level, FakePlayer player) {
    int[] durations = {30, 20, 20, 20, 10, 10};
    for (int index = 0; index < GRADES.size(); index++) {
      var scanner = ranked(stack("oil_scanner"), "diesel_scan_" + GRADES.get(index));
      player.setItemInHand(InteractionHand.MAIN_HAND, scanner);
      scanner.getItem().use(level, player, InteractionHand.MAIN_HAND);
      require(
          scanner.getOrDefault(CDGDataComponents.OIL_SCANNER_PROGRESS, -1) == durations[index],
          "Native scanner startup follows quality: " + GRADES.get(index));
      for (int tick = 0; tick <= durations[index]; tick++)
        scanner.getItem().inventoryTick(scanner, level, player, 0, true);
      require(
          scanner.getOrDefault(CDGDataComponents.OIL_SCANNER_STATE, 0) != 0,
          "Native scanner completes once after preparation");
      require(
          DieselReforges.scanMessage(scanner, 0, player, new ChunkPos(0, 0)) == null,
          "No quality invents oil on an empty reading");
    }
    require(
        DieselReforges.scanMessage(
                ranked(stack("oil_scanner"), "diesel_scan_fine"),
                48_000,
                player,
                new ChunkPos(0, 0))
            != null,
        "Fine quality supplies a clearer confirmed signal");
  }

  private static void sprayers(ServerLevel level, FakePlayer player) {
    for (String id : List.of("chemical_sprayer", "chemical_sprayer_lighter")) {
      for (String grade : GRADES) {
        var sprayer = stack(id);
        FueledToolItem fueled = (FueledToolItem) sprayer.getItem();
        fueled.writeFluid(sprayer, new FluidStack(Fluids.WATER, 100));
        int capacity = fueled.getCapacity(sprayer);
        ranked(sprayer, "diesel_spray_" + grade);
        require(
            fueled.readFluid(sprayer).getAmount() == 100 && fueled.getCapacity(sprayer) == capacity,
            "Reforging preserves stored fluid and native capacity");
        player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);
        for (int elapsed = 0; elapsed < 20; elapsed++)
          sprayer.getItem().onUseTick(level, player, sprayer, 1000 - elapsed);
        var pulses =
            level.getEntitiesOfClass(
                ChemicalSprayerProjectileEntity.class, player.getBoundingBox().inflate(3));
        try {
          require(
              pulses.size() == 10,
              "Every grade retains ten tracked native pulses per twenty ticks: "
                  + grade
                  + "; actual="
                  + pulses.size()
                  + "; fluid="
                  + fueled.readFluid(sprayer).getAmount());
          require(
              fueled.readFluid(sprayer).getAmount() == 90,
              "Every grade spends exactly one mB per pulse");
        } finally {
          pulses.forEach(ChemicalSprayerProjectileEntity::discard);
        }
      }
      double ordinary = firstPulseSpread(level, player, id, "ordinary");
      double legendary = firstPulseSpread(level, player, id, "legendary");
      require(
          legendary < ordinary * 0.25,
          "Legendary first pulse narrows real native projectile directions");
    }
  }

  private static double firstPulseSpread(
      ServerLevel level, FakePlayer player, String id, String grade) {
    double deviation = 0;
    var sprayer = ranked(stack(id), "diesel_spray_" + grade);
    FueledToolItem fueled = (FueledToolItem) sprayer.getItem();
    for (int trial = 0; trial < 64; trial++) {
      fueled.writeFluid(sprayer, new FluidStack(Fluids.WATER, 100));
      sprayer.getItem().onUseTick(level, player, sprayer, 1000);
      var pulses =
          level.getEntitiesOfClass(
              ChemicalSprayerProjectileEntity.class, player.getBoundingBox().inflate(3));
      try {
        require(pulses.size() == 1, "First native pulse exists once");
        var velocity = pulses.getFirst().getDeltaMovement().normalize();
        deviation += Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
      } finally {
        pulses.forEach(ChemicalSprayerProjectileEntity::discard);
      }
    }
    return deviation;
  }

  private static ItemStack stack(String id) {
    return new ItemStack(
        BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("createdieselgenerators", id)));
  }

  private static ItemStack ranked(ItemStack stack, String profile) {
    var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", profile);
    var tier = TieredNeoAPI.getTierAttribute(id);
    require(
        tier != null && tier.isValid(stack.getItem()),
        "Valid Diesel equipment profile: " + profile);
    ModifierUtils.applyTierToStack(stack, id, tier);
    return stack;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
