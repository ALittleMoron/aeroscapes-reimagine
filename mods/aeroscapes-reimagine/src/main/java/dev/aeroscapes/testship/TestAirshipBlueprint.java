package dev.aeroscapes.testship;

import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.SavedBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasData;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasHolder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Vector3d;

/** Imports Tommobean's complete Basic Aeronautics Airship through Create's native print path. */
public final class TestAirshipBlueprint {
  public static final BlockPos SEAT = new BlockPos(0, 1, 2);
  public static final BlockPos BURNER = new BlockPos(0, 1, 1);
  public static final BlockPos LIFT_LEVER = new BlockPos(-1, 1, 3);
  public static final BlockPos DRIVE_LEVER = new BlockPos(1, 1, 3);
  public static final BlockPos ENGINE = new BlockPos(0, 6, 1);
  public static final BlockPos FORWARD_PROPELLER = new BlockPos(0, 6, -4);
  public static final BlockPos REAR_PROPELLER = new BlockPos(0, 6, 6);
  public static final BlockPos WHEEL = new BlockPos(0, 1, 4);
  public static final BlockPos BEARING = new BlockPos(0, 0, -3);
  private static final BlockPos OFFSET = new BlockPos(-2, -1, -4);
  private final SchematicLevel schematic;
  private final Map<BlockPos, BlockState> blocks;

  private TestAirshipBlueprint(SchematicLevel schematic, Map<BlockPos, BlockState> blocks) {
    this.schematic = schematic;
    this.blocks = blocks;
  }

  public static TestAirshipBlueprint load(ServerLevel level) throws IOException {
    Map.of("aeronautics", "1.3.2", "simulated", "1.3.2", "create", "6.0.10", "sable", "2.0.6")
        .forEach(
            (id, expected) -> {
              String actual =
                  net.neoforged.fml.ModList.get()
                      .getModContainerById(id)
                      .orElseThrow()
                      .getModInfo()
                      .getVersion()
                      .toString();
              if (!expected.equals(actual))
                throw new IllegalStateException(
                    "Review test-airship import for " + id + " " + actual);
            });
    var path = FMLPaths.CONFIGDIR.get().resolve("aeroscapes_reimagine/test_airship.nbt");
    if (Files.size(path) != 2726) throw new IOException("Unexpected test-airship file size");
    byte[] bytes = Files.readAllBytes(path);
    try {
      if (!HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
          .equals("2ec7d3a941cf644b4aa8abda8357db20243cbfd92b4c3994296323d7e2e67ac2"))
        throw new IOException("Test-airship checksum mismatch");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    CompoundTag tag =
        NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(1048576));
    if (tag.contains("sub_levels")) throw new IOException("Unexpected serialized sublevels");
    StructureTemplate template = new StructureTemplate();
    template.load(level.holderLookup(Registries.BLOCK), tag);
    SchematicLevel schematic = new SchematicLevel(level);
    if (!template.placeInWorld(
        schematic,
        BlockPos.ZERO,
        BlockPos.ZERO,
        new StructurePlaceSettings().setIgnoreEntities(false),
        level.getRandom(),
        2)) throw new IOException("Empty test-airship template");
    StructureTransform transform =
        new StructureTransform(BlockPos.ZERO, Axis.Y, Rotation.NONE, Mirror.NONE);
    for (BlockEntity entity : schematic.getBlockEntities()) transform.apply(entity);
    Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    for (BlockPos position : schematic.getAllPositions()) {
      BlockState state = schematic.getBlockState(position);
      if (!state.isAir()) blocks.put(position.offset(OFFSET).immutable(), state);
    }
    if (blocks.size() != 166) throw new IOException("Incomplete test-airship palette");
    return new TestAirshipBlueprint(schematic, blocks);
  }

  public Map<BlockPos, BlockState> blocks() {
    return blocks;
  }

  public void place(ServerSubLevel ship) {
    ServerLevel level = ship.getLevel();
    BlockPos origin = ship.getPlot().getCenterBlock();
    blocks.forEach(
        (relative, state) ->
            BlockHelper.placeSchematicBlock(level, state, origin.offset(relative), null, null));
    blocks.forEach(
        (relative, state) -> {
          BlockPos target = origin.offset(relative);
          if (level.getBlockEntity(target) instanceof ExtraKinetics extra
              && extra.getExtraKinetics() != null) extra.getExtraKinetics().warnOfMovement();
          BlockEntity source = schematic.getBlockEntity(relative.subtract(OFFSET));
          CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, source);
          if (source instanceof RedstoneLinkBlockEntity) {
            if (data == null) throw new IllegalStateException("Missing native radio settings");
            boolean lift =
                data.getCompound("FrequencyFirst")
                    .getString("id")
                    .equals("aeronautics:adjustable_burner");
            long token = ship.getUniqueId().getLeastSignificantBits();
            ItemStack first = new ItemStack(Items.LEATHER_HELMET),
                second = new ItemStack(Items.LEATHER_BOOTS);
            first.set(
                DataComponents.DYED_COLOR,
                new DyedItemColor((int) (token >>> 24) & 0xffffff, false));
            second.set(
                DataComponents.DYED_COLOR,
                new DyedItemColor(((int) token ^ (lift ? 0 : 1)) & 0xffffff, false));
            data.put("FrequencyFirst", first.saveOptional(level.registryAccess()));
            data.put("FrequencyLast", second.saveOptional(level.registryAccess()));
          }
          BlockHelper.placeSchematicBlock(level, state, target, null, data);
        });
    for (Entity glue : schematic.getEntityList()) {
      glue.setPos(glue.position().add(Vec3.atLowerCornerOf(origin.offset(OFFSET))));
      if (!level.addFreshEntity(glue)) throw new IllegalStateException("Native glue rejected");
    }
  }

  public static <T extends BlockEntity> T entity(
      ServerSubLevel ship, BlockPos relative, Class<T> type) {
    BlockEntity found =
        ship.getLevel().getBlockEntity(ship.getPlot().getCenterBlock().offset(relative));
    if (!type.isInstance(found))
      throw new IllegalStateException("Expected " + type.getSimpleName() + " at " + relative);
    return type.cast(found);
  }

  public static void initialize(ServerSubLevel ship, Set<UUID> allocated) {
    ServerLevel level = ship.getLevel();
    var container = SubLevelContainer.getContainer(level);
    for (BlockPos relative :
        List.of(
            new BlockPos(-1, -1, 3),
            new BlockPos(1, -1, 3),
            new BlockPos(0, 1, 0),
            new BlockPos(1, 6, -2),
            new BlockPos(1, 6, 4))) entity(ship, relative, RedstoneLinkBlockEntity.class).tick();
    PortableEngineBlockEntity engine = entity(ship, ENGINE, PortableEngineBlockEntity.class);
    if (!engine.inventory.insertSlot(new ItemStack(Items.COAL_BLOCK, 64), 0, false).isEmpty())
      throw new IllegalStateException("Engine rejected fuel");
    engine.tick();
    engine.tick();
    entity(ship, DRIVE_LEVER, ThrottleLeverBlockEntity.class).setSignal(15);
    var bearing = entity(ship, BEARING, SwivelBearingBlockEntity.class);
    Set<UUID> before = new HashSet<>();
    for (var body : container.getAllSubLevels()) before.add(body.getUniqueId());
    try {
      bearing.assemble();
    } finally {
      for (var body : container.getAllSubLevels())
        if (!before.contains(body.getUniqueId())) allocated.add(body.getUniqueId());
    }
    if (!bearing.isAssembled()
        || bearing.getSubLevelID() == null
        || bearing.getLastAssemblyException() != null)
      throw new IllegalStateException(
          "Native steering assembly failed: " + bearing.getLastAssemblyException());
    if (!(container.getSubLevel(bearing.getSubLevelID()) instanceof ServerSubLevel child))
      throw new IllegalStateException("Native steering child missing");
    if (!child
        .getUserDataTag()
        .getCompound(TestShips.MARKER)
        .getUUID("root")
        .equals(ship.getUniqueId()))
      throw new IllegalStateException("Native steering ownership was not captured");
    HotAirBurnerBlockEntity burner = entity(ship, BURNER, HotAirBurnerBlockEntity.class);
    burner.tick();
    burner.tickBalloonLogic();
    if (!(burner.getBalloon() instanceof ServerBalloon balloon))
      throw new IllegalStateException("Downloaded envelope did not form a native balloon");
    Vec3 center = balloon.getCenter();
    Vector3d world =
        ship.logicalPose().transformPosition(new Vector3d(center.x, center.y, center.z));
    double pressure = DimensionPhysicsData.getAirPressure(level, world),
        strength = burner.getLiftingGasType().getLiftStrength();
    double mass = ship.getMassTracker().getMass() + child.getMassTracker().getMass();
    if (balloon.getCapacity() * strength * pressure < mass * 1.02)
      throw new IllegalStateException(
          "Not enough native lift at this altitude; create the test airship lower");
    ScrollValueBehaviour amount = burner.getBehaviour(ScrollValueBehaviour.TYPE);
    double neutral = mass / (strength * pressure);
    int signal = 8;
    amount.setValue((int) (Math.ceil(neutral * 15 / signal / 5) * 5));
    entity(ship, LIFT_LEVER, ThrottleLeverBlockEntity.class).setSignal(signal);
    burner.updateSignal();
    CompoundTag burnerTag = burner.saveCustomOnly(level.registryAccess());
    burnerTag.putInt("SignalStrength", signal);
    burner.loadCustomOnly(burnerTag, level.registryAccess());
    double gas = Math.min(balloon.getCapacity(), amount.value * signal / 15.0);
    balloon.loadFrom(
        new SavedBalloon(
            new BoundingBox3i(balloon.getBounds()),
            balloon.getControllerPos(),
            List.of(
                new LiftingGasHolder(
                    burner.getLiftingGasType(), new LiftingGasData(gas, gas, 0)))));
    balloon.tick();
    BalloonMap.MAP.get(level).markDirty();
    com.mojang.logging.LogUtils.getLogger()
        .info(
            "Downloaded airship initialized: mass={} pressure={} capacity={} filled={} lift={} burner={}",
            mass,
            pressure,
            balloon.getCapacity(),
            balloon.getTotalFilledVolume(),
            balloon.getTotalLift(),
            amount.value);
  }

  public static void stop(ServerSubLevel ship) {
    BlockEntity found =
        ship.getLevel().getBlockEntity(ship.getPlot().getCenterBlock().offset(DRIVE_LEVER));
    if (found instanceof ThrottleLeverBlockEntity lever) lever.setSignal(15);
    BlockEntity steering =
        ship.getLevel().getBlockEntity(ship.getPlot().getCenterBlock().offset(WHEEL));
    if (steering
        instanceof
        dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity wheel)
      wheel.stopHolding();
  }
}
