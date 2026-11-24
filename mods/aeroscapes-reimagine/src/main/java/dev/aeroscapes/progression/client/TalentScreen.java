package dev.aeroscapes.progression.client;

import dev.aeroscapes.inventory.InventoryNavigation;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.client.AbilityClient;
import dev.aeroscapes.mixin.progression.SkillsScreenAccessor;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.races.ChangeNetwork;
import dev.aeroscapes.races.client.ChangeClient;
import dev.aeroscapes.races.client.ChangeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
public final class TalentScreen extends Screen implements ChangeView {
  private static final int TEXT = 0xffd6e0eb;
  private static final int MUTED = 0xff92a9c3;
  private static final int GOLD = 0xffe9c084;
  private static final int ROUTE = 0xff75dfda;
  private static final int AFFORDABLE = 0xffc5fa77;
  private final ClientSkillScreenData data;
  private final long changeSession;
  private final Map<String, Set<String>> refunds = new HashMap<>();
  private Button review;
  private final TreeViewport camera = new TreeViewport();
  private final ScrollViewport detailsScroll = new ScrollViewport();
  private final List<Button> tabs = new ArrayList<>();
  private final Map<Page, List<Button>> subtabs = new EnumMap<>(Page.class);
  private final Map<Page, String> selectedSubtabs = new EnumMap<>(Page.class);
  private final Set<String> searchMatches = new HashSet<>();
  private Page page = Page.COMBAT;
  private String selected;
  private EditBox search;
  private Button fitButton;
  private Button learn;
  private ClientCategoryData fittedCategory;
  private Map<String, MagicTalentPath.Node> magicGraph = Map.of();
  private String pathGoal;
  private Set<String> pathOwned = Set.of();
  private MagicTalentPath path;
  private boolean dragging;
  private double pressX;
  private double pressY;
  private double travel;
  private boolean detailsDragging;
  private double detailsGrab;

  public TalentScreen(ClientSkillScreenData data) {
    this(data, 0);
  }

  private TalentScreen(ClientSkillScreenData data, long session) {
    super(session == 0 ? text("talents_button") : ChangeClient.text("refund"));
    this.data = data;
    this.changeSession = session;
  }

  @Override
  public long changeSession() {
    return changeSession;
  }

  private Set<String> refundSelection() {
    return refunds.computeIfAbsent(categoryPath(), ignored -> new HashSet<>());
  }

  @SubscribeEvent
  public static void replaceProviderScreen(ScreenEvent.Opening event) {
    if (event.getNewScreen() instanceof SkillsScreen nativeScreen) {
      event.setNewScreen(
          new TalentScreen(
              ((SkillsScreenAccessor) nativeScreen).aeroscapes$screenData(),
              ChangeClient.pendingTree()));
    }
  }

  private static Component text(String key, Object... arguments) {
    return Component.translatable("aeroscapes_reimagine.progression." + key, arguments);
  }

  @Override
  protected void init() {
    AbilityClient.request();
    tabs.clear();
    subtabs.clear();
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
                    selectTree();
                  })
              .bounds(16 + tab.ordinal() * (tabWidth + 4), 39, tabWidth, 20)
              .build();
      addRenderableWidget(button);
      tabs.add(button);
      addSubtabs(tab);
    }
    search =
        new EditBox(
            font, 20, controlsTop() + 2, Math.max(34, canvasWidth() - 74), 16, text("search"));
    search.setHint(text("search"));
    search.setResponder(this::updateSearch);
    addRenderableWidget(search);
    fitButton =
        addRenderableWidget(
            Button.builder(text("fit"), button -> fit())
                .bounds(24 + search.getWidth(), controlsTop(), 62, 20)
                .build());
    updateTabs();
    addRenderableWidget(
        Button.builder(Component.literal("×"), button -> onClose())
            .bounds(width - 33, 13, 17, 18)
            .build());
    learn =
        addRenderableWidget(
            Button.builder(
                    text("learn"),
                    button -> {
                      if (changeSession == 0) purchase();
                      else if (selected != null && !refundSelection().remove(selected))
                        refundSelection().add(selected);
                    })
                .bounds(inspectorX() + 8, height - 57, 140, 20)
                .build());
    if (changeSession != 0) {
      review =
          addRenderableWidget(
              Button.builder(
                      ChangeClient.text("preview"),
                      button -> {
                        var nodes = refundSelection().stream().sorted().toList();
                        ChangeClient.preview(
                            this,
                            new ChangeNetwork.Request(
                                changeSession, categoryPath(), "", 0, "", false, nodes),
                            false,
                            ChangeClient.text("calculating"));
                      })
                  .bounds(inspectorX() + 8, height - 30, 140, 18)
                  .build());
    }
    fittedCategory = null;
    detailsScroll.reset();
  }

  private void addSubtabs(Page parent) {
    var categories = parent.subcategories;
    if (categories.isEmpty()) return;
    int buttonWidth = (width - 32 - 4 * (categories.size() - 1)) / categories.size();
    var buttons = new ArrayList<Button>();
    for (String category : categories) {
      var title =
          Component.translatable("puffish_skills.aeroscapes_reimagine." + category + ".title");
      var label =
          font.width(title) <= buttonWidth - 8 ? title : text("page." + category + "_short");
      int x = 16 + buttons.size() * (buttonWidth + 4);
      buttons.add(
          addRenderableWidget(
              Button.builder(
                      label,
                      button -> {
                        selectedSubtabs.put(parent, category);
                        selectTree();
                      })
                  .bounds(x, 64, buttonWidth, 20)
                  .tooltip(Tooltip.create(title))
                  .build()));
    }
    subtabs.put(parent, buttons);
  }

  private void updateTabs() {
    for (int index = 0; index < tabs.size(); index++)
      tabs.get(index).active = index != page.ordinal();
    subtabs.forEach(
        (parent, buttons) -> {
          for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            button.visible = parent == page;
            button.active =
                button.visible && !parent.subcategories.get(index).equals(categoryPath());
          }
        });
  }

  private String categoryPath() {
    return page.subcategories.isEmpty()
        ? page.id
        : selectedSubtabs.getOrDefault(page, page.subcategories.getFirst());
  }

  private void selectTree() {
    selected = null;
    detailsScroll.reset();
    fittedCategory = null;
    search.setValue("");
    search.setY(controlsTop() + 2);
    fitButton.setY(controlsTop());
    updateTabs();
  }

  private ClientCategoryData category() {
    return data.getCategory(ResourceLocation.parse("aeroscapes_reimagine:" + categoryPath()))
        .orElse(null);
  }

  private List<ClientSkillConfig> nodes() {
    var category = category();
    return category == null
        ? List.of()
        : category.getConfig().skills().values().stream()
            .filter(node -> category.getConfig().getDefinitionById(node.definitionId()).isPresent())
            .sorted(Comparator.comparing(ClientSkillConfig::id))
            .toList();
  }

  private int canvasWidth() {
    return Math.max(80, width - 204);
  }

  private int canvasHeight() {
    return Math.max(60, height - canvasTop() - 38);
  }

  private int controlsTop() {
    return page.subcategories.isEmpty() ? 66 : 90;
  }

  private int canvasTop() {
    return controlsTop() + 28;
  }

  private int detailsTop() {
    return canvasTop() + 11;
  }

  private int inspectorX() {
    return width - 176;
  }

  private boolean inside(double x, double y) {
    return x >= 16
        && x < 16 + canvasWidth()
        && y >= canvasTop()
        && y < canvasTop() + canvasHeight();
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

  private void showMagicRoot(ClientCategoryData category) {
    var root = nodes().stream().filter(ClientSkillConfig::isRoot).findFirst().orElse(null);
    if (root == null) return;
    var config = category.getConfig();
    int reachX = 0, reachY = 0;
    for (String id : config.skillNormalNeighbors().getOrDefault(root.id(), List.of())) {
      var entry = config.skills().get(id);
      if (entry != null) {
        reachX = Math.max(reachX, Math.abs(entry.x() - root.x()));
        reachY = Math.max(reachY, Math.abs(entry.y() - root.y()));
      }
    }
    camera.showRoot(root.x(), root.y(), reachX, reachY, canvasWidth(), canvasHeight());
  }

  private void updateSearch(String value) {
    searchMatches.clear();
    String query = value.strip().toLowerCase(Locale.ROOT);
    var category = category();
    if (query.isEmpty() || category == null) return;
    for (var node : nodes()) {
      var title = category.getConfig().getDefinitionById(node.definitionId()).orElseThrow().title();
      if (title.getString().toLowerCase(Locale.ROOT).contains(query)) searchMatches.add(node.id());
    }
    if (page == Page.MAGIC) {
      for (var spell : SpellKnowledge.spells()) {
        var route = MagicTalents.spell(spell.getSpellId());
        if (route != null
            && spell
                .getDisplayName(minecraft.player)
                .getString()
                .toLowerCase(Locale.ROOT)
                .contains(query)) {
          searchMatches.add(route.node());
        }
      }
    }
  }

  private void loadMagicGraph(ClientCategoryData category) {
    pathGoal = null;
    path = null;
    if (page != Page.MAGIC || category == null) {
      magicGraph = Map.of();
      return;
    }
    var graph = new HashMap<String, MagicTalentPath.Node>();
    var config = category.getConfig();
    for (var node : nodes()) {
      var definition = config.getDefinitionById(node.definitionId()).orElseThrow();
      graph.put(
          node.id(),
          new MagicTalentPath.Node(
              definition.cost(),
              MagicTalents.requiredLevel(node.id()),
              node.isRoot(),
              List.copyOf(config.skillNormalNeighborsReversed().getOrDefault(node.id(), List.of())),
              MagicTalents.priorMasteries(node.id())));
    }
    magicGraph = graph;
  }

  private void updatePath(ClientCategoryData category) {
    if (selected == null || magicGraph.isEmpty() || category == null) {
      pathGoal = null;
      path = null;
      return;
    }
    var owned = new HashSet<String>();
    for (String id : magicGraph.keySet()) {
      if (category.getSkillState(category.getConfig().skills().get(id)) == Skill.State.UNLOCKED) {
        owned.add(id);
      }
    }
    if (!Objects.equals(pathGoal, selected) || !pathOwned.equals(owned)) {
      pathGoal = selected;
      pathOwned = owned;
      path = MagicTalentPath.find(magicGraph, owned, selected);
    }
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
    for (var button : subtabs.getOrDefault(page, List.of()))
      if (!button.active)
        graphics.renderOutline(
            button.getX() - 1,
            button.getY() - 1,
            button.getWidth() + 2,
            button.getHeight() + 2,
            GOLD);
    var category = category();
    if (category != fittedCategory) {
      fittedCategory = category;
      fit();
      if (page == Page.MAGIC && category != null) {
        var node = selected == null ? null : category.getConfig().skills().get(selected);
        if (node == null) showMagicRoot(category);
        else camera.focus(node.x(), node.y());
      }
      updateSearch(search.getValue());
      loadMagicGraph(category);
    }
    updatePath(category);
    if (category != null) {
      int balance = category.getPointsLeft();
      var points = balance < 0 ? text("points_due", -balance) : text("points", balance);
      graphics.drawString(font, points, width - 45 - font.width(points), 19, GOLD, false);
      if (page != Page.PROFESSION)
        graphics.drawString(
            font, text(page.id + "_budget"), inspectorX() + 5, controlsTop() + 3, MUTED, false);
    }
    int top = canvasTop();
    AtlasProgressionWidgets.panel(graphics, 16, top, canvasWidth(), canvasHeight());
    AtlasProgressionWidgets.panel(graphics, inspectorX(), top, 156, height - top - 32);
    graphics.enableScissor(20, top + 4, 12 + canvasWidth(), top - 4 + canvasHeight());
    if (category != null) {
      var hoveredNode = inside(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
      graphics.pose().pushPose();
      graphics
          .pose()
          .translate(
              16 + canvasWidth() / 2.0 + camera.x(0), top + canvasHeight() / 2.0 + camera.y(0), 0);
      graphics.pose().scale((float) camera.scale(), (float) camera.scale(), 1);
      drawConnections(graphics, category, category.getConfig().normalConnections(), false);
      drawConnections(graphics, category, category.getConfig().exclusiveConnections(), true);
      for (var node : nodes()) drawNode(graphics, category, node, node == hoveredNode);
      graphics.pose().popPose();
    }
    graphics.disableScissor();
    drawDetails(graphics, category);
    if (changeSession == 0)
      graphics.drawString(font, text("navigation"), 20, height - 24, MUTED, false);
    else {
      review.active = !refundSelection().isEmpty();
      review.setMessage(ChangeClient.text("preview_count", refundSelection().size()));
    }
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
                  if (MagicTalents.nodes().contains(hovered.id()))
                    tooltip.add(magicRequirement(hovered));
                  tooltip.add(stateText(category, hovered));
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
      if (a == null || b == null) continue;
      boolean unlocked =
          state(category, a) == Skill.State.UNLOCKED && state(category, b) == Skill.State.UNLOCKED;
      int color = exclusive ? 0xffbd806d : unlocked ? GOLD : 0xff627b96;
      boolean onPath =
          !exclusive
              && path != null
              && path.edges().contains(new MagicTalentPath.Edge(a.id(), b.id()));
      line(graphics, a.x(), a.y(), b.x(), b.y(), onPath ? ROUTE : color, exclusive, onPath);
    }
  }

  private static void line(
      GuiGraphics graphics,
      int x1,
      int y1,
      int x2,
      int y2,
      int color,
      boolean dashed,
      boolean onPath) {
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
      graphics.fill(0, onPath ? -2 : -1, length, onPath ? 3 : 2, 0xff111b29);
      graphics.fill(0, onPath ? -1 : 0, length, onPath ? 2 : 1, color);
    }
    graphics.pose().popPose();
  }

  private void drawNode(
      GuiGraphics graphics, ClientCategoryData category, ClientSkillConfig node, boolean hovered) {
    var definition = category.getConfig().getDefinitionById(node.definitionId()).orElse(null);
    if (definition == null) return;
    int x = node.x(), y = node.y();
    int radius = TreeViewport.NODE_RADIUS;
    Skill.State state = state(category, node);
    boolean onPath = path != null && path.unpaid().contains(node.id());
    boolean marked = changeSession != 0 && refundSelection().contains(node.id());
    if (node.id().equals(selected) || searchMatches.contains(node.id()) || onPath || marked) {
      int outer = TreeViewport.NODE_OUTER_RADIUS;
      int color = marked ? 0xffed9d7a : onPath && !node.id().equals(selected) ? ROUTE : GOLD;
      graphics.fill(x - outer, y - outer, x + outer, y + outer, color);
      graphics.fill(x - outer + 1, y - outer + 1, x + outer - 1, y + outer - 1, 0xff111b29);
    }
    ResourceLocation frame =
        ResourceLocation.parse(
            "aeroscapes_reimagine:textures/gui/skills/frame_"
                + state.name().toLowerCase(Locale.ROOT)
                + ".png");
    graphics.blit(frame, x - radius, y - radius, radius * 2, radius * 2, 0, 0, 26, 26, 26, 26);
    if (state == Skill.State.AFFORDABLE && changeSession == 0) {
      graphics.renderOutline(x - radius, y - radius, radius * 2, radius * 2, AFFORDABLE);
      graphics.renderOutline(
          x - radius + 1, y - radius + 1, radius * 2 - 2, radius * 2 - 2, 0xff40652a);
    }
    graphics.pose().pushPose();
    graphics.pose().translate(x - 8, y - 8, 0);
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
    if (state == Skill.State.UNLOCKED) {
      graphics.pose().pushPose();
      graphics.pose().translate(0, 0, 200);
      graphics.fill(x + 2, y + 2, x + radius, y + radius, 0xff162331);
      graphics.drawString(font, "✓", x + 4, y + 3, GOLD, false);
      graphics.pose().popPose();
    }
    if (hovered)
      graphics.renderOutline(x - radius - 1, y - radius - 1, radius * 2 + 2, radius * 2 + 2, GOLD);
  }

  private Skill.State state(ClientCategoryData category, ClientSkillConfig node) {
    Skill.State nativeState = category.getSkillState(node);
    return nativeState != Skill.State.UNLOCKED
            && (MagicTalents.requiredLevel(node.id()) > AbilityClient.snapshot().practiceLevel()
                || missingPriorMastery(category, node) != null)
        ? Skill.State.LOCKED
        : nativeState;
  }

  private static String missingPriorMastery(ClientCategoryData category, ClientSkillConfig node) {
    return MagicTalents.missingPriorMastery(
        node.id(),
        id -> {
          var prior = category.getConfig().skills().get(id);
          return prior != null && category.getSkillState(prior) == Skill.State.UNLOCKED;
        });
  }

  private Component stateText(ClientCategoryData category, ClientSkillConfig node) {
    Skill.State nativeState = category.getSkillState(node);
    if (nativeState != Skill.State.UNLOCKED) {
      if (MagicTalents.requiredLevel(node.id()) > AbilityClient.snapshot().practiceLevel())
        return text("magic.level_locked");
      String missing = missingPriorMastery(category, node);
      if (missing != null) return SpellKnowledge.requiredMasteryMessage(missing);
    }
    return text("state." + nativeState.name().toLowerCase(Locale.ROOT));
  }

  private Component magicRequirement(ClientSkillConfig node) {
    if (!AbilityClient.hasSnapshot())
      return text("magic.required_level", MagicTalents.requiredLevel(node.id()));
    return text(
        "magic.requirement",
        AbilityClient.snapshot().practiceLevel(),
        MagicTalents.requiredLevel(node.id()));
  }

  private int magicProgress(GuiGraphics graphics, int x, int y) {
    if (!AbilityClient.hasSnapshot())
      return paragraph(graphics, text("magic.waiting"), x, y, MUTED);
    var snapshot = AbilityClient.snapshot();
    y = paragraph(graphics, text("magic.level", snapshot.practiceLevel()), x, y, GOLD);
    y = paragraph(graphics, text("magic.total_xp", snapshot.practiceXp()), x, y + 3, MUTED);
    return paragraph(
        graphics,
        snapshot.nextTalentXp() > 0
            ? text(
                "magic.next_talent", Math.max(0, snapshot.nextTalentXp() - snapshot.practiceXp()))
            : text("magic.talent_cap"),
        x,
        y + 3,
        MUTED);
  }

  private int pathDetails(GuiGraphics graphics, ClientCategoryData category, int x, int y) {
    if (path == null) return paragraph(graphics, text("magic.path_unavailable"), x, y, MUTED);
    if (path.unpaid().isEmpty()) return y;
    y =
        paragraph(
            graphics,
            text(
                "magic.path_points",
                path.cost(),
                Math.max(0, path.cost() - category.getPointsLeft())),
            x,
            y,
            ROUTE);
    var requirement =
        AbilityClient.hasSnapshot()
            ? text(
                "magic.path_practice",
                path.practice(),
                Math.max(0, path.practice() - AbilityClient.snapshot().practiceLevel()))
            : text("magic.required_level", path.practice());
    return paragraph(graphics, requirement, x, y + 3, MUTED);
  }

  private void drawDetails(GuiGraphics graphics, ClientCategoryData category) {
    learn.active = false;
    int top = detailsTop();
    if (changeSession != 0)
      learn.setMessage(ChangeClient.text(refundSelection().contains(selected) ? "unmark" : "mark"));
    if (category == null) {
      graphics.drawWordWrap(font, text("waiting"), inspectorX() + 9, top, 138, TEXT);
      return;
    }
    var node = selected == null ? null : category.getConfig().skills().get(selected);
    int x = inspectorX() + 9, y = top - (int) detailsScroll.offset();
    graphics.enableScissor(x, top, x + 138, height - 65);
    if (node == null) {
      if (page == Page.MAGIC) {
        y = magicProgress(graphics, x, y);
        y = paragraph(graphics, text("magic.explanation"), x, y + 7, TEXT) + 7;
      } else if (categoryPath().equals(ProfessionDiscipline.ENCHANTING.skill())) {
        y = paragraph(graphics, category.getConfig().description(), x, y, TEXT) + 7;
      }
      y = paragraph(graphics, text("select"), x, y, TEXT);
    } else {
      var definition = category.getConfig().getDefinitionById(node.definitionId()).orElse(null);
      if (definition != null) {
        y = paragraph(graphics, definition.title(), x, y, GOLD);
        if (page == Page.MAGIC) y = pathDetails(graphics, category, x, y + 4);
        y = paragraph(graphics, definition.description(), x, y + 4, TEXT);
        y = paragraph(graphics, text("cost", definition.cost()), x, y + 6, MUTED);
        y = paragraph(graphics, stateText(category, node), x, y + 3, MUTED);
        if (page == Page.MAGIC) y = magicProgress(graphics, x, y + 8);
        var exclusions =
            category.getConfig().skillExclusiveNeighbors().getOrDefault(node.id(), List.of());
        for (String id : exclusions) {
          var other = category.getConfig().skills().get(id);
          if (other != null) {
            var otherDefinition =
                category.getConfig().getDefinitionById(other.definitionId()).orElse(null);
            if (otherDefinition == null) continue;
            y =
                paragraph(
                    graphics, text("excludes", otherDefinition.title()), x, y + 6, 0xffd49a83);
          }
        }
        learn.active =
            state(category, node)
                == (changeSession == 0 ? Skill.State.AFFORDABLE : Skill.State.UNLOCKED);
      }
    }
    graphics.disableScissor();
    detailsScroll.resize(y + (int) detailsScroll.offset() - top, Math.max(1, height - 65 - top));
    if (detailsScroll.maximum() > 0) {
      int thumbTop = top + detailsScroll.thumbPosition();
      graphics.fill(width - 26, top, width - 23, height - 65, 0xff111b29);
      graphics.fill(width - 26, thumbTop, width - 23, thumbTop + detailsScroll.thumbSize(), MUTED);
    }
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
    var node = category.getConfig().skills().get(selected);
    if (node == null || state(category, node) != Skill.State.AFFORDABLE) return;
    SkillsClientMod.getInstance()
        .getPacketSender()
        .send(new SkillClickOutPacket(category.getConfig().id(), selected));
  }

  private ClientSkillConfig nodeAt(double x, double y) {
    double offsetX = x - 16 - canvasWidth() / 2.0;
    double offsetY = y - canvasTop() - canvasHeight() / 2.0;
    return nodes().reversed().stream()
        .filter(node -> camera.hits(node.x(), node.y(), offsetX, offsetY))
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (width < 320 || height < 220) return super.mouseClicked(x, y, button);
    if (button == 0
        && x >= width - 29
        && x < width - 20
        && y >= detailsTop()
        && y < height - 65
        && detailsScroll.maximum() > 0) {
      detailsDragging = true;
      double relative = y - detailsTop() - detailsScroll.thumbPosition();
      detailsGrab =
          relative >= 0 && relative < detailsScroll.thumbSize()
              ? relative
              : detailsScroll.thumbSize() / 2.0;
      detailsScroll.drag(y - detailsTop() - detailsGrab);
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
      detailsScroll.drag(y - detailsTop() - detailsGrab);
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
    if (x >= inspectorX() && x < width - 20 && y >= detailsTop() && y < height - 65) {
      detailsScroll.wheel(vertical);
      return true;
    }
    if (!inside(x, y)) return super.mouseScrolled(x, y, horizontal, vertical);
    camera.zoom(vertical, x - 16 - canvasWidth() / 2.0, y - canvasTop() - canvasHeight() / 2.0);
    return true;
  }

  @Override
  public void onClose() {
    if (changeSession == 0) InventoryNavigation.openInventory();
    else {
      ChangeClient.cancel(changeSession);
      minecraft.setScreen(null);
    }
  }

  @Override
  public boolean isPauseScreen() {
    return changeSession == 0 && super.isPauseScreen();
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
                .filter(node -> search.getValue().isBlank() || searchMatches.contains(node.id()))
                .toList();
        if (!matches.isEmpty()) {
          int current = -1;
          for (int index = 0; index < matches.size(); index++) {
            if (matches.get(index).id().equals(selected)) current = index;
          }
          var node = matches.get((current + 1) % matches.size());
          selected = node.id();
          camera.focus(node.x(), node.y());
          detailsScroll.reset();
        }
      }
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  private enum Page {
    COMBAT("combat"),
    MAGIC("magic"),
    PROFESSION(
        "profession",
        Arrays.stream(ProfessionDiscipline.values()).map(ProfessionDiscipline::skill).toList());
    final String id;
    final List<String> subcategories;

    Page(String id) {
      this(id, List.of());
    }

    Page(String id, List<String> subcategories) {
      this.id = id;
      this.subcategories = subcategories;
    }
  }
}
