package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** Captures the real loaded item registry; model baking remains a separate client boundary. */
public final class EquipmentCatalogRegression {
  private static final Pattern CUSTOM_TOOL =
      Pattern.compile(
          "(?i).*(Wrench|Hammer|Trowel|SawItem|Knife|Spear|Dagger|Brush|SandPaper|Slingshot|CrabClaw|CaptureNet|CreatureNet|HookItem|StorageTool|EchoBell"
              + "|WorkbenchItem|Pickarang|RopeDart|BombItem|SludgeCharge|Launcher|Compass|Quill|Monstrometer|Gauge|MeterItem|FishFinder|PocketWatch|Thermometer|Whistle|CastingNet"
              + "|BubbleBlower|Abacus|LinkedController|Worldshaper|SymmetryWand|Zapper|ExtendoGrip|SuperGlue|Clipboard|WirelessTerminal|InventoryConfigurator|PaintKit|EnderLinker"
              + "|CameraItem|InterplanarProjector|InstrumentItem|SpectreBoundSpyglass|KeyItem|Paraglider).*");
  private static final Set<String> CUSTOM_IDS =
      Set.of(
          "chipped:saw",
          "quark:trowel",
          "quark:hammer",
          "handcrafted:hammer",
          "supplementaries:wrench",
          "supplementaries:slingshot",
          "naturalist:capture_net",
          "hapi:creature_net",
          "crittersandcompanions:grappling_hook",
          "sophisticatedstorage:paintbrush",
          "sophisticatedstorage:storage_tool",
          "galosphere:echo_bell",
          "spawn:coastal_crab_claw",
          "create:sand_paper",
          "create:red_sand_paper");

  private EquipmentCatalogRegression() {}

  public static void verify(ServerLevel level, Path output) {
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("seed", level.getSeed());
    JsonArray items = new JsonArray();
    JsonArray customCandidates = new JsonArray();
    JsonObject registeredClasses = new JsonObject();
    JsonArray buckets = new JsonArray();
    for (var id : BuiltInRegistries.ITEM.keySet().stream().sorted().toList()) {
      Item item = BuiltInRegistries.ITEM.get(id);
      if (item == null) throw new IllegalStateException("Missing registered item " + id);
      ItemStack stack = new ItemStack(item);
      String className = item.getClass().getName();
      boolean physicalBucket = dev.aeroscapes.atmosphere.BucketPresentation.physical(item);
      if (id.getPath().endsWith("bucket")
          && !(item instanceof com.crispytwig.naturalist.world.item.CaughtEntityItem)
          && !physicalBucket)
        throw new IllegalStateException(
            "Unclassified physical bucket: " + id + " (" + className + ")");
      if (physicalBucket) {
        if (!stack.is(net.neoforged.neoforge.common.Tags.Items.BUCKETS))
          throw new IllegalStateException("Physical bucket missing filter tag: " + id);
        buckets.add(id.toString());
      }
      JsonArray members = registeredClasses.getAsJsonArray(className);
      if (members == null) {
        members = new JsonArray();
        registeredClasses.add(className, members);
      }
      members.add(id.toString());
      boolean typed =
          functional(item)
              || stack.has(DataComponents.TOOL)
              || stack.getAttributeModifiers().modifiers().stream()
                  .anyMatch(
                      modifier -> modifier.attribute().value() == Attributes.ATTACK_DAMAGE.value());
      boolean knownCustom = CUSTOM_IDS.contains(id.toString());
      boolean namedCustom = CUSTOM_TOOL.matcher(item.getClass().getSimpleName()).matches();
      if (typed || knownCustom) items.add(entry(id.toString(), item, typed));
      else if (namedCustom) customCandidates.add(entry(id.toString(), item, false));
    }
    report.add("equipment", items);
    report.add("unclassified_custom_candidates", customCandidates);
    report.add("all_registered_item_classes", registeredClasses);
    report.add("physical_buckets", buckets);
    var octopus =
        BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse("spawn:captured_octopus"));
    for (var container :
        java.util.List.of(
            net.minecraft.world.item.Items.BUCKET,
            net.minecraft.world.item.Items.WATER_BUCKET,
            net.minecraft.world.item.Items.GLASS_BOTTLE,
            net.minecraft.world.item.Items.BOWL)) {
      var captured = new ItemStack(octopus);
      var data = new net.minecraft.nbt.CompoundTag();
      data.put("Item", new ItemStack(container).save(level.registryAccess()));
      captured.set(
          DataComponents.BUCKET_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(data));
      boolean bucket =
          container == net.minecraft.world.item.Items.BUCKET
              || container == net.minecraft.world.item.Items.WATER_BUCKET;
      if (dev.aeroscapes.atmosphere.BucketPresentation.physical(captured, level.registryAccess())
          != bucket)
        throw new IllegalStateException("Captured octopus container routing: " + container);
    }
    report.addProperty(
        "captured_octopus_bucket_states", "empty bucket and water bucket; bottle/bowl retained");
    report.addProperty("registered_items", BuiltInRegistries.ITEM.size());
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException error) {
      throw new IllegalStateException("Cannot write registered equipment catalog " + output, error);
    }
  }

  private static boolean functional(Item item) {
    return item instanceof DiggerItem
        || item instanceof SwordItem
        || item instanceof ProjectileWeaponItem
        || item instanceof FishingRodItem
        || item instanceof TridentItem
        || item instanceof ShieldItem
        || item instanceof ShearsItem
        || item instanceof BrushItem
        || item instanceof MaceItem
        || item instanceof FlintAndSteelItem;
  }

  private static JsonObject entry(String id, Item item, boolean typed) {
    JsonObject entry = new JsonObject();
    entry.addProperty("id", id);
    entry.addProperty("class", item.getClass().getName());
    entry.addProperty("typed_equipment", typed);
    return entry;
  }
}
