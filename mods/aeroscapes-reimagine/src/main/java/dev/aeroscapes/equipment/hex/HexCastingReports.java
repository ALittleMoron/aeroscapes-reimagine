package dev.aeroscapes.equipment.hex;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.items.magic.ItemPackagedHex;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class HexCastingReports {
  private static final String MESSAGE = "aeroscapes_reimagine.hex_quality.";
  private static final String LAST_MISHAP = "aeroscapes_reimagine:last_hex_mishap";
  private static final Map<ServerPlayer, StaffTrace> TRACES = new WeakHashMap<>();

  private HexCastingReports() {}

  private static final class StaffTrace {
    final ItemStack staff;
    int position;
    int lastPosition;
    Component lastPattern;

    StaffTrace(ItemStack staff) {
      this.staff = staff;
    }
  }

  public static void reset(ServerPlayer player) {
    TRACES.remove(player);
  }

  public static void finishStep(StaffCastEnv environment, CastResult result, boolean stopped) {
    if (!(result.getCast() instanceof PatternIota pattern)) return;
    var player = environment.getCaster();
    var staff = player.getItemInHand(environment.getCastingHand());
    String profile = ReforgeProfiles.path(staff);
    if (!profile.startsWith("hex_staff_") || profile.equals("hex_staff_poor")) {
      reset(player);
      return;
    }
    var trace = TRACES.get(player);
    if (trace == null || trace.staff != staff) {
      trace = new StaffTrace(staff);
      TRACES.put(player, trace);
    }
    ++trace.position;
    if (stopped || !result.getResolutionType().getSuccess()) {
      Component report;
      if (profile.equals("hex_staff_ordinary")) {
        int first = (trace.position - 1) / 4 * 4 + 1;
        report = Component.translatable(MESSAGE + "range", first, first + 3);
      } else {
        var position = Component.translatable(MESSAGE + "position", trace.position);
        if (!profile.equals("hex_staff_fine")) {
          if (trace.lastPattern == null) {
            position.append(Component.translatable(MESSAGE + "no_previous"));
          } else if (profile.equals("hex_staff_exceptional")) {
            position.append(Component.translatable(MESSAGE + "last", trace.lastPattern));
          } else {
            position.append(
                Component.translatable(MESSAGE + "last_at", trace.lastPosition, trace.lastPattern));
          }
        }
        if (profile.equals("hex_staff_legendary"))
          position.append(
              Component.translatable(
                  MESSAGE + "failed", PatternIota.display(pattern.getPattern())));
        report = position;
      }
      player.sendSystemMessage(report);
    } else if (result.getResolutionType() == ResolvedPatternType.EVALUATED) {
      trace.lastPattern = PatternIota.display(pattern.getPattern());
      trace.lastPosition = trace.position;
    }
  }

  public static void rememberMishap(
      ItemStack carrier, Component reason, HolderLookup.Provider registries) {
    if (!ReforgeProfiles.path(carrier).startsWith("hex_carrier_")) return;
    ComponentSerialization.CODEC
        .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), reason)
        .result()
        .ifPresent(
            encoded ->
                CustomData.update(
                    DataComponents.CUSTOM_DATA, carrier, tag -> tag.put(LAST_MISHAP, encoded)));
  }

  public static void carrierTooltip(
      ItemStack stack, List<Component> lines, HolderLookup.Provider registries) {
    if (!(stack.getItem() instanceof ItemPackagedHex carrier) || !carrier.hasHex(stack)) return;
    String profile = ReforgeProfiles.path(stack);
    if (!profile.startsWith("hex_carrier_")) return;
    long maximum = carrier.getMaxMedia(stack);
    if (maximum <= 0) return;
    long remaining = carrier.getMedia(stack);
    var charge = charge(profile, remaining, maximum);
    if (charge != null) {
      lines.removeIf(
          line ->
              line.getContents() instanceof TranslatableContents text
                  && text.getKey().equals("hexcasting.tooltip.media_amount.advanced"));
      lines.add(charge);
    }
    if (registries != null
        && (profile.equals("hex_carrier_epic") || profile.equals("hex_carrier_legendary"))) {
      var tag =
          stack
              .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
              .copyTag()
              .get(LAST_MISHAP);
      if (tag != null)
        ComponentSerialization.CODEC
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .result()
            .ifPresent(reason -> lines.add(Component.translatable(MESSAGE + "mishap", reason)));
    }
  }

  private static Component charge(String profile, long remaining, long maximum) {
    var percent = BigDecimal.valueOf(remaining).movePointRight(2);
    if (profile.equals("hex_carrier_poor")) {
      var band = percent.divide(BigDecimal.valueOf(maximum), -1, RoundingMode.DOWN);
      return Component.translatable(MESSAGE + "band", band.toPlainString());
    }
    if (profile.equals("hex_carrier_ordinary")) return null;
    int precision = profile.equals("hex_carrier_fine") ? 3 : 4;
    String available = dust(remaining, precision);
    String total = dust(maximum, precision);
    if (profile.equals("hex_carrier_legendary"))
      return Component.translatable(MESSAGE + "units", available, total, remaining, maximum);
    int percentPlaces = profile.equals("hex_carrier_fine") ? 1 : 2;
    return Component.translatable(
        MESSAGE + "charge",
        available,
        total,
        percent
            .divide(BigDecimal.valueOf(maximum), percentPlaces, RoundingMode.DOWN)
            .toPlainString());
  }

  private static String dust(long media, int precision) {
    return BigDecimal.valueOf(media, 4)
        .setScale(precision, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString();
  }
}
