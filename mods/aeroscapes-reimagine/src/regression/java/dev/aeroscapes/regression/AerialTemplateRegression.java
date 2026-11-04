package dev.aeroscapes.regression;

import dev.aeroscapes.worldgen.AerialTemplateRepairs;
import dev.aeroscapes.worldgen.HangingEntityAnchorProcessor;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Exercises the repaired original resources through native template decoding. */
final class AerialTemplateRegression {
  private AerialTemplateRegression() {}

  static void verify(ServerLevel level) {
    hangingVillageFrames(level);
    for (String name : List.of("airballoon", "airship", "pillager_airballoon", "pillager_airship"))
      template(level, ResourceLocation.fromNamespaceAndPath("cr_aero_structures", name));
    for (String name :
        List.of(
            "alchemist_1",
            "bridge_1",
            "bridge_2",
            "bridge_3",
            "bridge_4",
            "bridge_5",
            "cartographer_1",
            "farm_1",
            "farm_2",
            "farm_3",
            "fletcher_1",
            "fountain",
            "house_1",
            "librarian_1",
            "librarian_2",
            "loom_1",
            "mason_1",
            "villager",
            "weaponsmith_1",
            "weaponsmith_2"))
      template(level, ResourceLocation.fromNamespaceAndPath("skyvillages", name));
  }

  private static void hangingVillageFrames(ServerLevel level) {
    var processors =
        level
            .registryAccess()
            .registryOrThrow(Registries.PROCESSOR_LIST)
            .get(ResourceLocation.parse("skyvillages:house_processor"));
    require(
        processors != null && processors.list().contains(HangingEntityAnchorProcessor.INSTANCE),
        "Village houses lack their hanging-entity anchor processor");
    CompoundTag house =
        level
            .getStructureManager()
            .get(ResourceLocation.parse("skyvillages:house_1"))
            .orElseThrow()
            .save(new CompoundTag());
    int frames = 0;
    for (var value : house.getList("entities", Tag.TAG_COMPOUND)) {
      CompoundTag entity = (CompoundTag) value;
      CompoundTag tag = entity.getCompound("nbt");
      if (!tag.getString("id").equals("minecraft:item_frame")) continue;
      var position = entity.getList("pos", Tag.TAG_DOUBLE);
      var block = entity.getList("blockPos", Tag.TAG_INT);
      WorldgenDataRegression.hangingAnchor(
          level,
          new StructureTemplate.StructureEntityInfo(
              new Vec3(position.getDouble(0), position.getDouble(1), position.getDouble(2)),
              new BlockPos(block.getInt(0), block.getInt(1), block.getInt(2)),
              tag));
      frames++;
    }
    require(frames == 2, "Original village frame coverage changed");
  }

  private static void template(ServerLevel level, ResourceLocation id) {
    ResourceLocation resource =
        ResourceLocation.fromNamespaceAndPath(
            id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    try (var stream = level.getServer().getResourceManager().open(resource)) {
      CompoundTag original = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
      CompoundTag repaired = original.copy();
      AerialTemplateRepairs.apply(id, repaired);
      CompoundTag repeated = repaired.copy();
      AerialTemplateRepairs.apply(id, repeated);
      require(repeated.equals(repaired), "Aerial repair is not idempotent: " + id);
      require(original.get("size").equals(repaired.get("size")), "Aerial geometry changed: " + id);
      require(
          original.get("palette").equals(repaired.get("palette")), "Aerial palette changed: " + id);
      CompoundTag loaded =
          level.getStructureManager().get(id).orElseThrow().save(new CompoundTag());
      for (var value : loaded.getList("entities", Tag.TAG_COMPOUND)) {
        CompoundTag entity = ((CompoundTag) value).getCompound("nbt");
        String type = entity.getString("id");
        require(
            !type.equals("simulated:contraption_diagram")
                && !type.equals("naturalist:butterfly")
                && !type.equals("minecraft:item"),
            "Incidental saved entity survived native load: " + id);
        if (type.equals("minecraft:wandering_trader")) {
          for (var offer : entity.getCompound("Offers").getList("Recipes", Tag.TAG_COMPOUND)) {
            String sold = ((CompoundTag) offer).getCompound("sell").getString("id");
            require(
                !sold.equals("expandeddelight:peanut") && !sold.equals("minecraft:filled_map"),
                "Stale trader offer survived native load: " + id);
          }
        }
        if (type.equals("minecraft:wandering_trader") || type.equals("minecraft:trader_llama"))
          require(
              entity.getBoolean("PersistenceRequired") && entity.getInt("DespawnDelay") == 0,
              "Aerial merchant/llama can despawn: " + id);
        if (type.equals("minecraft:trader_llama"))
          require(entity.getBoolean("Tame"), "Native trader-llama timer can discard the crew");
      }
      for (var value : loaded.getList("blocks", Tag.TAG_COMPOUND)) {
        CompoundTag block = ((CompoundTag) value).getCompound("nbt");
        require(!block.contains("LootTableSeed"), "Author loot seed survived native load: " + id);
      }
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot inspect aerial template: " + id, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
