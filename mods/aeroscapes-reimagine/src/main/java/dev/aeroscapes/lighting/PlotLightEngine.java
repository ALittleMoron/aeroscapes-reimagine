package dev.aeroscapes.lighting;

import dev.aeroscapes.mixin.scalablelux.LevelLightEngineAccess;
import dev.aeroscapes.mixin.scalablelux.LightEngineStorageAccess;
import dev.aeroscapes.mixin.scalablelux.LightStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.jetbrains.annotations.Nullable;

/** Independent vanilla storage for one Sable plot under ScalableLux. */
public final class PlotLightEngine extends LevelLightEngine {
  private final LightEngine<?, ?> block;
  private final LightEngine<?, ?> sky;

  public PlotLightEngine(LightChunkGetter source, boolean hasSky) {
    super(source, true, hasSky);
    block = new BlockLightEngine(source);
    sky = hasSky ? new SkyLightEngine(source) : null;
    LevelLightEngineAccess fields = (LevelLightEngineAccess) (Object) this;
    fields.aeroscapes$setBlockEngine(block);
    fields.aeroscapes$setSkyEngine(sky);
  }

  @Override
  public void checkBlock(BlockPos pos) {
    block.checkBlock(pos);
    if (sky != null) sky.checkBlock(pos);
  }

  @Override
  public boolean hasLightWork() {
    return block.hasLightWork() || (sky != null && sky.hasLightWork());
  }

  @Override
  public int runLightUpdates() {
    return block.runLightUpdates() + (sky != null ? sky.runLightUpdates() : 0);
  }

  @Override
  public void updateSectionStatus(SectionPos pos, boolean empty) {
    block.updateSectionStatus(pos, empty);
    if (sky != null) sky.updateSectionStatus(pos, empty);
  }

  @Override
  public void setLightEnabled(ChunkPos pos, boolean enabled) {
    block.setLightEnabled(pos, enabled);
    if (sky != null) sky.setLightEnabled(pos, enabled);
  }

  @Override
  public void propagateLightSources(ChunkPos pos) {
    block.propagateLightSources(pos);
    if (sky != null) sky.propagateLightSources(pos);
  }

  @Override
  public LayerLightEventListener getLayerListener(LightLayer layer) {
    return layer == LightLayer.BLOCK
        ? block
        : sky == null ? LayerLightEventListener.DummyLightLayerEventListener.INSTANCE : sky;
  }

  @Override
  public String getDebugData(LightLayer layer, SectionPos pos) {
    LightEngine<?, ?> engine = layer == LightLayer.BLOCK ? block : sky;
    return engine == null ? "n/a" : engine.getDebugData(pos.asLong());
  }

  @Override
  public LayerLightSectionStorage.SectionType getDebugSectionType(
      LightLayer layer, SectionPos pos) {
    LightEngine<?, ?> engine = layer == LightLayer.BLOCK ? block : sky;
    return engine == null
        ? LayerLightSectionStorage.SectionType.EMPTY
        : engine.getDebugSectionType(pos.asLong());
  }

  @Override
  public void queueSectionData(LightLayer layer, SectionPos pos, @Nullable DataLayer data) {
    LightEngine<?, ?> engine = layer == LightLayer.BLOCK ? block : sky;
    if (engine != null) engine.queueSectionData(pos.asLong(), data);
  }

  @Override
  public void retainData(ChunkPos pos, boolean retain) {
    block.retainData(pos, retain);
    if (sky != null) sky.retainData(pos, retain);
  }

  @Override
  public int getRawBrightness(BlockPos pos, int darkness) {
    return Math.max(block.getLightValue(pos), sky == null ? 0 : sky.getLightValue(pos) - darkness);
  }

  @Override
  public boolean lightOnInSection(SectionPos pos) {
    return lightOn(block, pos) && (sky == null || lightOn(sky, pos));
  }

  private static boolean lightOn(LightEngine<?, ?> engine, SectionPos pos) {
    return ((LightStorageAccess)
            ((LightEngineStorageAccess) (Object) engine).aeroscapes$getStorage())
        .aeroscapes$lightOnInSection(pos.asLong());
  }
}
