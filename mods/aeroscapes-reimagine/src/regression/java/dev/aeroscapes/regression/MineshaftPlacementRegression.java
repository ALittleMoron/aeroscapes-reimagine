package dev.aeroscapes.regression;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;

/** Assembly queries exercise the real transformed native method, not forced world placement. */
final class MineshaftPlacementRegression {
  private MineshaftPlacementRegression() {}

  static void verify(ServerLevel level) {
    var generator = level.getChunkSource().getGenerator();
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    for (String name : List.of("mineshaft", "mineshaft_mesa")) {
      Structure structure =
          level
              .registryAccess()
              .registryOrThrow(Registries.STRUCTURE)
              .get(ResourceLocation.withDefaultNamespace(name));
      if (structure == null) throw new IllegalStateException("Missing mineshaft settings " + name);
      // Isolate the transformed vanilla class from the pack's external structure wrappers.
      var mineshaft =
          new MineshaftStructure(
              new Structure.StructureSettings(
                  structure.biomes(),
                  structure.spawnOverrides(),
                  structure.step(),
                  structure.terrainAdaptation()),
              name.equals("mineshaft_mesa")
                  ? MineshaftStructure.Type.MESA
                  : MineshaftStructure.Type.NORMAL);
      for (long seed : List.of(1L, 42L, -1L, 777L)) {
        var context =
            new Structure.GenerationContext(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                seed,
                new ChunkPos(seed < 0 ? -12 : 12, -18),
                level,
                ignored -> true);
        var stub = mineshaft.findGenerationPoint(context).orElseThrow();
        var pieces = stub.getPiecesBuilder().build().pieces();
        if (pieces.stream().filter(piece -> piece instanceof MineshaftPieces.MineShaftRoom).count()
            != 2) {
          throw new IllegalStateException("Legacy mineshaft assembly count changed");
        }
        StringBuilder data = new StringBuilder(stub.position().toString());
        for (var piece : pieces) data.append(piece.createTag(serialization));
        for (int index = 0; index < 8; index++)
          data.append(context.random().nextLong()).append(':');
        FeaturePlacementRegression.check(name, seed, sha256(data.toString()), pieces.size());
      }
    }
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }
}
