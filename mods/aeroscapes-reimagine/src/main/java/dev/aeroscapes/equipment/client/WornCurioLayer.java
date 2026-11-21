package dev.aeroscapes.equipment.client;

import static java.util.Map.entry;

import com.mojang.blaze3d.vertex.PoseStack;
import io.redspace.ironsspellbooks.api.item.curios.AffinityData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import java.util.Map;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WornCurioLayer
    extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
  private static final RenderType MATERIAL =
      RenderType.entityCutoutNoCull(
          ResourceLocation.parse("aeroscapes_reimagine:textures/entity/white.png"));
  private static final Look AFFINITY = new Look(Shape.GEM_RING, 0x977053, 0xbcb3a3);
  private static final Map<String, Look> SPELLS =
      Map.ofEntries(
          entry("mana_ring", new Look(Shape.GEM_RING, 0x788b92, 0x4ee0d6)),
          entry("cooldown_ring", new Look(Shape.GEM_RING, 0xc1784d, 0xa2dd7d)),
          entry("cast_time_ring", new Look(Shape.GEM_RING, 0xa95d49, 0xbb93dc)),
          entry("silver_ring", new Look(Shape.BAND, 0xaabecb, 0x4f6f99)),
          entry("emerald_stoneplate_ring", new Look(Shape.GEM_RING, 0xd29636, 0x2fdc64)),
          entry("fireward_ring", new Look(Shape.GEM_RING, 0xc48432, 0xdc4035)),
          entry("frostward_ring", new Look(Shape.GEM_RING, 0x77949c, 0xb5c9f1)),
          entry("poisonward_ring", new Look(Shape.GEM_RING, 0x62633c, 0xb1d744)),
          entry("affinity_ring", AFFINITY),
          entry("lurker_ring", new Look(Shape.GEM_RING, 0x978064, 0xa52b46)),
          entry("expulsion_ring", new Look(Shape.GEM_RING, 0x479f94, 0xb49cd3)),
          entry("visibility_ring", new Look(Shape.BAND, 0xd89829, 0xffdb70)),
          entry("betrayer_signet", new Look(Shape.BAND, 0x5f545d, 0x93818d)),
          entry("wicked_bone_ring", new Look(Shape.BAND, 0xb9b3a1, 0xece1ca)),
          entry("enchanted_ward_amulet", new Look(Shape.PENDANT, 0x343f51, 0xbdeceb)),
          entry("heavy_chain_necklace", new Look(Shape.CHAIN, 0x4b5261, 0x687485)),
          entry("conjurers_talisman", new Look(Shape.TALISMAN, 0x4c3637, 0xe9dfc7)),
          entry("greater_conjurers_talisman", new Look(Shape.TALISMAN, 0x612039, 0xf29bb7)),
          entry("concentration_amulet", new Look(Shape.PENDANT, 0x919da8, 0xa5c0d9)),
          entry("amethyst_resonance_charm", new Look(Shape.PENDANT, 0x70513e, 0xbb87ec)),
          entry("teleportation_amulet", new Look(Shape.PENDANT, 0x364456, 0x49c4ad)));

  private static final Map<String, Look> SURVIVAL =
      Map.of(
          "heat_resistance_ring", new Look(Shape.GEM_RING, 0xbd7448, 0xe9422b),
          "cold_resistance_ring", new Look(Shape.GEM_RING, 0xbd7448, 0x43cfe8),
          "thermal_resistance_ring", new Look(Shape.GEM_RING, 0xbd7448, 0x995cdb),
          "nether_chalice", new Look(Shape.CHALICE, 0xc48c32, 0x932b22),
          "thermometer", new Look(Shape.THERMOMETER, 0x9eb9bb, 0x537ec4));
  private static final Look PEARLS = new Look(Shape.PEARLS, 0xb0ceda, 0xf1f3da);
  private static final Look KEY = new Look(Shape.KEY, 0xca9431, 0xf0cc65);
  private static final Look TERMINAL = new Look(Shape.TERMINAL, 0x48454a, 0x8b878c);

  private final Map<Shape, ModelPart> models = WornCurioModels.create();

  enum Shape {
    GEM_RING,
    BAND,
    PENDANT,
    TALISMAN,
    CHAIN,
    PEARLS,
    CHALICE,
    KEY,
    THERMOMETER,
    TERMINAL
  }

  private record Look(Shape shape, int metal, int detail) {}

  private WornCurioLayer(PlayerRenderer renderer) {
    super(renderer);
  }

  @SubscribeEvent
  public static void addLayer(EntityRenderersEvent.AddLayers event) {
    if (!ModList.get().isLoaded("curios")) return;
    for (var skin : event.getSkins())
      if (event.getSkin(skin) instanceof PlayerRenderer renderer)
        renderer.addLayer(new WornCurioLayer(renderer));
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      AbstractClientPlayer player,
      float limbSwing,
      float limbAmount,
      float partialTick,
      float age,
      float yaw,
      float pitch) {
    if (player.isInvisible()) return;
    var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
    if (inventory == null) return;
    int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
    inventory
        .getStacksHandler("ring")
        .ifPresent(slot -> renderSlot(slot, "ring", player, pose, buffers, light, overlay));
    inventory
        .getStacksHandler("necklace")
        .ifPresent(slot -> renderSlot(slot, "necklace", player, pose, buffers, light, overlay));
    inventory
        .getStacksHandler("belt")
        .ifPresent(slot -> renderSlot(slot, "belt", player, pose, buffers, light, overlay));
  }

  private void renderSlot(
      ICurioStacksHandler slot,
      String identifier,
      AbstractClientPlayer player,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var stacks = slot.getStacks();
    var cosmetics = slot.getCosmeticStacks();
    var rendered = slot.getRenders();
    for (int index = 0; index < stacks.getSlots(); index++) {
      // Curios gives a cosmetic stack precedence over the equipment and its visibility toggle.
      ItemStack stack = cosmetics.getStackInSlot(index);
      if (stack.isEmpty() && index < rendered.size() && rendered.get(index))
        stack = stacks.getStackInSlot(index);
      if (stack.isEmpty() || CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent())
        continue;
      var look = look(BuiltInRegistries.ITEM.getKey(stack.getItem()));
      if (look != null)
        renderLook(
            look,
            detailColor(look, stack),
            identifier,
            index,
            player,
            pose,
            buffers,
            light,
            overlay);
    }
  }

  private static int detailColor(Look look, ItemStack stack) {
    if (look == AFFINITY && AffinityData.hasAffinityData(stack)) {
      var affinities = AffinityData.getAffinityData(stack).affinityData().keySet().iterator();
      if (!affinities.hasNext()) return look.detail();
      var spell = SpellRegistry.getSpell(affinities.next());
      if (spell != SpellRegistry.none()) {
        var color = spell.getSchoolType().getDisplayName().getStyle().getColor();
        if (color != null) return color.getValue();
      }
    }
    return look.detail();
  }

  private static Look look(ResourceLocation id) {
    String path = id.getPath();
    return switch (id.getNamespace()) {
      case "irons_spellbooks" -> SPELLS.get(path);
      case "legendarysurvivaloverhaul" -> SURVIVAL.get(path);
      case "crittersandcompanions" ->
          switch (path) {
            case "pearl_necklace_1", "pearl_necklace_2", "pearl_necklace_3" -> PEARLS;
            default -> null;
          };
      case "supplementaries" -> path.equals("key") ? KEY : null;
      case "toms_storage" -> path.equals("adv_wireless_terminal") ? TERMINAL : null;
      default -> null;
    };
  }

  private void renderLook(
      Look look,
      int detailColor,
      String identifier,
      int index,
      AbstractClientPlayer player,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var model = getParentModel();
    boolean ring = identifier.equals("ring");
    boolean right = index % 2 == 0;
    var anchor = ring ? (right ? model.rightArm : model.leftArm) : model.body;
    if (!anchor.visible) return;
    pose.pushPose();
    try {
      anchor.translateAndRotate(pose);
      if (ring) {
        boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
        float armCenter = (right ? -1 : 1) * (slim ? 0.5f : 1);
        pose.translate((armCenter + index / 2 % 3 - 0.4f) / 16, (9.1f - index / 6) / 16, 0);
      } else if (identifier.equals("belt")) {
        boolean armored =
            !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        pose.translate((right ? -2.8f : 2.8f) / 16, 10.5f / 16, (armored ? -3.35f : -2.5f) / 16);
      } else {
        boolean armored = !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        pose.scale(1, 1, armored ? 1.38f : 1);
      }
      var parts = models.get(look.shape());
      var material = buffers.getBuffer(MATERIAL);
      parts.getChild("base").render(pose, material, light, overlay, 0xff000000 | look.metal());
      parts.getChild("detail").render(pose, material, light, overlay, 0xff000000 | detailColor);
    } finally {
      pose.popPose();
    }
  }
}
