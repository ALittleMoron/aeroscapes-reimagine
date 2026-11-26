package dev.aeroscapes.equipment.combat;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public enum MartialTechnique {
  SHIELD_BRACE("shield_brace", 60, 160),
  COUNTERATTACK("counterattack", 60, 120),
  HEAVY_STRIKE("heavy_strike", 60, 160),
  SLOWING_SHOT("slowing_shot", 100, 140),
  MARKING_SHOT("marking_shot", 100, 180),
  QUICK_SHOT("quick_shot", 100, 100),
  DISENGAGE("disengage", 30, 100),
  DASH("dash", 50, 100),
  FLANK_STRIKE("flank_strike", 60, 120);

  public static final String GROUP = "aeroscapes_reimagine:martial_techniques";
  public static final float QUICK_DRAW_TIME = 0.45F;
  private static final Map<String, MartialTechnique> BY_ID =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(MartialTechnique::id, Function.identity()));

  private final String talent;
  private final String id;
  private final int windowTicks;
  private final int cooldownTicks;
  private final ResourceLocation icon;

  MartialTechnique(String talent, int windowTicks, int cooldownTicks) {
    this.talent = talent;
    this.id = "aeroscapes_reimagine:" + talent;
    this.windowTicks = windowTicks;
    this.cooldownTicks = cooldownTicks;
    this.icon =
        ResourceLocation.fromNamespaceAndPath(
            "aeroscapes_reimagine", "textures/technique/" + talent + ".png");
  }

  public String id() {
    return id;
  }

  String talent() {
    return talent;
  }

  int windowTicks() {
    return windowTicks;
  }

  public int cooldownTicks() {
    return cooldownTicks;
  }

  String cooldownKey() {
    return talent;
  }

  boolean footwork() {
    return this == DASH || this == DISENGAGE;
  }

  public ResourceLocation icon() {
    return icon;
  }

  public Component displayName() {
    return Component.translatable("aeroscapes_reimagine.technique." + talent + ".name");
  }

  public Component description() {
    return Component.translatable("aeroscapes_reimagine.technique." + talent + ".description");
  }

  @Nullable
  public static MartialTechnique byId(String id) {
    return BY_ID.get(id);
  }
}
