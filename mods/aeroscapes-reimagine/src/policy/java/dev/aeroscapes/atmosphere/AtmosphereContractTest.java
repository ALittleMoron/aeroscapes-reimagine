package dev.aeroscapes.atmosphere;

import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationJson;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.jem_objects.EMFPartData;

/** Checks the provider boundaries used by client adapters without starting Minecraft. */
public final class AtmosphereContractTest {
  private AtmosphereContractTest() {}

  private static void oreInformationSideGate() {
    Map<String, String> server =
        Map.of(
            "emi_ores", "1.3+1.21.1+neoforge",
            "create", "6.0.10",
            "neoforge", "21.1.244");
    require(
        AtmosphereMixinPlugin.shouldApplyForVersions(
            "dev.aeroscapes.mixin.emi.EmiOresSyncMixin", server::get),
        "Dedicated ore synchronization does not require a client recipe viewer");
    require(
        !AtmosphereMixinPlugin.shouldApplyForVersions(
            "dev.aeroscapes.mixin.emi.EmiOresRecipeMixin", server::get),
        "Client ore widgets retain the recipe-viewer gate");
  }

  private static void miscellaneousTranslationFallbacks() {
    String key = "immersive_weathering.configuration.general.creative_tab";
    Map<String, String> player = Map.of(key, "Owner's custom caption");
    List<String> russian = List.of("en_us", "ru_ru");
    var translated = MiscTranslations.apply(player, russian);
    require(translated.get(key).equals(player.get(key)), "Player configuration captions win");
    require(player.size() == 1, "Translation input is not mutated");
    String note = "aeroscapes_reimagine.ore_information.attempts";
    require(
        translated.containsKey(note), "Missing native/configuration captions receive fallbacks");
    require(
        !MiscTranslations.apply(Map.of(), List.of("en_us")).get(note).equals(translated.get(note)),
        "English and Russian fallback captions differ");
    require(
        MiscTranslations.apply(translated, russian).equals(translated),
        "Missing-key fallback is reload-idempotent");
  }

  public static void main(String[] arguments) throws IOException {
    swordCastPresentation();
    sandpaperRenderer();
    bridgedPlacementVersionGate();
    oreInformationSideGate();
    miscellaneousTranslationFallbacks();
    constructionShapeVersionGate();
    quiverComposition();
    fastRodRenderers();
    lanternGrip();
    rainImpactBiome();
    languageCorrections();
    ConfigLiteralNamespaceTest.verify();
    catnipConfigTranslations();
    contextualConfigComments();
    contextualConfigEnums();
    runtimeConfigMetadata();
    nativeConfigurationTranslations();
    puzzlesConfigurationTranslations();
    inventoryMovementCompatibility();
    creativeSearchContract();
    schematicMessages();
    distantSceneryCompatibility();
    IcebergConfigBridgeTest.verify();
    AdvancementCriterionTranslationsTest.verify();
    ClassNode attachment = provider("punchy/client/render/FishingLineAttachment");
    method(
        attachment,
        "beginWorld",
        "(Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
    method(
        attachment,
        "capture",
        "(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;Lorg/joml/Matrix4f;)V");
    method(
        attachment,
        "captureModelTip",
        "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/entity/HumanoidArm;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;)V");
    method(
        provider("punchy/client/render/FishingLineProjection"),
        "projectTip",
        "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;");
    for (String id : List.of("rain", "heavy_rain", "snow", "heavy_snow")) {
      require(PrecipitationPolicy.isAirbornePrecipitation(id, true), "sky precipitation opts out");
      require(!PrecipitationPolicy.isAirbornePrecipitation(id, false), "surface data stays native");
    }
    for (String id : List.of("fog", "dust", "ripple", "splatter", "new_particle")) {
      require(!PrecipitationPolicy.isAirbornePrecipitation(id, true), "other effects stay native");
    }
    require(
        !PrecipitationPolicy.isAirbornePrecipitation(null, true),
        "uninitialized data stays native");

    ClassNode rain = provider("pigcart/particlerain/ParticleRain");
    MethodNode wind = method(rain, "getWind", "(DDD)Lorg/joml/Vector3f;");
    List<AbstractInsnNode> instructions = executable(wind);
    int zeros = 0;
    for (int index = 0; index < instructions.size(); index++) {
      if (instructions.get(index).getOpcode() != Opcodes.FCONST_0) continue;
      zeros++;
      if (zeros == 1) {
        require(instructions.get(index - 1) instanceof JumpInsnNode, "wind ternary join");
        require(
            instructions.get(index + 1).getOpcode() == Opcodes.FSTORE, "altitude multiplier store");
        require(
            instructions.subList(0, index).stream()
                .anyMatch(
                    instruction ->
                        instruction instanceof FieldInsnNode field
                            && field.name.equals("yLevelAdjustment")),
            "native altitude option");
        require(
            instructions.get(index - 2) instanceof MethodInsnNode call
                && call.name.equals("yLevelWindMultiplier")
                && call.desc.equals("(D)F"),
            "native adjusted branch");
      }
    }
    require(zeros == 2, "only the disabled altitude multiplier and vector Y are zero constants");
    ClassNode version = provider("pigcart/particlerain/VersionUtil");
    for (String name : List.of("getMinY", "getMaxY")) {
      require(
          (method(version, name, "(Lnet/minecraft/world/level/Level;)I").access
                  & Opcodes.ACC_STATIC)
              != 0,
          "static height helper");
    }
    ClassNode custom = provider("pigcart/particlerain/particle/CustomParticle");
    method(
        custom,
        "<init>",
        "(Lnet/minecraft/client/multiplayer/ClientLevel;DDDLpigcart/particlerain/config/ParticleData;)V");
    ClassNode kickable = provider("dev/ryanhcode/sable/api/particle/ParticleSubLevelKickable");
    for (String name :
        List.of(
            "sable$shouldCareAboutIntersectingSubLevels",
            "sable$shouldKickFromTracking",
            "sable$shouldCollideWithTrackingSubLevel")) method(kickable, name, "()Z");

    ClassNode arms = provider("punchy/client/render/PunchyArmRenderer");
    String renderDescriptor =
        "(Lnet/minecraft/client/renderer/ItemInHandRenderer;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    MethodNode render = method(arms, "renderFirstPerson", renderDescriptor);
    require((render.access & Opcodes.ACC_STATIC) != 0, "static Punchy entry point");
    require(
        executable(render).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESTATIC
                            && call.owner.equals(arms.name)
                            && call.name.equals("renderFirstPersonInternal")
                            && call.desc.equals(renderDescriptor))
                .count()
            == 1,
        "one scoped Punchy render invocation");
    ClassNode emf =
        provider("traben/entity_model_features/models/animation/state/EMFEntityRenderState");
    method(emf, "manualPlayerState", "()L" + emf.name + ";");
    method(emf, "setIsFirstPersonHand", "(Z)V");
    ClassNode etf = provider("traben/entity_texture_features/features/state/ETFState");
    method(etf, "mount", "(Ltraben/entity_texture_features/features/state/ETFEntityRenderState;)V");
    method(etf, "unMount", "()V");
    method(
        provider("traben/entity_model_features/models/jem_objects/EMFJemData"),
        "prepare",
        "(Ltraben/entity_model_features/utils/EMFDirectoryHandler;Ltraben/entity_model_features/models/EMFModel_ID;)V");
    loadingOverlay();
    System.out.println(
        "Atmosphere: quiver composition, precipitation scope and native API contracts passed");
  }

  private static void swordCastPresentation() throws IOException {
    var nativeCallback =
        method(
            provider("io/redspace/ironsspellbooks/player/ClientSpellCastHelper"),
            "handleClientboundOnClientCast",
            "(Ljava/lang/String;ILio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/spells/ICastData;)V");
    require(
        (nativeCallback.access & Opcodes.ACC_STATIC) != 0,
        "Sword cast packets dispatch statically");
    var clips = new HashSet<net.minecraft.resources.ResourceLocation>();
    for (String spell : List.of("echoing_strikes", "frostbite", "counterspell")) {
      String id = "irons_spellbooks:" + spell;
      require(
          SwordCastPresentation.clip(id, CastSource.SCROLL) == null
              && SwordCastPresentation.clip(id, CastSource.SPELLBOOK) == null,
          "Scrolls and spellbooks retain their native cast motion: " + id);
      var clip = SwordCastPresentation.clip(id, CastSource.SWORD);
      require(clip != null, "Confirmed built-in weapon spell has a clip: " + id);
      clips.add(clip);
    }
    require(clips.size() == 2, "Counterspell retains a distinct release motion");
    for (var clip : clips) {
      String path =
          "/assets/" + clip.getNamespace() + "/player_animations/" + clip.getPath() + ".json";
      try (var stream = AtmosphereContractTest.class.getResourceAsStream(path)) {
        if (stream == null) throw new IOException("Missing cast animation: " + clip);
        List<KeyframeAnimation> loaded =
            AnimationJson.GSON.fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                new TypeToken<List<KeyframeAnimation>>() {}.getType());
        var animation = loaded.getFirst();
        require(
            loaded.size() == 1
                && !animation.isInfinite
                && animation.getLength() >= 7
                && animation.getPart("rightArm").pitch.getKeyFrames().size() >= 3,
            "PlayerAnimator decodes a finite cast and recovery: " + clip);
      }
    }
  }

  private static void sandpaperRenderer() throws IOException {
    ClassNode renderer =
        classpathClass("com/simibubi/create/content/equipment/sandPaper/SandPaperItemRenderer");
    MethodNode render =
        method(
            renderer,
            "render",
            "(Lnet/minecraft/world/item/ItemStack;Lcom/simibubi/create/foundation/item/render/CustomRenderedItemModel;Lcom/simibubi/create/foundation/item/render/PartialItemModelRenderer;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V");
    long bodyCalls =
        executable(render).stream()
            .filter(
                instruction ->
                    instruction instanceof MethodInsnNode call
                        && call.owner.equals("net/minecraft/client/renderer/entity/ItemRenderer")
                        && call.name.equals("render")
                        && call.desc.equals(
                            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))
            .count();
    require(
        bodyCalls == 1,
        "The sandpaper adapter targets only the native body draw, retaining the polishing overlay");
    Map<String, String> versions = Map.of("create", "6.0.10", "neoforge", "21.1.244");
    require(
        AtmosphereMixinPlugin.shouldApplyForVersions(
            "dev.aeroscapes.mixin.atmosphere.SandpaperHeldModelMixin", versions::get),
        "The sandpaper adapter uses the verified provider versions");
  }

  private static void bridgedPlacementVersionGate() {
    Map<String, String> versions =
        new HashMap<>(
            Map.of(
                "accurateblockplacement", "1.4.8_mc-1.20.5-1.21.1",
                "connector", "2.0.0-beta.17+1.21.1",
                "cloth_config", "15.0.140",
                "neoforge", "21.1.244"));
    for (String mixin :
        List.of("AccuratePlacementInteractionMixin", "AccuratePlacementEnumLanguageMixin")) {
      String name = "dev.aeroscapes.mixin." + mixin;
      require(
          AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get),
          "Connector's loaded mod version must enable the pinned placement adapter");
      for (String unreviewed :
          List.of("1.4.9_mc-1.20.5-1.21.1", "1.4.8_mc-1.21.2", "1.4.8+mc-1.20.5-1.21.1")) {
        versions.put("accurateblockplacement", unreviewed);
        try {
          AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get);
          throw new AssertionError("Unexpected runtime version must require adapter review");
        } catch (IllegalStateException expected) {
          require(
              expected.getMessage().contains("accurateblockplacement"),
              "Name the mismatched provider");
        }
      }
      versions.put("accurateblockplacement", "1.4.8_mc-1.20.5-1.21.1");
      versions.remove("connector");
      require(
          !AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get),
          "A missing bridge must disable the adapter");
      versions.put("connector", "2.0.0-beta.17+1.21.1");
    }
  }

  private static void constructionShapeVersionGate() {
    String name = "dev.aeroscapes.mixin.construction.LittleSableShapeContextMixin";
    Map<String, String> versions =
        new HashMap<>(
            Map.of(
                "littletiles",
                "1.6.0-pre236",
                "creativecore",
                "2.13.52",
                "sable",
                "2.0.6",
                "neoforge",
                "21.1.244"));
    require(
        AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get),
        "The reviewed construction physics pins enable the position-aware shape adapter");
    for (String provider : List.copyOf(versions.keySet())) {
      String selected = versions.remove(provider);
      require(
          !AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get),
          "Missing construction physics provider disables the adapter: " + provider);
      versions.put(provider, "unreviewed");
      try {
        AtmosphereMixinPlugin.shouldApplyForVersions(name, versions::get);
        throw new AssertionError("Changed construction physics provider requires review");
      } catch (IllegalStateException expected) {
        require(expected.getMessage().contains(provider), "Name the mismatched physics provider");
      }
      versions.put(provider, selected);
    }
  }

  private static void quiverComposition() {
    for (String mob : List.of("skeleton", "stray", "bogged")) {
      EMFJemData base = new EMFJemData();
      EMFPartData animation = new EMFPartData();
      animation.model = "skeleton_animations.jpm";
      base.models.add(animation);
      EMFPartData head = new EMFPartData();
      head.id = "head";
      base.models.add(head);
      EMFPartData mushrooms = new EMFPartData();
      mushrooms.model = "bogged_mushroom_animations.jpm";
      if (mob.equals("bogged")) base.models.add(mushrooms);
      EMFJemData selected = new EMFJemData();
      EMFPartData quiver = new EMFPartData();
      quiver.part = "body";
      quiver.id = "quiver";
      quiver.model = mob + "_quiver.jpm";
      quiver.attach = true;
      selected.models.add(quiver);
      require(QuiverCompatibility.compose(selected, base, mob), "quiver composition applies");
      require(selected.models.getFirst() == animation, "native animation reference retained");
      require(selected.models.contains(head), "native head and family flags retained");
      require(selected.models.getLast() == quiver, "original body attachment retained");
      require(base.models.size() == (mob.equals("bogged") ? 3 : 2), "base list unchanged");
      require(
          selected.models.contains(mushrooms) == mob.equals("bogged"),
          "bogged mushroom animation retained");
      selected.models.clear();
      require(!QuiverCompatibility.compose(selected, base, mob), "disabled quiver stays absent");
      selected.models.add(quiver);
      quiver.model = "other_pack.jpm";
      require(!QuiverCompatibility.compose(selected, base, mob), "other models stay unchanged");
      require(selected.models.size() == 1, "unrelated model list unchanged");
    }
  }

  private static void puzzlesConfigurationTranslations() throws IOException {
    String owner = "fuzs/puzzleslib/neoforge/impl/client/config/NeoForgeConfigurationScreen";
    method(
        provider(owner),
        "getConfigSectionComponent",
        "(Lnet/neoforged/fml/config/ModConfig$Type;)Lnet/minecraft/network/chat/Component;");
    MethodNode label =
        method(
            provider(owner),
            "getConfigValueComponent",
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;");
    require((label.access & Opcodes.ACC_STATIC) != 0, "Puzzles label conversion remains static");
    List<String> displayCalls = new ArrayList<>();
    for (AbstractInsnNode instruction : executable(label)) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.owner.equals("fuzs/puzzleslib/api/core/v1/ModContainer")
          && call.name.equals("getCapitalizedString")
          && call.desc.equals("(Ljava/lang/String;)Ljava/lang/String;"))
        displayCalls.add("capitalize");
      if (call.owner.equals("java/lang/String") && call.name.equals("replace"))
        displayCalls.add("substitute");
      if (call.owner.equals("net/minecraft/network/chat/Component")
          && call.name.equals("literal")
          && call.desc.equals("(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
        displayCalls.add("literal");
    }
    require(
        displayCalls.equals(List.of("capitalize", "substitute", "substitute", "literal")),
        "one final Puzzles display literal follows native capitalization and And/Or substitutions");
    MethodNode customLabel =
        method(
            provider(owner + "$CustomConfigurationSectionScreen"),
            "getTranslationComponent",
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;");
    require(
        executable(customLabel).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals(owner)
                            && call.name.equals("getConfigValueComponent")
                            && call.desc.equals(
                                "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
                .count()
            == 1,
        "Puzzles custom configuration labels use the scoped display helper");
  }

  private enum SurfaceStyle {
    FLAT
  }

  private enum ItemDisplay {
    FLAT
  }

  private static void contextualConfigEnums() {
    Map<String, String> translations =
        Map.of(
            ConfigLiteralTranslations.key("FLAT"),
            "No smoothing",
            "aeroscapes_reimagine.config.enum." + ItemDisplay.class.getName() + ".FLAT",
            "Laid flat");
    require(
        ConfigLiteralTranslations.translateEnum(
                ItemDisplay.FLAT, "FLAT", translations::getOrDefault)
            .equals("Laid flat"),
        "Item display labels use their own enum identity");
    require(
        ConfigLiteralTranslations.translateEnum(
                SurfaceStyle.FLAT, "FLAT", translations::getOrDefault)
            .equals("No smoothing"),
        "Unrelated enum retains the existing generic translation");
    require(
        ConfigLiteralTranslations.translateEnum(
                    ItemDisplay.FLAT, "FLAT", Map.<String, String>of()::getOrDefault)
                .equals("FLAT")
            && ItemDisplay.FLAT.name().equals("FLAT"),
        "Missing display translations preserve native labels and serialized values");
  }

  private static void catnipConfigTranslations() throws IOException {
    require(
        ConfigLiteralTranslations.key("root")
            .equals(
                "aeroscapes_reimagine.config.literal.4813494d137e1631bba301d5acab6e7bb7aa74ce1185d456565ef51d737677b2"),
        "Catnip literal keys use exact UTF-8 SHA-256 source text");
    String label = "Maximum Rotation Speed";
    String translatedLabel = "Максимальная скорость вращения";
    Map<String, String> russian = Map.of(ConfigLiteralTranslations.key(label), translatedLabel);
    require(
        ConfigLiteralTranslations.translate(label, russian::getOrDefault).equals(translatedLabel),
        "recognized display labels use the selected language");
    require(
        ConfigLiteralTranslations.translate(label, Map.<String, String>of()::getOrDefault)
            .equals(label),
        "English or an absent translation preserves the original display text");
    for (String unknown :
        List.of(
            "Unknown setting",
            "root ",
            "[%s; %1$d; 50%; $value; C:\\path]",
            "[@cui:requiresRestart:CLIENT]")) {
      require(
          ConfigLiteralTranslations.translate(unknown, russian::getOrDefault).equals(unknown),
          "unrecognized exact source, syntax, formatting and whitespace stay intact");
    }
    Map<String, String> custom = Map.of(ConfigLiteralTranslations.key(label), "Мой перевод");
    require(
        ConfigLiteralTranslations.translate(label, custom::getOrDefault).equals("Мой перевод"),
        "resource-pack overrides of owned literal keys are respected");
    for (Map.Entry<String, String> alias :
        Map.of(
                "cropsloverain.configuration.chance_description",
                "cropsloverain.configuration.rain_growth_speed.tooltip",
                "cropsloverain.configuration.chance_description_individual",
                "cropsloverain.configuration.use_individual_speeds.tooltip")
            .entrySet()) {
      String fallbackKey = ConfigLiteralTranslations.key(alias.getKey());
      require(
          ConfigLiteralTranslations.translate(
                  alias.getKey(),
                  Map.of(fallbackKey, "Резервный текст", alias.getValue(), "Текст провайдера")
                      ::getOrDefault)
              .equals("Текст провайдера"),
          "obsolete literal comment IDs resolve the existing provider tooltip");
      require(
          ConfigLiteralTranslations.translate(
                  alias.getKey(),
                  Map.of(alias.getKey(), "Текст ресурспака", alias.getValue(), "Текст провайдера")
                      ::getOrDefault)
              .equals("Текст ресурспака"),
          "a resource pack may override the original comment ID");
      require(
          ConfigLiteralTranslations.translate(
                  alias.getKey(), Map.of(fallbackKey, "Резервный текст")::getOrDefault)
              .equals("Резервный текст"),
          "an absent provider alias keeps the readable owned fallback");
    }

    String labeledOwner = "net/createmod/catnip/config/ui/ConfigScreenList$LabeledEntry";
    MethodNode labelConstructor = method(provider(labeledOwner), "<init>", "(Ljava/lang/String;)V");
    require(
        executable(labelConstructor).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals(
                                "net/createmod/catnip/gui/element/TextStencilElement")
                            && call.name.equals("<init>")
                            && call.desc.equals(
                                "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;)V"))
                .count()
            == 1,
        "one display-only label constructor argument includes values and categories");
    MethodNode labelRender =
        method(
            provider(labeledOwner), "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V");
    require(
        executable(labelRender).stream()
                .filter(
                    instruction ->
                        instruction instanceof FieldInsnNode field
                            && field.owner.equals(labeledOwner)
                            && field.name.equals("unit")
                            && field.getOpcode() == Opcodes.GETFIELD)
                .count()
            == 3,
        "unit null check, width and draw read the same native metadata field");
    ClassNode value = provider("net/createmod/catnip/config/ui/entries/ValueEntry");
    require(value.superName.equals(labeledOwner), "value entries share the native label path");
    require(
        provider("net/createmod/catnip/config/ui/entries/SubMenuEntry")
            .superName
            .equals(labeledOwner),
        "category entries share the native label path");
    MethodNode constructor =
        method(
            value,
            "<init>",
            "(Ljava/lang/String;Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;Lnet/neoforged/neoforge/common/ModConfigSpec$ValueSpec;)V");
    List<String> tooltipBoundaries = new ArrayList<>();
    for (AbstractInsnNode instruction : executable(constructor)) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.owner.equals("net/minecraft/network/chat/Component") && call.name.equals("literal"))
        tooltipBoundaries.add("literal");
      if (call.owner.equals("net/createmod/catnip/config/ui/ConfigHelper")
          && call.name.equals("readMetadataFromComment")) tooltipBoundaries.add("metadata");
      if (call.name.equals("stream")) tooltipBoundaries.add("comment stream");
    }
    require(
        tooltipBoundaries.equals(List.of("literal", "metadata", "comment stream", "literal")),
        "only the first tooltip literal is a label; comments follow native metadata parsing");
    List<String> commentMappers = new ArrayList<>();
    List<AbstractInsnNode> constructorCode = executable(constructor);
    for (int index = 0; index < constructorCode.size(); index++) {
      AbstractInsnNode instruction = constructorCode.get(index);
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("java/util/stream/Stream")
          && call.name.equals("map")) {
        require(
            index > 0
                && constructorCode.get(index - 1) instanceof InvokeDynamicInsnNode factory
                && factory.bsmArgs.length > 1
                && factory.bsmArgs[1] instanceof Handle,
            "native comment stream maps are exact generated function references");
        InvokeDynamicInsnNode factory = (InvokeDynamicInsnNode) constructorCode.get(index - 1);
        commentMappers.add(((Handle) factory.bsmArgs[1]).getName());
      }
    }
    require(
        commentMappers.equals(List.of("lambda$new$3", "lambda$new$4")),
        "only the second mapper constructs comments after native separator normalization");
    MethodNode comment =
        method(
            value,
            "lambda$new$4",
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;");
    require((comment.access & Opcodes.ACC_STATIC) != 0, "comment display mapper remains static");
    require(
        executable(comment).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraft/network/chat/Component")
                            && call.name.equals("literal"))
                .count()
            == 1,
        "one comment display conversion leaves raw specification comments intact");
    String subMenuOwner = "net/createmod/catnip/config/ui/SubMenuConfigScreen";
    MethodNode render =
        method(
            provider(subMenuOwner), "renderWindow", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V");
    require(
        executable(render).stream()
                .filter(
                    instruction ->
                        instruction instanceof FieldInsnNode field
                            && field.owner.equals(subMenuOwner)
                            && field.name.equals("title")
                            && field.getOpcode() == Opcodes.GETFIELD)
                .count()
            == 1,
        "one title display read preserves the native submenu title");
    require(
        executable(render).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/neoforged/fml/config/ModConfig$Type")
                            && call.name.equals("toString"))
                .count()
            == 1,
        "one configuration-type display conversion preserves the selected enum");
    MethodNode enumDisplay =
        method(
            provider("net/createmod/catnip/config/ui/entries/EnumEntry"),
            "onValueChange",
            "(Ljava/lang/Enum;)V");
    require(
        executable(enumDisplay).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals(
                                "net/createmod/catnip/gui/element/TextStencilElement")
                            && call.name.equals("withText")
                            && call.desc.equals(
                                "(Ljava/lang/String;)Lnet/createmod/catnip/gui/element/TextStencilElement;"))
                .count()
            == 1,
        "one enum display conversion leaves enum identity and cycling unchanged");
  }

  private static void contextualConfigComments() {
    String line = "Maximum structure search size";
    String radiusBlock = line + "\n Default: 100\n Range: 1 ~ 1000";
    String countBlock = line + "\n Default: 3\n Range: 1 ~ 10";
    Map<String, String> translations =
        Map.of(
            ConfigLiteralTranslations.key(line),
            "Общий размер поиска",
            ConfigLiteralTranslations.key(radiusBlock),
            "Радиус поиска в чанках\n По умолчанию: 100\n Диапазон: 1 ~ 1000",
            ConfigLiteralTranslations.key(countBlock),
            "Число типов структур\n По умолчанию: 3\n Диапазон: 1 ~ 10");
    require(
        ConfigLiteralTranslations.translateCommentLine(
                line, radiusBlock, translations::getOrDefault)
            .equals("Радиус поиска в чанках"),
        "complete comment context selects the radius setting's translated line");
    require(
        ConfigLiteralTranslations.translateCommentLine(line, countBlock, translations::getOrDefault)
            .equals("Число типов структур"),
        "the same copied source line can describe a different actual setting");
    for (String absentBlock : List.of("Unknown block", line + "\n Default: 9")) {
      require(
          ConfigLiteralTranslations.translateCommentLine(
                  line, absentBlock, translations::getOrDefault)
              .equals("Общий размер поиска"),
          "unknown complete blocks retain the exact line fallback");
    }
    require(
        ConfigLiteralTranslations.translateCommentLine(line, null, translations::getOrDefault)
            .equals("Общий размер поиска"),
        "absent source block retains the line fallback");
    for (String malformed : List.of("Only one translated line", "one\ntwo\nthree\nfour")) {
      Map<String, String> custom = new HashMap<>(translations);
      custom.put(ConfigLiteralTranslations.key(radiusBlock), malformed);
      require(
          ConfigLiteralTranslations.translateCommentLine(line, radiusBlock, custom::getOrDefault)
              .equals("Общий размер поиска"),
          "changed translation line structure cannot select an unrelated line");
    }
    require(
        ConfigLiteralTranslations.translateCommentLine(
                "Unknown line", radiusBlock, translations::getOrDefault)
            .equals("Unknown line"),
        "only an exact original line may select a translated block line");
    Map<String, String> custom = new HashMap<>(translations);
    custom.put(ConfigLiteralTranslations.key(radiusBlock), radiusBlock);
    require(
        ConfigLiteralTranslations.translateCommentLine(line, radiusBlock, custom::getOrDefault)
            .equals(line),
        "an explicit original-language resource-pack override takes precedence");
    String repeated = line + "\n" + line;
    custom.put(ConfigLiteralTranslations.key(repeated), "Первый смысл\nВторой смысл");
    require(
        ConfigLiteralTranslations.translateCommentLine(line, repeated, custom::getOrDefault)
            .equals("Общий размер поиска"),
        "ambiguous repeated source lines retain the generic line fallback");
  }

  private static void runtimeConfigMetadata() {
    Map<String, String> translations =
        Map.of(
            ConfigLiteralTranslations.key("Worker thread count"),
            "Число рабочих потоков",
            ConfigLiteralTranslations.key(" Default: "),
            " По умолчанию: ",
            ConfigLiteralTranslations.key(" Range: "),
            " Диапазон: ",
            ConfigLiteralTranslations.key(" Allowed Values: "),
            " Допустимые значения: ");
    String block =
        "\n"
            + "Worker thread count\n"
            + "example:worker_id\n"
            + " Default: 72\n"
            + " Range: -1 ~ 192\n"
            + " Allowed Values: AUTO, CUSTOM\n";
    String expected =
        "\n"
            + "Число рабочих потоков\n"
            + "example:worker_id\n"
            + " По умолчанию: 72\n"
            + " Диапазон: -1 ~ 192\n"
            + " Допустимые значения: AUTO, CUSTOM\n";
    require(
        ConfigLiteralTranslations.translateCommentBlock(block, translations::getOrDefault)
            .equals(expected),
        "runtime metadata values, IDs and leading/trailing newlines survive line translation");
    require(
        ConfigLiteralTranslations.translateCommentBlock(
                block, Map.<String, String>of()::getOrDefault)
            .equals(block),
        "English or absent dictionaries preserve the complete original block");
    for (int processors : List.of(2, 12, 63, 168)) {
      String sourceLine = " Range: -1 ~ " + processors;
      String runtimeBlock = "Worker thread count\n Default: -1\n" + sourceLine;
      require(
          ConfigLiteralTranslations.translateCommentLine(
                  sourceLine, runtimeBlock, translations::getOrDefault)
              .equals(" Диапазон: -1 ~ " + processors),
          "Catnip metadata fallback accepts unseen machine-dependent CPU bounds");
    }
    String suffix = "-1 ~ 9999; %s; example:worker_id; C:\\path";
    require(
        ConfigLiteralTranslations.translateCommentBlock(
                " Range: " + suffix, translations::getOrDefault)
            .equals(" Диапазон: " + suffix),
        "metadata suffixes are copied without formatting or token interpretation");
    for (String unknown : List.of(" Defaulted: 72", " default: 72", " Range:", "unknown:setting")) {
      require(
          ConfigLiteralTranslations.translateCommentBlock(unknown, translations::getOrDefault)
              .equals(unknown),
          "only the finite exact metadata prefixes receive automatic headings");
    }
    Map<String, String> custom = new HashMap<>(translations);
    custom.put(ConfigLiteralTranslations.key(block), block);
    require(
        ConfigLiteralTranslations.translateCommentBlock(block, custom::getOrDefault).equals(block),
        "an explicit original-English whole-block override prevents automatic line translation");
    custom.put(ConfigLiteralTranslations.key(block), "Пользовательский блок");
    require(
        ConfigLiteralTranslations.translateCommentBlock(block, custom::getOrDefault)
            .equals("Пользовательский блок"),
        "an explicit whole-block override keeps its own layout and content");
    custom.remove(ConfigLiteralTranslations.key(block));
    custom.put(ConfigLiteralTranslations.key(" Default: 72"), " Default: 72");
    require(
        ConfigLiteralTranslations.translateCommentLine(" Default: 72", block, custom::getOrDefault)
            .equals(" Default: 72"),
        "an explicit original-English line override prevents prefix fallback");
    custom.put(ConfigLiteralTranslations.key(" Default: 72"), " Моя настройка: 72");
    require(
        ConfigLiteralTranslations.translateCommentBlock(block, custom::getOrDefault)
            .contains("\n Моя настройка: 72\n"),
        "explicit line overrides take priority within an otherwise unknown block");
    String alias = "cropsloverain.configuration.chance_description";
    custom.put(ConfigLiteralTranslations.key(alias), "Резервная подсказка");
    custom.put("cropsloverain.configuration.rain_growth_speed.tooltip", "Подсказка провайдера");
    require(
        ConfigLiteralTranslations.translateCommentBlock(alias, custom::getOrDefault)
            .equals("Подсказка провайдера"),
        "one-line obsolete comment IDs retain the provider alias before their owned fallback");
    require(
        ConfigLiteralTranslations.translateCommentLine(alias, alias, custom::getOrDefault)
            .equals("Подсказка провайдера"),
        "Catnip full-block context does not intercept the bounded comment aliases");
    require(
        ConfigLiteralTranslations.translateCommentBlock(
                "Worker thread count\n" + alias, custom::getOrDefault)
            .equals("Число рабочих потоков\nПодсказка провайдера"),
        "mixed blocks resolve aliases while translating ordinary body lines");
  }

  private static void nativeConfigurationTranslations() throws IOException {
    String source = "Native fallback comment: 100% of the configured amount.";
    String translated = "Переведённая подсказка: 100% заданного количества.";
    String tooltip = "example.configuration.option.tooltip";
    Map<String, String> russian =
        Map.of(ConfigLiteralTranslations.key(source), translated, tooltip, "Перевод ресурспака");
    String fallback =
        ConfigLiteralTranslations.translateCommentBlock(source, russian::getOrDefault);
    require(fallback.equals(translated), "native fallback comments retain literal percentages");
    require(
        russian.getOrDefault(tooltip, fallback).equals("Перевод ресурспака"),
        "native tooltip keys and user resources retain priority over fallback comments");
    require(
        Map.<String, String>of().getOrDefault(tooltip, fallback).equals(translated),
        "missing native tooltip keys use the translated source comment");
    ClassNode configuration = new ClassNode();
    try (var input =
        AtmosphereContractTest.class.getResourceAsStream(
            "/net/neoforged/neoforge/client/gui/ConfigurationScreen$ConfigurationSectionScreen.class")) {
      require(input != null, "pinned native configuration screen is available");
      new ClassReader(input.readAllBytes()).accept(configuration, 0);
    }
    MethodNode comment =
        method(configuration, "getComment", "(Ljava/lang/String;)Ljava/lang/String;");
    require(
        executable(comment).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && (call.name.equals("getComment")
                                || call.name.equals("getLevelComment")))
                .count()
            == 2,
        "native value and category comment sources meet at one display boundary");
    MethodNode tooltipMethod =
        method(
            configuration,
            "getTooltipComponent",
            "(Ljava/lang/String;Lnet/neoforged/neoforge/common/ModConfigSpec$Range;)Lnet/minecraft/network/chat/Component;");
    require(
        executable(tooltipMethod).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraft/network/chat/Component")
                            && call.name.equals("translatableWithFallback")
                            && call.desc.equals(
                                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
                .count()
            == 1,
        "native tooltip translation retains its resource key and fallback semantics");
    MethodNode enumLabel =
        method(
            configuration,
            "lambda$createEnumValue$12",
            "(Lnet/minecraft/network/chat/Component;Ljava/lang/Enum;)Lnet/minecraft/network/chat/Component;");
    for (String target : List.of("getTranslatedName", "name")) {
      require(
          executable(enumLabel).stream()
                  .filter(
                      instruction ->
                          instruction instanceof MethodInsnNode call && call.name.equals(target))
                  .count()
              == 1,
          "native translated enums and literal enum names retain separate branches");
    }
  }

  private static void languageCorrections() throws IOException {
    Map<String, String> original = new HashMap<>();
    Map<String, String> englishOriginal = new HashMap<>();
    Map<String, String> replacements = new HashMap<>();
    Map<String, String> englishReplacements = new HashMap<>();
    try (var input =
        AtmosphereContractTest.class.getResourceAsStream(
            "/assets/aeroscapes_reimagine/language_corrections.json")) {
      require(input != null, "language correction rules are packaged");
      var rows =
          JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
              .getAsJsonArray();
      for (var element : rows) {
        var row = element.getAsJsonObject();
        String key = row.get("key").getAsString();
        String source = row.get("expected").getAsString();
        String replacement = row.get("replacement").getAsString();
        boolean english = row.has("language") && row.get("language").getAsString().equals("en_us");
        if (english) {
          englishOriginal.put(key, source);
          englishReplacements.put(key, replacement);
          original.putIfAbsent(key, source);
          replacements.putIfAbsent(key, source);
        } else {
          original.put(key, source);
          replacements.put(key, replacement);
          englishOriginal.putIfAbsent(key, source);
          englishReplacements.putIfAbsent(key, source);
        }
      }
    }
    require(!original.isEmpty(), "verified provider defects have correction rules");
    Map<String, String> baseline = Map.copyOf(original);
    List<String> russian = List.of("en_us", "ru_ru");
    Map<String, String> corrected = LanguageCorrections.apply(original, russian);
    require(corrected.equals(replacements), "all exact provider strings are corrected in Russian");
    Map<String, String> brandLabels =
        Map.of(
            "itemGroup.cratedelight", "Ящичные Изыски",
            "itemGroup.windchimes.main", "wind chimes",
            "itemGroup.naturalist.tab", "Естествовед",
            "spawn.item_group", "Спавн",
            "item.spawn.spawn", "Спавн",
            "itemGroup.comforts", "Удобства",
            "itemGroup.comforts.general", "Удобства",
            "entity.corpse.corpse", "Труп",
            "advancement.exposure.exposure.title", "Разрезая время светом");
    require(
        LanguageCorrections.apply(brandLabels, russian)
            .equals(
                Map.of(
                    "itemGroup.cratedelight", "Crate Delight",
                    "itemGroup.windchimes.main", "wind chimes",
                    "itemGroup.naturalist.tab", "Naturalist",
                    "spawn.item_group", "Spawn",
                    "item.spawn.spawn", "Spawn",
                    "itemGroup.comforts", "Comforts",
                    "itemGroup.comforts.general", "Comforts",
                    "entity.corpse.corpse", "Труп",
                    "advancement.exposure.exposure.title", "Разрезая время светом")),
        "mod brands retain their names while ordinary nouns and advancement titles stay localized");
    require(
        LanguageCorrections.apply(
                Map.of("itemGroup.cratedelight", "Custom resource-pack title"), russian)
            .equals(Map.of("itemGroup.cratedelight", "Custom resource-pack title")),
        "brand repairs preserve changed player translations");
    require(original.equals(baseline), "provider maps are not mutated");
    require(
        LanguageCorrections.apply(corrected, russian).equals(corrected),
        "language reload is idempotent");
    for (List<String> languages : List.of(List.<String>of(), List.of("en_us", "de_de"))) {
      require(
          LanguageCorrections.apply(original, languages).equals(original),
          "other selected languages retain even identical source strings");
    }
    Map<String, String> english = LanguageCorrections.apply(englishOriginal, List.of("en_us"));
    require(english.equals(englishReplacements), "English-only repairs preserve Russian rules");
    require(
        LanguageCorrections.apply(english, List.of("en_us")).equals(english),
        "English language reload is idempotent");
    Map<String, String> custom = new HashMap<>();
    original.forEach((key, value) -> custom.put(key, value + " custom"));
    custom.put("unrelated.translation", "Unrelated");
    require(
        LanguageCorrections.apply(custom, russian).equals(custom),
        "changed provider strings and user resource-pack replacements win");
    require(
        LanguageCorrections.apply(custom, List.of("en_us")).equals(custom),
        "English repairs preserve changed provider and user translations");
    require(
        LanguageCorrections.apply(Map.of("unrelated.translation", "Unrelated"), russian)
            .equals(Map.of("unrelated.translation", "Unrelated")),
        "missing correction keys are not introduced");
    try {
      corrected.put("unrelated.translation", "Unrelated");
      throw new AssertionError("language map must stay immutable");
    } catch (UnsupportedOperationException expected) {
      require(corrected.equals(replacements), "immutable language map preserves corrections");
    }
    Map<String, Integer> argumentCounts =
        Map.of(
            "death.attack.allurement.shockwave", 2,
            "betterclouds.message.profiling.genTimes", 6,
            "quark.jei.hint.gold_tool_fortune", 1,
            "item.supplementaries.bamboo_spikes_tipped.effect", 1,
            "message.supplementaries.cannon.maneuver", 2,
            "tooltip.emi.synfav.fully_craftable", 1);
    argumentCounts.forEach(
        (key, count) -> {
          Set<Integer> references = new HashSet<>();
          var matcher = Pattern.compile("%(?:(\\d+)\\$)?([s%])").matcher(corrected.get(key));
          int implicit = 0;
          while (matcher.find()) {
            if (matcher.group(2).equals("%")) continue;
            references.add(
                matcher.group(1) == null ? ++implicit : Integer.parseInt(matcher.group(1)));
          }
          Set<Integer> expected = new HashSet<>();
          for (int index = 1; index <= count; index++) expected.add(index);
          require(references.equals(expected), "all English arguments survive: " + key);
        });
    ClassNode language = new ClassNode();
    try (var input =
        AtmosphereContractTest.class.getResourceAsStream(
            "/net/minecraft/client/resources/language/ClientLanguage.class")) {
      require(input != null, "pinned native language implementation is available");
      new ClassReader(input.readAllBytes()).accept(language, 0);
    }
    MethodNode load =
        method(
            language,
            "loadFrom",
            "(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Z)Lnet/minecraft/client/resources/language/ClientLanguage;");
    require(
        executable(load).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESPECIAL
                            && call.owner.equals(language.name)
                            && call.name.equals("<init>")
                            && call.desc.equals("(Ljava/util/Map;ZLjava/util/Map;)V"))
                .count()
            == 1,
        "one final native language map is supplied to the NeoForge constructor");
  }

  private static void lanternGrip() throws IOException {
    ItemTransform display =
        new ItemTransform(
            new Vector3f(200, 0, 197), new Vector3f(0, -3f / 16, -1f / 16), new Vector3f(0.7f));
    for (boolean left : List.of(false, true)) {
      PoseStack model = new PoseStack();
      display.apply(left, model);
      Vector3f pivot = new Vector3f(left ? 0.04f : -0.04f, -0.14f, 0.11f);
      for (float swing : List.of(0f, 0.7f, -0.4f)) {
        Matrix4f arm = new Matrix4f().translation(7, -4, 12).rotateY(0.7f).scale(1.01f);
        Matrix4f rotation =
            new Matrix4f(arm)
                .translate(pivot)
                .rotateX(swing)
                .rotateZ(-1.4f)
                .translate(new Vector3f(pivot).negate());
        for (Vector3f adjustment : List.of(new Vector3f(), new Vector3f(0.1f, 0.2f, -0.3f))) {
          Vector3f translation =
              LanternGrip.translation(
                  model.last().pose(),
                  left,
                  adjustment.x,
                  -0.49999976f + adjustment.y,
                  0.06999993f + adjustment.z);
          Vector3f actual =
              new Matrix4f(rotation)
                  .translate(translation)
                  .mul(model.last().pose())
                  .transformPosition(new Vector3f(0, 0.125f, 0));
          Vector3f expected =
              rotation.transformPosition(new Vector3f(pivot).add(adjustment), new Vector3f());
          require(
              actual.distance(expected) < 0.00001f, "lantern handle follows grip and user offset");
        }
      }
    }
    ClassNode arms = provider("punchy/client/render/PunchyArmRenderer");
    MethodNode held =
        arms.methods.stream()
            .filter(method -> method.name.equals("renderItemInHand"))
            .findFirst()
            .orElseThrow();
    require(
        executable(held).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.name.equals("applyItemTranslation")
                            && call.desc.equals(
                                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lpunchy/client/render/ToolTuning$Profile;Z)V"))
                .count()
            == 2,
        "visible lantern and native item proxy share the same translation boundary");
    List<AbstractInsnNode> heldCode = executable(held);
    MethodInsnNode resolved =
        heldCode.stream()
            .filter(
                instruction ->
                    instruction instanceof MethodInsnNode call
                        && call.owner.equals("punchy/client/animation/PunchyAnimationManager")
                        && call.name.equals("resolveRenderStack"))
            .map(MethodInsnNode.class::cast)
            .findFirst()
            .orElseThrow();
    require(
        local(heldCode.get(heldCode.indexOf(resolved) + 1), Opcodes.ASTORE, 14),
        "Punchy's resolved visual stack occupies local 14, separately from base stack 13");
    int translations = 0;
    int itemRenders = 0;
    for (AbstractInsnNode instruction : heldCode) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.name.equals("applyItemTranslation")) {
        translations++;
        require(
            held.localVariables.stream()
                .anyMatch(
                    variable ->
                        variable.index == 14
                            && variable.desc.equals("Lnet/minecraft/world/item/ItemStack;")
                            && held.instructions.indexOf(variable.start)
                                < held.instructions.indexOf(call)
                            && held.instructions.indexOf(variable.end)
                                > held.instructions.indexOf(call)),
            "resolved visual stack remains live at both translation boundaries");
      }
      if (call.owner.equals("net/minecraft/client/renderer/ItemInHandRenderer")
          && call.name.equals("renderItem")
          && translations == 2) {
        itemRenders++;
        require(
            local(heldCode.get(heldCode.indexOf(call) - 6), Opcodes.ALOAD, 14),
            "native item emission uses the same resolved visual stack");
      }
    }
    require(itemRenders == 1, "one visible item render follows both grip translation boundaries");
    MethodNode resolver =
        method(
            provider("punchy/client/animation/PunchyAnimationManager"),
            "resolveRenderStack",
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;");
    for (String boundary : List.of("isOffhandEquipTransitionActive", "getVisualOffhandStack"))
      require(
          executable(resolver).stream()
              .anyMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call
                          && call.owner.equals("punchy/client/state/HandEquipStateMachine")
                          && call.name.equals(boundary)),
          "equip transitions can render a cached stack instead of the live hand item");
    MethodNode adapter =
        classpathClass("dev/aeroscapes/mixin/atmosphere/LanternGripMixin").methods.stream()
            .filter(method -> method.name.equals("aeroscapes$attachLantern"))
            .findFirst()
            .orElseThrow();
    int stackParameter = capturedLocal(adapter, 14, "Lnet/minecraft/world/item/ItemStack;");
    List<AbstractInsnNode> adapterCode = executable(adapter);
    for (AbstractInsnNode instruction : adapterCode) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.name.equals("is") && call.owner.equals("net/minecraft/world/item/ItemStack"))
        require(
            local(adapterCode.get(adapterCode.indexOf(call) - 2), Opcodes.ALOAD, stackParameter),
            "lantern gating checks the actual rendered stack during equip transitions");
      if (call.name.equals("resolveBaselineUseTransform"))
        require(
            local(adapterCode.get(adapterCode.indexOf(call) - 3), Opcodes.ALOAD, stackParameter),
            "lantern model resolution uses the same captured visual stack");
    }
    method(
        arms,
        "resolveBaselineUseTransform",
        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/block/model/ItemTransform;");
  }

  private static void rainImpactBiome() throws IOException {
    ClassNode nativeMixin =
        provider("pigcart/particlerain/mixin/render/WeatherEffectRendererMixin");
    MethodNode substitution =
        nativeMixin.methods.stream()
            .filter(method -> method.name.equals("getBiomeValue"))
            .findFirst()
            .orElseThrow();
    List<AbstractInsnNode> instructions = executable(substitution);
    require(
        instructions.stream()
            .anyMatch(
                instruction ->
                    instruction instanceof FieldInsnNode field
                        && field.owner.equals("net/minecraft/world/level/biome/Biomes")
                        && field.name.equals("PLAINS")),
        "pinned rain adapter replaces actual impact biome with plains");
    require(
        instructions.stream()
            .noneMatch(
                instruction ->
                    instruction instanceof MethodInsnNode call
                        && call.owner.equals(
                            "com/llamalad7/mixinextras/injector/wrapoperation/Operation")
                        && call.name.equals("call")),
        "native impact-biome substitution bypasses the original lookup");
    MethodNode tick =
        method(
            classpathClass("net/minecraft/client/renderer/LevelRenderer"),
            "tickRain",
            "(Lnet/minecraft/client/Camera;)V");
    List<AbstractInsnNode> tickCode = executable(tick);
    int emissions = 0;
    for (AbstractInsnNode instruction : tickCode) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      int index = tickCode.indexOf(call);
      if (call.name.equals("getHeightmapPos")
          && call.owner.equals("net/minecraft/world/level/LevelReader")
          && emissions == 0)
        require(
            local(tickCode.get(index + 1), Opcodes.ASTORE, 11),
            "native weather sample from the heightmap occupies local 11");
      if (call.name.equals("getBiome") || call.name.equals("getPrecipitationAt"))
        require(
            local(tickCode.get(index - 1), Opcodes.ALOAD, 11),
            "native impact biome and altitude precipitation share sample local 11");
      if (call.name.equals("below"))
        require(
            local(tickCode.get(index - 1), Opcodes.ALOAD, 11)
                && local(tickCode.get(index + 1), Opcodes.ASTORE, 6),
            "collision block local 6 is below the actual weather sample");
      if (call.owner.equals("net/minecraft/client/multiplayer/ClientLevel")
          && call.name.equals("addParticle")) {
        emissions++;
        require(
            tick.localVariables.stream()
                .anyMatch(
                    variable ->
                        variable.index == 11
                            && variable.desc.equals("Lnet/minecraft/core/BlockPos;")
                            && tick.instructions.indexOf(variable.start)
                                < tick.instructions.indexOf(call)
                            && tick.instructions.indexOf(variable.end)
                                > tick.instructions.indexOf(call)),
            "native weather sample remains live at the sole particle emission");
      }
    }
    require(emissions == 1, "one native impact particle emission remains independently guarded");
    MethodNode adapter =
        classpathClass("dev/aeroscapes/mixin/atmosphere/RainImpactBiomeMixin").methods.stream()
            .filter(method -> method.name.equals("aeroscapes$localImpactWeather"))
            .findFirst()
            .orElseThrow();
    int sampleParameter = capturedLocal(adapter, 11, "Lnet/minecraft/core/BlockPos;");
    List<AbstractInsnNode> adapterCode = executable(adapter);
    for (AbstractInsnNode instruction : adapterCode) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.name.equals("getBiome") || call.name.equals("getPrecipitationAt"))
        require(
            local(adapterCode.get(adapterCode.indexOf(call) - 1), Opcodes.ALOAD, sampleParameter),
            "guard biome and altitude use the captured weather sample, including partial surfaces");
    }
  }

  private static ClassNode classpathClass(String name) throws IOException {
    try (var input = AtmosphereContractTest.class.getResourceAsStream("/" + name + ".class")) {
      require(input != null, "contract class is available: " + name);
      ClassNode type = new ClassNode();
      new ClassReader(input.readAllBytes()).accept(type, 0);
      return type;
    }
  }

  private static void creativeSearchContract() throws IOException {
    ClassNode screen =
        classpathClass("net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen");
    method(screen, "refreshSearchResults", "()V");
    method(screen, "containerTick", "()V");
    require(
        screen.fields.stream()
            .anyMatch(
                field ->
                    field.name.equals("selectedTab")
                        && field.desc.equals("Lnet/minecraft/world/item/CreativeModeTab;")
                        && (field.access & Opcodes.ACC_STATIC) != 0),
        "native selected creative tab remains a static field");
    require(
        screen.fields.stream()
            .anyMatch(
                field ->
                    field.name.equals("searchBox")
                        && field.desc.equals("Lnet/minecraft/client/gui/components/EditBox;")),
        "native creative query field remains available");
    ClassNode search = classpathClass("net/minecraft/client/multiplayer/SessionSearchTrees");
    for (String name : List.of("creativeNameSearch", "creativeTagSearch")) {
      MethodNode lookup =
          method(
              search,
              name,
              "(Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;)Lnet/minecraft/client/searchtree/SearchTree;");
      require(
          executable(lookup).stream()
              .anyMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call
                          && call.owner.equals("java/util/concurrent/CompletableFuture")
                          && call.name.equals("join")),
          "creative index readiness must be checked before the native blocking lookup");
    }
  }

  private static int capturedLocal(MethodNode handler, int index, String descriptor) {
    Type[] parameters = Type.getArgumentTypes(handler.desc);
    require(handler.invisibleParameterAnnotations != null, "adapter has local capture annotations");
    int slot = (handler.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
    for (int parameter = 0; parameter < parameters.length; parameter++) {
      var annotations = handler.invisibleParameterAnnotations[parameter];
      if (annotations != null)
        for (AnnotationNode annotation : annotations) {
          if (!annotation.desc.equals("Lcom/llamalad7/mixinextras/sugar/Local;")) continue;
          int key = annotation.values.indexOf("index");
          if (key < 0 || !annotation.values.get(key + 1).equals(index)) continue;
          require(parameters[parameter].getDescriptor().equals(descriptor), "captured local type");
          return slot;
        }
      slot += parameters[parameter].getSize();
    }
    throw new AssertionError("Missing captured local " + index + " in " + handler.name);
  }

  private static boolean local(AbstractInsnNode instruction, int opcode, int index) {
    return instruction instanceof VarInsnNode variable
        && variable.getOpcode() == opcode
        && variable.var == index;
  }

  private static void loadingOverlay() throws IOException {
    ClassNode nativeMixin = provider("net/puzzlemc/splashscreen/mixin/MixinNeoForgeLoadingOverlay");
    MethodNode nativeHandler =
        nativeMixin.methods.stream()
            .filter(method -> method.name.equals("puzzle$initTexturesNeoforge"))
            .findFirst()
            .orElseThrow();
    ClassNode overlay = mergedOverlay(nativeHandler);
    MethodNode unrelated = new MethodNode();
    unrelated.name = "nativeLogoRegistration";
    unrelated.desc = "()V";
    unrelated.instructions.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "net/minecraft/client/gui/screens/LoadingOverlay",
            "registerTextures",
            "(Lnet/minecraft/client/Minecraft;)V",
            false));
    overlay.methods.add(unrelated);
    PuzzleLoadingOverlayTransformer.patch(overlay);
    require(
        executable(overlay.methods.getFirst()).stream()
            .map(AbstractInsnNode::getOpcode)
            .toList()
            .equals(List.of(Opcodes.ALOAD, Opcodes.POP, Opcodes.RETURN)),
        "only Puzzle's redundant registration is consumed without deleting the original logo");
    require(
        unrelated.instructions.getFirst() instanceof MethodInsnNode,
        "native logo registration is retained");
    rejectsOverlay(overlay);
    ClassNode absent = mergedOverlay(nativeHandler);
    absent.methods.getFirst().visibleAnnotations = null;
    rejectsOverlay(absent);
    ClassNode duplicate = mergedOverlay(nativeHandler);
    duplicate.methods.add(mergedOverlay(nativeHandler).methods.getFirst());
    rejectsOverlay(duplicate);
  }

  private static ClassNode mergedOverlay(MethodNode nativeHandler) {
    ClassNode overlay = new ClassNode();
    overlay.name = "net/neoforged/neoforge/client/loading/NeoForgeLoadingOverlay";
    MethodNode handler =
        new MethodNode(
            Opcodes.ASM9,
            nativeHandler.access,
            "handler$fixture$puzzle$initTexturesNeoforge",
            nativeHandler.desc,
            null,
            null);
    nativeHandler.accept(handler);
    AnnotationNode merged =
        new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
    merged.values =
        new ArrayList<>(
            List.of("mixin", "net.puzzlemc.splashscreen.mixin.MixinNeoForgeLoadingOverlay"));
    if (handler.visibleAnnotations == null) handler.visibleAnnotations = new ArrayList<>();
    handler.visibleAnnotations.add(merged);
    overlay.methods.add(handler);
    return overlay;
  }

  private static void rejectsOverlay(ClassNode overlay) {
    try {
      PuzzleLoadingOverlayTransformer.patch(overlay);
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError("Changed Puzzle overlay must require a new compatibility review");
  }

  private static void fastRodRenderers() throws IOException {
    String fastDescriptor =
        "(Lnet/minecraft/client/resources/model/SimpleBakedModel;ILnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lnet/minecraft/client/color/item/ItemColors;)V";
    for (String name :
        List.of(
            "com/moepus/flerovium/functions/FastSimpleBakedModelRenderer",
            "com/moepus/flerovium/Iris/IrisSimpleBakedItemRenderer")) {
      MethodNode renderer = method(provider(name), "render", fastDescriptor);
      require(
          (renderer.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC))
              == (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC),
          "public static fast renderer");
      List<AbstractInsnNode> code = executable(renderer);
      require(
          code.getFirst() instanceof org.objectweb.asm.tree.VarInsnNode local
              && local.getOpcode() == Opcodes.ALOAD
              && local.var == 5,
          "fast renderer uses actual incoming pose");
      require(
          code.get(1) instanceof MethodInsnNode call
              && call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")
              && call.name.equals("last"),
          "same pose passed to mesh emission");
      require(
          code.stream()
              .anyMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call && call.name.equals("getQuads")),
          "real model geometry still drives fast draw");
      require(
          code.stream()
              .noneMatch(
                  instruction ->
                      instruction instanceof MethodInsnNode call
                          && call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")
                          && !call.name.equals("last")),
          "entry pose stays unchanged before vertices");
    }
  }

  private static void distantSceneryCompatibility() throws IOException {
    require(
        !DistantSceneryPolicy.showCompatibilityChat(
            "§6Distant Horizons: Chunky detected.§r\nCompatibility advice"),
        "only the Chunky startup chat is hidden");
    for (String message :
        List.of(
            "§6Distant Horizons: Alex's Caves detected.§r\nCompatibility advice",
            "Distant Horizons: not enough memory",
            "Chunky generation failed",
            "")) {
      require(DistantSceneryPolicy.showCompatibilityChat(message), "other messages stay visible");
    }
    for (int material = 0; material < 256; material++) {
      byte original = (byte) material;
      require(
          DistantSceneryPolicy.material(original) == (material == 15 ? 0 : original),
          "only the synthetic illuminated category changes");
    }
    ClassNode initializer =
        provider("com/seibel/distanthorizons/common/AbstractModInitializer_neoforge");
    MethodNode warnings = method(initializer, "logIncompatibilityWarnings", "()V");
    require((warnings.access & Opcodes.ACC_STATIC) != 0, "static compatibility startup boundary");
    require(
        executable(warnings).stream()
            .anyMatch(
                instruction ->
                    instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(
                            "com/seibel/distanthorizons/core/api/internal/ClientApi")
                        && call.name.equals("queueSlowChatMessage")
                        && call.desc.equals("(Ljava/lang/String;)V")),
        "compatibility chat call is direct and has an instance receiver");
    ClassNode wrapper =
        provider("com/seibel/distanthorizons/common/wrappers/block/BlockStateWrapper_neoforge");
    List<AbstractInsnNode> getter = executable(method(wrapper, "getMaterialId", "()B"));
    require(
        getter.size() == 3
            && getter.get(1) instanceof FieldInsnNode field
            && field.name.equals("blockMaterialId")
            && field.desc.equals("B")
            && getter.get(2).getOpcode() == Opcodes.IRETURN,
        "material getter is separate from color, opacity and light sampling");
    ClassNode clouds = provider("com/qendolin/betterclouds/clouds/shaders/Shader");
    MethodNode compile =
        method(
            clouds,
            "compileShader",
            "(ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/server/packs/resources/ResourceManager;)I");
    require((compile.access & Opcodes.ACC_STATIC) == 0, "instance cloud shader compiler");
    MethodNode coverageResources =
        method(
            provider("com/qendolin/betterclouds/clouds/shaders/CoverageShader"), "<clinit>", "()V");
    require(
        executable(coverageResources).stream()
            .anyMatch(
                instruction ->
                    instruction instanceof LdcInsnNode constant
                        && constant.cst.equals("shaders/core/betterclouds_coverage.fsh")),
        "native coverage compiler uses the exact intercepted resource path");
    require(
        executable(compile).stream()
                .filter(
                    instruction ->
                        instruction instanceof MethodInsnNode call
                            && call.owner.equals("org/apache/commons/io/IOUtils")
                            && call.name.equals("toString")
                            && call.desc.equals(
                                "(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
                .count()
            == 1,
        "one source-loading boundary before native cloud shader preprocessing");
    String resource = "betterclouds:shaders/core/betterclouds_coverage.fsh";
    String source =
        new String(
            providerResource("assets/betterclouds/shaders/core/betterclouds_coverage.fsh"),
            StandardCharsets.UTF_8);
    String adapted = CloudCoverageShader.adapt(resource, source);
    require(!adapted.equals(source), "the actual pinned cloud shader is adapted");
    require(
        CloudCoverageShader.adapt(resource, "custom cloud shader").equals("custom cloud shader"),
        "player resource overrides remain unchanged");
    require(
        CloudCoverageShader.adapt("another:shader.fsh", source).equals(source),
        "other shader programs remain unchanged");
    Files.writeString(Path.of("build", "cloud-coverage.frag"), adapted);
  }

  private static void schematicMessages() throws IOException {
    String filename = "factory%20%s.schem";
    var translated =
        ForgematicaMessages.resolve(
            "Failed to read the Sponge schematic from '" + filename + "\"", new Object[0]);
    require(translated != null, "Native schematic import failure has a translation route");
    require(
        translated.key().equals("aeroscapes_reimagine.forgematica.message.sponge_read_failed")
            && List.of(translated.arguments()).equals(List.of(filename)),
        "Filenames stay raw arguments instead of format strings");
    require(
        ForgematicaMessages.resolve(translated.key(), translated.arguments()) == null,
        "Translated calls cannot reenter literal handling");
    require(
        ForgematicaMessages.resolve("Deleted twelve blocks", new Object[0]) == null,
        "Unrecognized dynamic text remains unchanged");
    require(
        ForgematicaMessages.resolve("Custom preview image set!", new Object[0]) == null,
        "Changed provider text remains unchanged");
    ClassNode messages = provider("fi/dy/masa/malilib/util/InfoUtils");
    for (String name :
        List.of("showGuiOrInGameMessage", "showGuiAndInGameMessage", "showGuiOrActionBarMessage")) {
      method(
          messages,
          name,
          "(Lfi/dy/masa/malilib/gui/Message$MessageType;ILjava/lang/String;[Ljava/lang/Object;)V");
    }
  }

  private static void inventoryMovementCompatibility() throws IOException {
    ClassNode movement = provider("me/pieking1215/invmove/InvMove");
    method(movement, "registerModule", "(Lme/pieking1215/invmove/module/Module;)V");
    require(
        (method(
                        movement,
                        "literalComponent",
                        "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;")
                    .access
                & Opcodes.ACC_STATIC)
            == 0,
        "Native literal helper remains an instance method for its instance injector");
    method(provider("dev/emi/emi/api/EmiApi"), "isSearchFocused", "()Z");
    ClassNode enums = provider("me/pieking1215/invmove/module/config/ConfigEnum");
    long selectors =
        enums.methods.stream()
            .filter(candidate -> candidate.name.equals("addTo"))
            .flatMap(candidate -> executable(candidate).stream())
            .filter(
                instruction ->
                    instruction instanceof MethodInsnNode call
                        && call.owner.equals("me/shedaniel/clothconfig2/api/ConfigEntryBuilder")
                        && call.name.equals("startEnumSelector")
                        && call.desc.equals(
                            "(Lnet/minecraft/network/chat/Component;Ljava/lang/Class;Ljava/lang/Enum;)Lme/shedaniel/clothconfig2/impl/builders/EnumSelectorBuilder;"))
            .count();
    require(selectors == 2, "Both native inventory-movement enum editor paths remain covered");
    ClassNode storage = provider("net/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase");
    method(storage, "isTextBoxFocused", "()Z");
    require(
        storage.fields.stream()
            .anyMatch(
                field ->
                    field.name.equals("modalOverlay")
                        && field.desc.equals(
                            "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/controls/WidgetBase;")),
        "Storage modal input guard uses the pinned native overlay field");
    require(
        provider("com/tom/storagemod/screen/AbstractStorageTerminalScreen").fields.stream()
            .anyMatch(
                field ->
                    field.name.equals("popup")
                        && field.desc.equals("Lcom/tom/storagemod/util/PopupMenuManager;")),
        "Terminal popup accessor retains the native manager type");
    require(
        provider("com/tom/storagemod/util/PopupMenuManager").fields.stream()
            .anyMatch(field -> field.name.equals("menu") && field.desc.equals("Ljava/util/List;")),
        "Terminal modal accessor retains the native popup list type");
  }

  private static ClassNode provider(String name) throws IOException {
    ClassNode type = new ClassNode();
    new ClassReader(providerResource(name + ".class")).accept(type, 0);
    return type;
  }

  private static byte[] providerResource(String name) throws IOException {
    for (String file : System.getProperty("aeroscapes.atmosphere.apis").split(File.pathSeparator)) {
      try (ZipFile archive = new ZipFile(Path.of(file).toFile())) {
        var entry = archive.getEntry(name);
        if (entry == null) continue;
        try (var input = archive.getInputStream(entry)) {
          return input.readAllBytes();
        }
      }
    }
    throw new AssertionError("Missing provider resource " + name);
  }

  private static MethodNode method(ClassNode type, String name, String descriptor) {
    return type.methods.stream()
        .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Changed provider method " + type.name + "." + name));
  }

  private static List<AbstractInsnNode> executable(MethodNode method) {
    List<AbstractInsnNode> result = new ArrayList<>();
    for (var instruction : method.instructions)
      if (instruction.getOpcode() >= 0) result.add(instruction);
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
