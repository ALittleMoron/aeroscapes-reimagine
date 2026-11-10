package dev.aeroscapes.interfaceart;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class InterfaceArt {
  public static final ResourceLocation ATLAS = texture("atlas");
  public static final ResourceLocation WORDMARK = texture("wordmark");
  public static final ResourceLocation SPLASH = texture("splash");
  public static final ResourceLocation INVENTORY = texture("inventory");
  public static final int LOADING_BACKGROUND = 0x101722;
  public static final int LOADING_ACCENT = 0xe3ae61;

  private static final int ATLAS_WIDTH = 1440;
  private static final int ATLAS_HEIGHT = 900;
  private static final int WORDMARK_WIDTH = 488;
  private static final int WORDMARK_HEIGHT = 112;
  private static Set<ResourceLocation> availableTextures = Set.of();

  private InterfaceArt() {}

  private static ResourceLocation texture(String name) {
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", "textures/gui/interface/" + name + ".png");
  }

  public static boolean available(ResourceLocation texture) {
    return availableTextures.contains(texture);
  }

  public static void refresh(ResourceManager resources) {
    var textures = Minecraft.getInstance().getTextureManager();
    var found = new HashSet<ResourceLocation>();
    for (var texture : List.of(ATLAS, WORDMARK, SPLASH, INVENTORY)) {
      if (resources.getResource(texture).isEmpty()) continue;
      found.add(texture);
      var cached = textures.getTexture(texture, null);
      if (cached != null && cached == MissingTextureAtlasSprite.getTexture()) {
        // The overlay can draw before TextureManager removes the placeholder from the last reload.
        textures.register(texture, new SimpleTexture(texture));
      }
    }
    availableTextures = Set.copyOf(found);
    ContainerArtwork.refresh(resources);
    AtlasPanelPalette.refresh(resources);
  }

  public static void drawAtlas(GuiGraphics graphics, int width, int height, float fade) {
    float scale = Math.max((float) width / ATLAS_WIDTH, (float) height / ATLAS_HEIGHT);
    int scaledWidth = (int) Math.ceil(ATLAS_WIDTH * scale);
    int scaledHeight = (int) Math.ceil(ATLAS_HEIGHT * scale);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    graphics.setColor(1, 1, 1, fade);
    graphics.blit(
        ATLAS,
        (width - scaledWidth) / 2,
        (height - scaledHeight) / 2,
        scaledWidth,
        scaledHeight,
        0,
        0,
        ATLAS_WIDTH,
        ATLAS_HEIGHT,
        ATLAS_WIDTH,
        ATLAS_HEIGHT);
    graphics.setColor(1, 1, 1, 1);
    RenderSystem.disableBlend();
  }

  public static void drawTitle(GuiGraphics graphics, int width, int height, float fade) {
    int markWidth = Math.min(width - 16, WORDMARK_WIDTH / 2);
    int markHeight = markWidth * WORDMARK_HEIGHT / WORDMARK_WIDTH;
    int top = Math.max(4, (height / 4 + 32 - markHeight) / 2);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    graphics.setColor(1, 1, 1, fade);
    graphics.blit(
        WORDMARK,
        (width - markWidth) / 2,
        top,
        markWidth,
        markHeight,
        0,
        0,
        WORDMARK_WIDTH,
        WORDMARK_HEIGHT,
        WORDMARK_WIDTH,
        WORDMARK_HEIGHT);
    graphics.setColor(1, 1, 1, 1);
    RenderSystem.disableBlend();
  }

  public static int loadingColor(int original, int rgb) {
    return available(SPLASH) ? (original & 0xff000000) | rgb : original;
  }
}
