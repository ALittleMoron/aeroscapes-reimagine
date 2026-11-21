package dev.aeroscapes.atmosphere;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Exercises native lookup, discovery, close ownership and custom-renderer fallback across reloads.
 */
public final class ItemModelResourcesTest {
  private ItemModelResourcesTest() {}

  public static void main(String[] arguments) throws ReflectiveOperationException, IOException {
    require(ItemModelResources.requested() && ItemModelResources.enabled(), "3D is the default");
    var config = CommentedConfig.inMemory();
    config.set("celestialSky", false);
    config.set("itemModels3d", true);
    PresentationConfig.SPEC.correct(config);
    var constructor =
        Class.forName("net.neoforged.fml.config.LoadedConfig")
            .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
    constructor.setAccessible(true);
    PresentationConfig.SPEC.acceptConfig(
        (IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    try {
      for (boolean use3d : new boolean[] {true, false, true}) {
        PresentationConfig.ITEM_MODELS_3D.set(use3d);
        resources(use3d);
        require(ItemModelResources.enabled() == use3d, "Apply mode with client resources");
        require(!PresentationConfig.CELESTIAL_SKY.get(), "Preserve the independent sky preference");
        customRenderers(use3d);
        staffPerspective(use3d);
      }
      ItemModelResources.prepare(PackType.CLIENT_RESOURCES, List.of());
      staffPerspective(true);
      var layered = packs();
      layered.add(
          new MemoryPack(
              "higher-priority",
              Map.of("hexcasting:models/item/equipment/held/staff/oak.json", "custom oak")));
      try (var withOverride = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, layered)) {
        ItemModelResources.modelsBaked(withOverride);
        staffPerspective(true, false);
      }
      try (var withoutPack = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of())) {
        ItemModelResources.modelsBaked(withoutPack);
        staffPerspective(false);
      }
      PresentationConfig.ITEM_MODELS_3D.set(false);
      require(ItemModelResources.enabled(), "Unsaved edits cannot change loaded render seams");
      var data = packs();
      require(
          ItemModelResources.prepare(PackType.SERVER_DATA, data) == data,
          "Server datapacks are never wrapped");
      require(ItemModelResources.enabled(), "Server reload cannot change client presentation");
      data.forEach(PackResources::close);
    } finally {
      PresentationConfig.SPEC.acceptConfig(null);
      ItemModelResources.prepare(PackType.CLIENT_RESOURCES, List.of());
    }
    System.out.println(
        "Item models: 3D/2D/3D resources, states, render seams and lifecycle passed");
  }

  private static void resources(boolean use3d) throws IOException {
    var packs = packs();
    var selected = ItemModelResources.prepare(PackType.CLIENT_RESOURCES, packs);
    if (use3d) require(selected == packs, "3D retains the original resource stack");
    try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, selected)) {
      ItemModelResources.modelsBaked(manager);
      require(
          manager
              .listPacks()
              .map(PackResources::packId)
              .toList()
              .equals(packs.stream().map(PackResources::packId).toList()),
          "Preserve selected pack order and close ownership");
      for (String path :
          List.of("models/item/diamond_sword.json", "models/item/bow_pulling_2.json")) {
        var id = ResourceLocation.withDefaultNamespace(path);
        require(read(manager, id).equals(use3d ? "sculpted" : "native"), "Native state: " + path);
        require(manager.getResourceStack(id).size() == (use3d ? 3 : 1), "Filter state stacks");
        var listed = manager.listResources("models", location -> location.equals(id));
        require(listed.size() == 1, "Discover the effective model");
        try (var stream = listed.get(id).open()) {
          require(
              new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                  .equals(use3d ? "sculpted" : "native"),
              "Listed and direct models agree");
        }
        require(
            manager.listResourceStacks("models", location -> location.equals(id)).get(id).size()
                == (use3d ? 3 : 1),
            "Model discovery cannot restore hidden state overrides");
      }
      require(
          manager
                  .getResource(ResourceLocation.withDefaultNamespace("items/trident.json"))
                  .isPresent()
              == use3d,
          "Modefite discovers the Refined trident only in 3D");
      require(
          manager.listResources("items", location -> true).size() == (use3d ? 1 : 0),
          "Modefite enumeration follows the same toggle");
      require(
          read(manager, ResourceLocation.parse("yo_hooks:models/item/iron_grappling_hook.json"))
              .equals(use3d ? "sculpted" : "native"),
          "Atmosphere hook geometry returns to the provider");
      require(
          read(manager, ResourceLocation.parse("nutritionalbalance:models/item/lunchbox.json"))
              .equals(use3d ? "sculpted" : "native"),
          "Native lunchbox renderer root is restored");
      for (String path :
          List.of("punchy/compat/aeroscapes_melee.json", "sounds.json", "models/block/sky.json"))
        require(
            read(manager, ResourceLocation.withDefaultNamespace(path)).equals("atmosphere"),
            "Retain independent animation, sounds and world presentation: " + path);
      require(
          read(manager, ResourceLocation.withDefaultNamespace("models/item/stone.json"))
              .equals("native"),
          "Native volumetric block items remain native");
      require(
          manager
                  .getResource(
                      ResourceLocation.withDefaultNamespace("punchy/compat/equipment.json"))
                  .isPresent()
              == use3d,
          "Disable the added geometry's grip profiles together");
    }
    for (var pack : packs)
      require(((MemoryPack) pack).closes == 1, "Close each underlying pack exactly once");
  }

  private static String read(MultiPackResourceManager manager, ResourceLocation id)
      throws IOException {
    try (var stream = manager.getResource(id).orElseThrow().open()) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static void customRenderers(boolean use3d) {
    var original =
        (BakedModel)
            Proxy.newProxyInstance(
                ItemModelResourcesTest.class.getClassLoader(),
                new Class<?>[] {BakedModel.class},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("applyTransform")) return proxy;
                  throw new AssertionError("Unexpected model call: " + method.getName());
                });
    for (var context : ItemDisplayContext.values()) {
      require(
          HeldEquipmentRendering.held(context) == (use3d && context.name().endsWith("_HAND")),
          "Custom body routing follows the loaded mode: " + context);
      if (!use3d) {
        require(HeldEquipmentRendering.lunchbox(context, original) == original, "Native lunchbox");
        require(
            HeldEquipmentRendering.altimeter(context, original) == original, "Native altimeter");
        require(HeldEquipmentRendering.sandpaper(context, original) == original, "Native partials");
        require(
            BucketHeldRendering.select(
                    original,
                    true,
                    false,
                    context,
                    () -> {
                      throw new AssertionError("2D must not construct the held bucket");
                    })
                == original,
            "Native resolved bucket state");
        require(
            CopperBucketHeldRendering.route(
                        original,
                        () -> {
                          throw new AssertionError("2D must not construct copper bucket volume");
                        })
                    .applyTransform(context, new PoseStack(), false)
                == original,
            "Native copper fluid model and transform");
      }
    }
    require(
        HeldEquipmentGrip.usesPalm(ResourceLocation.withDefaultNamespace("brush")) == use3d,
        "Only sculpted equipment uses the custom palm grip");
    var additional = new HashSet<ModelResourceLocation>();
    var event = new ModelEvent.RegisterAdditional(additional);
    HeldEquipmentRendering.register(event);
    BucketHeldRendering.register(event);
    CopperBucketHeldRendering.register(event);
    require(additional.isEmpty() != use3d, "Never bake hidden additional models in 2D");
  }

  private static void staffPerspective(boolean separate) {
    staffPerspective(separate, separate);
  }

  private static void staffPerspective(boolean separate, boolean oak) {
    for (boolean left : new boolean[] {false, true}) {
      var third =
          left
              ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
              : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      var first =
          left
              ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
      for (String item : List.of("staff/oak", "battery", "cypher", "trinket", "artifact", "focus"))
        require(
            HeldEquipmentGrip.punchyDisplay(
                    ResourceLocation.fromNamespaceAndPath("hexcasting", item), third, left, false)
                == ((item.equals("staff/oak") ? oak : separate) ? first : third),
            item + " pose must follow the loaded models in either hand");
      require(
          HeldEquipmentGrip.punchyDisplay(
                  ResourceLocation.parse("hexcasting:spellbook"), third, left, false)
              == third,
          "Keep Punchy's normal book attachment");
    }
  }

  private static List<PackResources> packs() {
    var nativeModels =
        Map.of(
            "minecraft:models/item/diamond_sword.json", "native",
            "minecraft:models/item/bow_pulling_2.json", "native",
            "minecraft:models/item/stone.json", "native",
            "yo_hooks:models/item/iron_grappling_hook.json", "native",
            "nutritionalbalance:models/item/lunchbox.json", "native");
    var refined =
        Map.of(
            "minecraft:models/item/diamond_sword.json", "refined",
            "minecraft:models/item/bow_pulling_2.json", "refined",
            "minecraft:items/trident.json", "definition");
    var owned =
        new java.util.HashMap<>(
            Map.of(
                "minecraft:models/item/diamond_sword.json", "sculpted",
                "minecraft:models/item/bow_pulling_2.json", "sculpted",
                "minecraft:punchy/compat/equipment.json", "grip"));
    for (String leaf :
        List.of("staff/oak", "phial_small_0", "cypher", "trinket", "artifact", "focus")) {
      owned.put("hexcasting:models/item/equipment/held/" + leaf + ".json", "geometry");
      owned.put("hexcasting:models/item/" + leaf + ".json", "selector");
    }
    var atmosphere =
        Map.of(
            "yo_hooks:models/item/iron_grappling_hook.json", "sculpted",
            "nutritionalbalance:models/item/lunchbox.json", "sculpted",
            "minecraft:punchy/compat/aeroscapes_melee.json", "atmosphere",
            "minecraft:sounds.json", "atmosphere",
            "minecraft:models/block/sky.json", "atmosphere");
    return new ArrayList<>(
        List.of(
            new MemoryPack("native", nativeModels),
            new MemoryPack("file/Refined Tools 3.0.zip", refined),
            new MemoryPack("file/aeroscapes-reimagine-refined-compat", owned),
            new MemoryPack("file/aeroscapes-reimagine-atmosphere", atmosphere)));
  }

  private static final class MemoryPack extends AbstractPackResources {
    private final Map<ResourceLocation, String> resources;
    private int closes;

    private MemoryPack(String id, Map<String, String> resources) {
      super(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()));
      this.resources = new java.util.LinkedHashMap<>();
      resources.forEach((key, value) -> this.resources.put(ResourceLocation.parse(key), value));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
      return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
      String content = resources.get(id);
      return content == null
          ? null
          : () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
      resources.forEach(
          (id, value) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(path))
              output.accept(id, getResource(type, id));
          });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
      var namespaces = new HashSet<String>();
      resources.keySet().forEach(id -> namespaces.add(id.getNamespace()));
      return namespaces;
    }

    @Override
    public void close() {
      closes++;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
