package dev.aeroscapes.guide;

import dev.aeroscapes.preflight.PreflightSurvey;
import dev.ryanhcode.sable.Sable;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class GuideFlight {
  private static final ResourceLocation FILLED =
      ResourceLocation.parse("aeronautics:head_in_the_clouds");
  private static final TagKey<Structure> WRECKS =
      TagKey.create(
          Registries.STRUCTURE,
          ResourceLocation.parse("aeroscapes_reimagine:guide_recoverable_ships"));
  private static final Map<ServerPlayer, Departure> DEPARTURES = new WeakHashMap<>();
  private static final Map<ServerPlayer, WreckVisit> WRECK_VISITS = new WeakHashMap<>();

  private record Departure(UUID hull, double x, double y, double z) {}

  private record WreckVisit(ResourceKey<Level> dimension, BlockPos position) {}

  private GuideFlight() {}

  @SubscribeEvent
  public static void observe(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || player instanceof FakePlayer
        || !player.isAlive()
        || player.tickCount % 5 != 0
        || !ModList.get().isLoaded("aeronautics")
        || !ModList.get().isLoaded("sable")) return;
    boolean checkWreck =
        player.tickCount % 20 == 0 && !FieldGuide.completed(player, "restored_hull");
    boolean checkFlight = !FieldGuide.completed(player, "first_flight");
    if (!checkWreck && !checkFlight) return;
    var body = PreflightSurvey.aboardBody(player);
    if (checkWreck) {
      var level = player.serverLevel();
      var pos = BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, player.position()));
      if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
          && level.structureManager().getStructureWithPieceAt(pos, WRECKS).isValid()) {
        WRECK_VISITS.put(player, new WreckVisit(level.dimension(), pos));
        if (!FieldGuide.completed(player, "wreck_found"))
          FieldGuide.complete(player, "wreck_found");
      }
      var wreck = WRECK_VISITS.get(player);
      if (body != null
          && wreck != null
          && wreck.dimension().equals(level.dimension())
          && wreck.position().distSqr(pos) <= 32 * 32
          && FieldGuide.completed(player, "restore")) {
        FieldGuide.complete(player, "restored_hull");
        WRECK_VISITS.remove(player);
      }
    }
    if (!checkFlight) return;
    if (body == null) {
      DEPARTURES.remove(player);
      return;
    }
    if (!FieldGuide.completed(player, "aboard")) FieldGuide.complete(player, "aboard");
    if (!FieldGuide.done(player, FILLED)) return;
    var position = body.logicalPose().position();
    var start = DEPARTURES.get(player);
    if (start == null || !start.hull().equals(body.getUniqueId())) {
      DEPARTURES.put(
          player, new Departure(body.getUniqueId(), position.x(), position.y(), position.z()));
      return;
    }
    double x = position.x() - start.x();
    double z = position.z() - start.z();
    if (position.y() - start.y() >= 2 && x * x + z * z >= 36) {
      FieldGuide.complete(player, "first_flight");
      DEPARTURES.remove(player);
    }
  }
}
