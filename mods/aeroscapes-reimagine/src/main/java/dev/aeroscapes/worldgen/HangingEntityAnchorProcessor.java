package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Repairs author-world anchors in selected templates without discarding hanging entities. */
public final class HangingEntityAnchorProcessor extends StructureProcessor {
  public static final HangingEntityAnchorProcessor INSTANCE = new HangingEntityAnchorProcessor();
  public static final MapCodec<HangingEntityAnchorProcessor> CODEC = MapCodec.unit(INSTANCE);

  private static final DeferredRegister<StructureProcessorType<?>> TYPES =
      DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, "aeroscapes_reimagine");
  private static final DeferredHolder<
          StructureProcessorType<?>, StructureProcessorType<HangingEntityAnchorProcessor>>
      TYPE = TYPES.register("hanging_entity_anchor", () -> () -> CODEC);

  private HangingEntityAnchorProcessor() {}

  /** Register before loading the exact-template processor-list override. */
  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public StructureTemplate.StructureEntityInfo processEntity(
      LevelReader level,
      BlockPos origin,
      StructureTemplate.StructureEntityInfo original,
      StructureTemplate.StructureEntityInfo transformed,
      StructurePlaceSettings settings,
      StructureTemplate template) {
    CompoundTag data = transformed.nbt;
    String type = data.getString("id");
    if (!type.equals("minecraft:item_frame")
        && !type.equals("minecraft:glow_item_frame")
        && !type.equals("minecraft:painting")) {
      return transformed;
    }
    // NeoForge has already transformed blockPos, but vanilla later rewrites only
    // Pos before loading the entity. Leaving TileXYZ untouched logs invalid
    // author-world anchors before the entity is moved to its final position.
    CompoundTag repaired = data.copy();
    repaired.putInt("TileX", transformed.blockPos.getX());
    repaired.putInt("TileY", transformed.blockPos.getY());
    repaired.putInt("TileZ", transformed.blockPos.getZ());
    return new StructureTemplate.StructureEntityInfo(
        transformed.pos, transformed.blockPos, repaired);
  }

  @Override
  protected StructureProcessorType<?> getType() {
    return TYPE.get();
  }
}
