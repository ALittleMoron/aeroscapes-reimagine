package dev.aeroscapes.progression.client;

import dev.aeroscapes.inventory.InventoryNavigation;
import dev.aeroscapes.mixin.progression.SkillsScreenAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.puffish.skillsmod.api.Skill;
import net.puffish.skillsmod.client.SkillsClientMod;
import net.puffish.skillsmod.client.config.ClientIconConfig;
import net.puffish.skillsmod.client.config.skill.ClientSkillConfig;
import net.puffish.skillsmod.client.config.skill.ClientSkillConnectionConfig;
import net.puffish.skillsmod.client.data.ClientCategoryData;
import net.puffish.skillsmod.client.data.ClientSkillScreenData;
import net.puffish.skillsmod.client.gui.SkillsScreen;
import net.puffish.skillsmod.client.network.packets.out.SkillClickOutPacket;

/** Atlas navigation over the provider's authoritative categories, states and purchase packets. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class TalentScreen extends Screen {
  private static final int TEXT = 0xffd6e0eb;
  private static final int MUTED = 0xff92a9c3;
  private static final int GOLD = 0xffe9c084;
  private final ClientSkillScreenData data;
  private final TreeViewport camera = new TreeViewport();
  private final ScrollViewport detailsScroll = new ScrollViewport();
  private final List<Button> tabs = new ArrayList<>();
  private Page page = Page.COMBAT;
  private String selected;
  private EditBox search;
  private Button learn;
  private ClientCategoryData fittedCategory;
  private boolean dragging;
  private double pressX;
  private double pressY;
  private double travel;
  private boolean detailsDragging;
  private double detailsGrab;

  public TalentScreen(ClientSkillScreenData data) {
    super(text("talents_button"));
    this.data = data;
  }

  @SubscribeEvent
  public static void replaceProviderScreen(ScreenEvent.Opening event) {
    if (event.getNewScreen() instanceof SkillsScreen nativeScreen) {
      event.setNewScreen(
          new TalentScreen(((SkillsScreenAccessor) nativeScreen).aeroscapes$screenData()));
    }
  }

  private static Component text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.progression." + key, arguments);
  }

  @Override
  protected void init() {
    tabs.clear();
    search = null;
    if (width < 320 || height < 220) {
      addRenderableWidget(
          Button.builder(Component.literal("×"), button -> onClose())
              .bounds(width - 33, 13, 17, 18)
              .build());
      return;
    }
    int tabWidth = (width - 40) / Page.values().length;
    for (Page tab : Page.values()) {
      var button =
          Button.builder(
                  text("page." + tab.id),
                  pressed -> {
                    page = tab;
                    selected = null;
                    detailsScroll.reset();
                    fittedCategory = null;
                    search.setValue("");
                  })
              .bounds(16 + tab.ordinal() * (tabWidth + 4), 39, tabWidth, 20)
              .build();
      addRenderableWidget(button);
      tabs.add(button);
    }
    search = new EditBox(font, 20, 68, Math.max(34, canvasWidth() - 74), 16, text("search"));
    search.setHint(text("search"));
    addRenderableWidget(search);
    addRenderableWidget(
        Button.builder(text("fit"), button -> fit())
            .bounds(24 + search.getWidth(), 66, 62, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("×"), button -> onClose())
            .bounds(width - 33, 13, 17, 18)
            .build());
    learn =
        addRenderableWidget(
            Button.builder(text("learn"), button -> purchase())
                .bounds(inspectorX() + 8, height - 57, 140, 20)
                .build());
    fittedCategory = null;
    detailsScroll.reset();
  }

  private ClientCategoryData category() {
    return data.getCategory(ResourceLocation.parse("aeroscapes_reimagine:" + page.category))
        .orElse(null);
  }

  private List<ClientSkillConfig> nodes() {
    var category = category();
    return category == null
        ? List.of()
        : category.getConfig().skills().values().stream()
            .filter(node -> page.accepts(node.id()))
            .filter(node -> category.getConfig().getDefinitionById(node.definitionId()).isPresent())
            .sorted(Comparator.comparing(ClientSkillConfig::id))
            .toList();
  }

  private int canvasWidth() {
    return Math.max(80, width - 204);
  }

  private int canvasHeight() {
    return Math.max(60, height - 132);
  }

  private int inspectorX() {
    return width - 176;
  }

  private int nodeX(ClientSkillConfig node) {
    return 16 + canvasWidth() / 2 + (int) Math.round(camera.x(node.x()));
  }

  private int nodeY(ClientSkillConfig node) {
    return 94 + canvasHeight() / 2 + (int) Math.round(camera.y(node.y()));
  }

  private boolean inside(double x, double y) {
    return x >= 16 && x < 16 + canvasWidth() && y >= 94 && y < 94 + canvasHeight();
  }

  private void fit() {
    var nodes = nodes();
    if (nodes.isEmpty()) return;
    camera.fit(
        nodes.stream().mapToInt(ClientSkillConfig::x).min().orElse(0),
        nodes.stream().mapToInt(ClientSkillConfig::y).min().orElse(0),
        nodes.stream().mapToInt(ClientSkillConfig::x).max().orElse(0),
        nodes.stream().mapToInt(ClientSkillConfig::y).max().orElse(0),
        canvasWidth(),
        canvasHeight());
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.renderBackground(graphics, mouseX, mouseY, partialTick);
    AtlasProgressionWidgets.panel(graphics, 8, 8, width - 16, height - 16);
    graphics.drawString(font, title, 20, 18, GOLD, false);
    if (width < 320 || height < 220) {
      graphics.drawWordWrap(font, text("small_window"), 20, 60, Math.max(30, width - 40), TEXT);
      return;
    }
    for (int index = 0; index < tabs.size(); index++)
      tabs.get(index).active = index != page.ordinal();
    var category = category();
    if (category != fittedCategory) {
      fittedCategory = category;
      fit();
    }
    if (category != null) {
      var points = text("points", category.getPointsLeft());
      graphics.drawString(font, points, width - 45 - font.width(points), 19, GOLD, false);
      graphics.drawString(
          font,
          text(page == Page.PROFESSION ? "profession_budget" : "combat_budget"),
          inspectorX() + 5,
          69,
          MUTED,
          false);
    }
    AtlasProgressionWidgets.panel(graphics, 16, 94, canvasWidth(), canvasHeight());
    AtlasProgressionWidgets.panel(graphics, inspectorX(), 94, 156, height - 126);
    graphics.enableScissor(20, 98, 12 + canvasWidth(), 90 + canvasHeight());
    if (category != null) {
      drawConnections(graphics, category, category.getConfig().normalConnections(), false);
      drawConnections(graphics, category, category.getConfig().exclusiveConnections(), true);
      var hoveredNode = inside(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
      for (var node : nodes()) drawNode(graphics, category, node, node == hoveredNode);
    }
    graphics.disableScissor();
    drawDetails(graphics, category);
    graphics.drawString(font, text("navigation"), 20, height - 24, MUTED, false);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    if (width < 320 || height < 220) return;
    var category = category();
    if (category != null && inside(mouseX, mouseY)) {
      var hovered = nodeAt(mouseX, mouseY);
      if (hovered != null)
        category
            .getConfig()
            .getDefinitionById(hovered.definitionId())
            .ifPresent(
                definition -> {
                  var tooltip = new ArrayList<Component>();
                  tooltip.add(definition.title());
                  tooltip.add(definition.description().copy().withStyle(ChatFormatting.GRAY));
                  tooltip.add(stateText(category.getSkillState(hovered)));
                  graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                });
    }
  }

  private void drawConnections(
      GuiGraphics graphics,
      ClientCategoryData category,
      java.util.Collection<ClientSkillConnectionConfig> connections,
      boolean exclusive) {
    for (var edge : connections) {
      var a = category.getConfig().skills().get(edge.skillAId());
      var b = category.getConfig().skills().get(edge.skillBId());
      if (a == null || b == null || !page.accepts(a.id()) || !page.accepts(b.id())) continue;
      boolean unlocked =
          category.getSkillState(a) == Skill.State.UNLOCKED
              && category.getSkillState(b) == Skill.State.UNLOCKED;
      int color = exclusive ? 0xffbd806d : unlocked ? GOLD : 0xff627b96;
      line(graphics, nodeX(a), nodeY(a), nodeX(b), nodeY(b), color, exclusive);
    }
  }

  private static void line(
      GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, boolean dashed) {
    int length = (int) Math.round(Math.hypot(x2 - x1, y2 - y1));
    graphics.pose().pushPose();
    graphics.pose().translate(x1, y1, 0);
    graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
    if (dashed) {
      for (int step = 0; step < length; step += 8) {
        graphics.fill(step, -1, Math.min(length, step + 4), 2, 0xff111b29);
        graphics.fill(step, 0, Math.min(length, step + 4), 1, color);
      }
    } else {
      graphics.fill(0, -1, length, 2, 0xff111b29);
      graphics.fill(0, 0, length, 1, color);
    }
    graphics.pose().popPose();
  }

  private void drawNode(
      GuiGraphics graphics, ClientCategoryData category, ClientSkillConfig node, boolean hovered) {
    var definition = category.getConfig().getDefinitionById(node.definitionId()).orElse(null);
    if (definition == null) return;
    int x = nodeX(node), y = nodeY(node);
    int radius = Math.max(9, (int) (14 * camera.scale()));
    Skill.State state = category.getSkillState(node);
    boolean match =
        !search.getValue().isBlank()
            && definition
                .title()
                .getString()
                .toLowerCase(Locale.ROOT)
                .contains(search.getValue().toLowerCase(Locale.ROOT));
    if (node.id().equals(selected) || match) {
      graphics.fill(x - radius - 3, y - radius - 3, x + radius + 3, y + radius + 3, GOLD);
      graphics.fill(x - radius - 2, y - radius - 2, x + radius + 2, y + radius + 2, 0xff111b29);
    }
    ResourceLocation frame =
        ResourceLocation.parse(
            "aeroscapes_reimagine:textures/gui/skills/frame_"
                + state.name().toLowerCase(Locale.ROOT)
                + ".png");
    graphics.blit(frame, x - radius, y - radius, radius * 2, radius * 2, 0, 0, 26, 26, 26, 26);
    graphics.pose().pushPose();
    graphics.pose().translate(x - 8 * camera.scale(), y - 8 * camera.scale(), 0);
    graphics.pose().scale((float) camera.scale(), (float) camera.scale(), 1);
    if (definition.icon() instanceof ClientIconConfig.ItemIconConfig item)
      graphics.renderItem(item.item(), 0, 0);
    else if (definition.icon() instanceof ClientIconConfig.TextureIconConfig texture)
      graphics.blit(texture.texture(), 0, 0, 0, 0, 16, 16, 16, 16);
    else if (definition.icon() instanceof ClientIconConfig.EffectIconConfig effect)
      graphics.blit(
          0,
          0,
          0,
          16,
          16,
          minecraft
              .getMobEffectTextures()
              .get(
                  net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
                      effect.effect())));
    graphics.pose().popPose();
    if (state == Skill.State.UNLOCKED)
      graphics.drawString(font, "✓", x + radius - 5, y + radius - 7, GOLD, false);
    if (hovered)
      graphics.renderOutline(x - radius - 1, y - radius - 1, radius * 2 + 2, radius * 2 + 2, GOLD);
  }

  private Component stateText(Skill.State state) {
    return text("state." + state.name().toLowerCase(Locale.ROOT));
  }

  private void drawDetails(GuiGraphics graphics, ClientCategoryData category) {
    learn.active = false;
    if (category == null) {
      graphics.drawWordWrap(font, text("waiting"), inspectorX() + 9, 105, 138, TEXT);
      return;
    }
    var node = selected == null ? null : category.getConfig().skills().get(selected);
    if (node == null) {
      graphics.drawWordWrap(font, text("select"), inspectorX() + 9, 105, 138, TEXT);
      return;
    }
    var definition = category.getConfig().getDefinitionById(node.definitionId()).orElse(null);
    if (definition == null) return;
    int x = inspectorX() + 9, y = 105 - (int) detailsScroll.offset();
    graphics.enableScissor(x, 105, x + 138, height - 65);
    y = paragraph(graphics, definition.title(), x, y, GOLD);
    y = paragraph(graphics, definition.description(), x, y + 4, TEXT);
    y = paragraph(graphics, text("cost", definition.cost()), x, y + 6, MUTED);
    y = paragraph(graphics, stateText(category.getSkillState(node)), x, y + 3, MUTED);
    var exclusions =
        category.getConfig().skillExclusiveNeighbors().getOrDefault(node.id(), List.of());
    for (String id : exclusions) {
      var other = category.getConfig().skills().get(id);
      if (other != null) {
        var otherDefinition =
            category.getConfig().getDefinitionById(other.definitionId()).orElse(null);
        if (otherDefinition == null) continue;
        y = paragraph(graphics, text("excludes", otherDefinition.title()), x, y + 6, 0xffd49a83);
      }
    }
    graphics.disableScissor();
    detailsScroll.resize(y + (int) detailsScroll.offset() - 105, Math.max(1, height - 170));
    if (detailsScroll.maximum() > 0) {
      int top = 105 + detailsScroll.thumbPosition();
      graphics.fill(width - 26, 105, width - 23, height - 65, 0xff111b29);
      graphics.fill(width - 26, top, width - 23, top + detailsScroll.thumbSize(), MUTED);
    }
    learn.active = category.getSkillState(node) == Skill.State.AFFORDABLE;
  }

  private int paragraph(GuiGraphics graphics, Component component, int x, int y, int color) {
    for (var line : font.split(component, 138)) {
      graphics.drawString(font, line, x, y, color, false);
      y += font.lineHeight + 2;
    }
    return y;
  }

  private void purchase() {
    var category = category();
    if (category == null || selected == null || !learn.active) return;
    SkillsClientMod.getInstance()
        .getPacketSender()
        .send(new SkillClickOutPacket(category.getConfig().id(), selected));
  }

  private ClientSkillConfig nodeAt(double x, double y) {
    int radius = Math.max(9, (int) (14 * camera.scale()));
    return nodes().reversed().stream()
        .filter(node -> Math.abs(nodeX(node) - x) <= radius && Math.abs(nodeY(node) - y) <= radius)
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (width < 320 || height < 220) return super.mouseClicked(x, y, button);
    if (button == 0
        && x >= width - 29
        && x < width - 20
        && y >= 105
        && y < height - 65
        && detailsScroll.maximum() > 0) {
      detailsDragging = true;
      double relative = y - 105 - detailsScroll.thumbPosition();
      detailsGrab =
          relative >= 0 && relative < detailsScroll.thumbSize()
              ? relative
              : detailsScroll.thumbSize() / 2.0;
      detailsScroll.drag(y - 105 - detailsGrab);
      setFocused(null);
      return true;
    }
    if (button == 0 && inside(x, y)) {
      dragging = true;
      pressX = x;
      pressY = y;
      travel = 0;
      search.setFocused(false);
      setFocused(null);
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
    if (width < 320 || height < 220) return super.mouseDragged(x, y, button, deltaX, deltaY);
    if (detailsDragging && button == 0) {
      detailsScroll.drag(y - 105 - detailsGrab);
      return true;
    }
    if (!dragging || button != 0) return super.mouseDragged(x, y, button, deltaX, deltaY);
    travel += Math.abs(deltaX) + Math.abs(deltaY);
    if (travel > 3) camera.pan(deltaX, deltaY);
    return true;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (width < 320 || height < 220) return super.mouseReleased(x, y, button);
    if (detailsDragging && button == 0) {
      detailsDragging = false;
      return true;
    }
    if (dragging && button == 0) {
      dragging = false;
      if (travel <= 3 && Math.abs(x - pressX) + Math.abs(y - pressY) <= 3) {
        var node = nodeAt(x, y);
        selected = node == null ? null : node.id();
        detailsScroll.reset();
      }
      return true;
    }
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (width < 320 || height < 220) return super.mouseScrolled(x, y, horizontal, vertical);
    if (x >= inspectorX() && x < width - 20 && y >= 105 && y < height - 65) {
      detailsScroll.wheel(vertical);
      return true;
    }
    if (!inside(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
    camera.zoom(vertical, x - 16 - canvasWidth() / 2.0, y - 94 - canvasHeight() / 2.0);
    return true;
  }

  @Override
  public void onClose() {
    InventoryNavigation.openInventory();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if ((search == null || !search.isFocused())
        && (SkillsClientMod.OPEN_KEY_BINDING.matches(key, scan)
            || minecraft.options.keyInventory.matches(key, scan))) {
      onClose();
      return true;
    }
    if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && search != null && search.isFocused()) {
      var category = category();
      if (category != null) {
        var matches =
            nodes().stream()
                .filter(
                    node ->
                        category
                            .getConfig()
                            .getDefinitionById(node.definitionId())
                            .orElseThrow()
                            .title()
                            .getString()
                            .toLowerCase(Locale.ROOT)
                            .contains(search.getValue().toLowerCase(Locale.ROOT)))
                .toList();
        if (!matches.isEmpty()) {
          int current = -1;
          for (int index = 0; index < matches.size(); index++) {
            if (matches.get(index).id().equals(selected)) current = index;
          }
          var node = matches.get((current + 1) % matches.size());
          selected = node.id();
          camera.center(node.x(), node.y());
          detailsScroll.reset();
        }
      }
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  private enum Page {
    COMBAT("combat", "combat"),
    MAGIC("magic", "combat"),
    PROFESSION("profession", "profession");
    final String id;
    final String category;

    Page(String id, String category) {
      this.id = id;
      this.category = category;
    }

    boolean accepts(String skill) {
      return this == PROFESSION || skill.startsWith("magic_") == (this == MAGIC);
    }
  }
}
