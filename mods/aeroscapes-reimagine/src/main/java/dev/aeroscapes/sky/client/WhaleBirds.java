package dev.aeroscapes.sky.client;

import dev.dacite.skywhales.entity.SkyWhale;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only swifts around some whales: a few circling birds, no entities, spawned once per
 * approach and removed when the whale leaves the view range.
 */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WhaleBirds {
  public static final double RANGE = 96.0;
  private static final double FORGET_MARGIN = 24.0;
  public static final int ACCOMPANIED_PERCENT = 60;
  public static final int MINIMUM_BIRDS = 3;
  public static final int MAXIMUM_BIRDS = 6;
  private static final ResourceLocation[] FRAMES = {
    ResourceLocation.fromNamespaceAndPath("atmospheric-fauna", "common_swift_flying_1"),
    ResourceLocation.fromNamespaceAndPath("atmospheric-fauna", "common_swift_flying_2")
  };
  private static final ResourceLocation PARTICLE_ATLAS =
      ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
  private static final IntSet ACCOMPANIED = new IntOpenHashSet();
  private static @Nullable Object accompaniedLevel;

  private WhaleBirds() {}

  /** Stable per whale: the same whale is always accompanied or always alone. */
  public static int birds(long mostSignificant, long leastSignificant) {
    long mixed = mostSignificant ^ Long.rotateLeft(leastSignificant, 17);
    mixed ^= mixed >>> 31;
    mixed *= 0x9E3779B97F4A7C15L;
    mixed ^= mixed >>> 29;
    int roll = (int) Long.remainderUnsigned(mixed, 100);
    if (roll >= ACCOMPANIED_PERCENT) return 0;
    return MINIMUM_BIRDS + roll % (MAXIMUM_BIRDS - MINIMUM_BIRDS + 1);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    if (level != accompaniedLevel) {
      ACCOMPANIED.clear();
      accompaniedLevel = level;
    }
    if (level == null) return;
    if (minecraft.isPaused()
        || level.getGameTime() % 20 != 0
        || !ModList.get().isLoaded("skywhales")) return;
    // Without Atmospheric Fauna its sprites are absent and no birds are drawn.
    var frames = frames(minecraft);
    if (frames == null) return;
    var camera = minecraft.gameRenderer.getMainCamera().getPosition();
    var kept = new IntOpenHashSet();
    for (var whale :
        level.getEntitiesOfClass(
            SkyWhale.class,
            new net.minecraft.world.phys.AABB(camera, camera).inflate(RANGE + FORGET_MARGIN))) {
      double distance = whale.distanceToSqr(camera);
      // Forget a whale only well past the spawn range, so crossing it does not double a flock.
      if (distance < (RANGE + FORGET_MARGIN) * (RANGE + FORGET_MARGIN)) kept.add(whale.getId());
      if (distance >= RANGE * RANGE || !ACCOMPANIED.add(whale.getId())) continue;
      var id = whale.getUUID();
      int count = birds(id.getMostSignificantBits(), id.getLeastSignificantBits());
      for (int index = 0; index < count; index++) {
        minecraft.particleEngine.add(
            new WhaleBirdParticle(level, whale, frames, index, id.getLeastSignificantBits()));
      }
    }
    ACCOMPANIED.retainAll(kept);
  }

  private static TextureAtlasSprite @Nullable [] frames(Minecraft minecraft) {
    if (!(minecraft.getTextureManager().getTexture(PARTICLE_ATLAS) instanceof TextureAtlas atlas))
      return null;
    var sprites = new TextureAtlasSprite[FRAMES.length];
    for (int index = 0; index < FRAMES.length; index++) {
      sprites[index] = atlas.getSprite(FRAMES[index]);
      if (sprites[index].contents().name().equals(MissingTextureAtlasSprite.getLocation()))
        return null;
    }
    return sprites;
  }
}
