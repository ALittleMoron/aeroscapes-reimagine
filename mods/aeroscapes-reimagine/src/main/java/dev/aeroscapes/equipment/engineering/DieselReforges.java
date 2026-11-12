package dev.aeroscapes.equipment.engineering;

import dev.aeroscapes.engineering.OilFields;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public final class DieselReforges {
  private DieselReforges() {}

  public static float sprayFocus(ItemStack sprayer, int elapsed) {
    return switch (ReforgeProfiles.basePath(sprayer)) {
      case "diesel_spray_poor" -> elapsed < 6 ? 1.4F : 1F;
      case "diesel_spray_fine" -> 0.8F;
      case "diesel_spray_exceptional" -> elapsed < 6 ? 0.8F : 0.5F;
      case "diesel_spray_epic" -> 0.5F;
      case "diesel_spray_legendary" -> elapsed < 2 ? 0.1F : 0.5F;
      default -> 1F;
    };
  }

  public static int scanDuration(ItemStack scanner, int duration) {
    return switch (ReforgeProfiles.basePath(scanner)) {
      case "diesel_scan_poor" -> duration * 3 / 2;
      case "diesel_scan_epic", "diesel_scan_legendary" -> Math.max(1, duration / 2);
      default -> duration;
    };
  }

  public static int processingDuration(ItemStack tool, int duration) {
    int percent =
        switch (ReforgeProfiles.basePath(tool)) {
          case "diesel_processing_poor" -> 120;
          case "diesel_processing_fine" -> 90;
          case "diesel_processing_exceptional" -> 80;
          case "diesel_processing_epic" -> 70;
          case "diesel_processing_legendary" -> 60;
          default -> 100;
        };
    return Math.max(1, duration * percent / 100);
  }

  public static int processingWear(ItemStack tool, RandomSource random, int wear) {
    int saving =
        switch (ReforgeProfiles.basePath(tool)) {
          case "diesel_processing_fine" -> 15;
          case "diesel_processing_exceptional" -> 25;
          case "diesel_processing_epic" -> 35;
          case "diesel_processing_legendary" -> 45;
          default -> 0;
        };
    return wear == 1 && saving > 0 && random.nextInt(100) < saving ? 0 : wear;
  }

  public static @Nullable Component scanMessage(
      ItemStack scanner, int amount, Entity user, ChunkPos chunk) {
    String profile = ReforgeProfiles.basePath(scanner);
    if (amount <= 0 || !profile.startsWith("diesel_scan_")) return null;
    if (profile.equals("diesel_scan_fine"))
      return Component.translatable(
          "aeroscapes_reimagine.equipment.diesel.scan.signal", chunk.x, chunk.z);
    if (!profile.equals("diesel_scan_exceptional")
        && !profile.equals("diesel_scan_epic")
        && !profile.equals("diesel_scan_legendary")) return null;
    var reading =
        Component.translatable("aeroscapes_reimagine.equipment.diesel.scan.amount", amount);
    if (!profile.equals("diesel_scan_legendary") || !(user.level() instanceof ServerLevel level))
      return reading;
    var target = OilFields.findTarget(level, chunk);
    if (target == null) return reading;
    double deltaX = target.getX() + 0.5 - user.getX();
    double deltaZ = target.getZ() + 0.5 - user.getZ();
    String direction =
        deltaX * deltaX + deltaZ * deltaZ < 0.25
            ? "here"
            : Direction.getNearest(deltaX, 0, deltaZ).getName();
    var bearing = Component.translatable("aeroscapes_reimagine.equipment.diesel.scan." + direction);
    return reading.append(
        Component.translatable(
            "aeroscapes_reimagine.equipment.diesel.scan.target",
            bearing,
            target.getX(),
            target.getY(),
            target.getZ()));
  }
}
