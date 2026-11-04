package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import dev.aeroscapes.ecology.SpawnIslandSites;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class SpawnLocateMixin {
  @WrapMethod(method = "findNearestMapStructure")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Also covers the provider's injected early return for the unique
  // island.
  @Nullable
  private Pair<BlockPos, Holder<Structure>> aeroscapes$dodoHeight(
      ServerLevel level,
      HolderSet<Structure> structures,
      BlockPos origin,
      int radius,
      boolean skipKnown,
      Operation<Pair<BlockPos, Holder<Structure>>> original) {
    var result = original.call(level, structures, origin, radius, skipKnown);
    if (result == null
        || !result
            .getSecond()
            .is(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.parse("spawn:dodo_island"))))
      return result;
    BlockPos position = result.getFirst();
    return Pair.of(
        position.atY(
            SpawnIslandSites.seaLevel(
                level.getChunkSource().randomState(),
                position.getX(),
                position.getZ(),
                position.getY())),
        result.getSecond());
  }
}
