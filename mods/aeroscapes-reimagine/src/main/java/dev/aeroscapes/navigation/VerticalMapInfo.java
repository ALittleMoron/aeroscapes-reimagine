package dev.aeroscapes.navigation;

import net.minecraft.network.chat.Component;
import xaero.hud.minimap.info.InfoDisplay;
import xaero.hud.minimap.info.widget.InfoDisplayCommonWidgetFactories;
import xaero.lib.common.config.option.value.io.serialization.BuiltInConfigValueIOCodecs;

public final class VerticalMapInfo {
  private VerticalMapInfo() {}

  public static InfoDisplay<Boolean> create() {
    return InfoDisplay.Builder.<Boolean>begin()
        .setId("aeroscapes_map_layer")
        .setName(Component.translatable("gui.aeroscapes_navigation.info"))
        .setDefaultState(true)
        .setCodec(BuiltInConfigValueIOCodecs.BOOLEAN)
        .setWidgetFactory(InfoDisplayCommonWidgetFactories.OFF_ON)
        .setCompiler(
            (display, compiler, session, availableWidth, playerPos) -> {
              if (display.getEffectiveState() && VerticalMapNavigation.view() != null)
                compiler.addWords(VerticalMapNavigation.description().getString());
            })
        .build();
  }
}
