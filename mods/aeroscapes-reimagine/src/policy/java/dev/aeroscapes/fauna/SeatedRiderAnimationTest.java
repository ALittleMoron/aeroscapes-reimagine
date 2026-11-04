package dev.aeroscapes.fauna;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Executes Naturalist's nested rider rendering and the production wrapper without a client. */
public final class SeatedRiderAnimationTest {
  private static final String PROVIDER =
      "com/crispytwig/naturalist/client/renderer/entity/layers/SeatedRiderLayer";
  private static final String MIXIN = "dev/aeroscapes/mixin/fauna/NaturalistSeatedRiderMixin";
  private static final String FIXTURE = Type.getInternalName(Fixture.class);
  private static final String STATE = Type.getInternalName(RenderState.class);
  private static final String CONTEXT = Type.getInternalName(RenderContext.class);
  private static final String OPERATION = Type.getInternalName(RenderOperation.class);
  private static final String MATH = "traben/entity_model_features/models/animation/math/EMFMath";
  private static final String QUERIES = "dev/aeroscapes/fauna/NativeSeatedRiderQueries";
  private static final String RENDER =
      "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Mob;FFFFFF)V";
  private static final Map<String, String> TYPES =
      Map.ofEntries(
          Map.entry(PROVIDER, FIXTURE),
          Map.entry("com/crispytwig/naturalist/client/model/SeatedModel", FIXTURE),
          Map.entry("net/minecraft/world/entity/Entity", FIXTURE),
          Map.entry("net/minecraft/world/entity/Mob", FIXTURE),
          Map.entry("net/minecraft/world/entity/LivingEntity", FIXTURE),
          Map.entry("net/minecraft/world/entity/WalkAnimationState", FIXTURE),
          Map.entry(
              "net/minecraft/world/entity/vehicle/AbstractMinecart",
              Type.getInternalName(OtherVehicle.class)),
          Map.entry(
              "net/minecraft/world/entity/vehicle/Boat", Type.getInternalName(OtherVehicle.class)),
          Map.entry("net/minecraft/world/entity/player/Player", FIXTURE),
          Map.entry("net/minecraft/client/player/LocalPlayer", FIXTURE),
          Map.entry("net/minecraft/client/model/EntityModel", FIXTURE),
          Map.entry("net/minecraft/client/renderer/entity/EntityRenderer", FIXTURE),
          Map.entry("net/minecraft/client/renderer/entity/EntityRenderDispatcher", FIXTURE),
          Map.entry("net/minecraft/client/renderer/MultiBufferSource", FIXTURE),
          Map.entry("net/minecraft/client/Minecraft", FIXTURE),
          Map.entry("net/minecraft/client/Options", FIXTURE),
          Map.entry("net/minecraft/client/CameraType", FIXTURE),
          Map.entry("net/minecraft/util/Mth", FIXTURE),
          Map.entry("com/mojang/blaze3d/vertex/PoseStack", FIXTURE),
          Map.entry("com/mojang/math/Axis", FIXTURE),
          Map.entry("traben/entity_texture_features/utils/ETFEntity", FIXTURE),
          Map.entry("traben/entity_model_features/utils/EMFEntity", FIXTURE),
          Map.entry(
              "traben/entity_model_features/models/animation/state/EMFEntityRenderState", STATE),
          Map.entry("traben/entity_texture_features/features/state/ETFEntityRenderState", STATE),
          Map.entry("traben/entity_texture_features/features/state/ETFState", CONTEXT),
          Map.entry("traben/entity_model_features/models/animation/state/EMFState", CONTEXT),
          Map.entry("com/llamalad7/mixinextras/injector/wrapoperation/Operation", OPERATION));

  private SeatedRiderAnimationTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    queries();
    Method nativeRender = render(false);
    Method repairedRender = render(true);
    Fixture local = new Fixture();
    local.riding = true;
    local.walk = 0.8F;
    Fixture.INSTANCE.player = local;
    Fixture elephant = new Fixture();
    elephant.passenger = local;
    elephant.walk = 1;

    begin(elephant);
    invoke(nativeRender, elephant);
    require(Fixture.seen.entity == elephant, "Native nested render inherits the elephant state");
    require(
        !Fixture.seenRiding && Fixture.seenWalk == 1,
        "Native model queries see walking instead of a seated passenger");

    begin(elephant);
    invoke(repairedRender, elephant);
    require(Fixture.seen.entity == local, "The wrapper mounts the actual rider");
    require(
        Fixture.seenRiding && Fixture.seenWalk == 0,
        "Rider queries retain the seated player rather than the elephant's walk");
    restored(elephant);
    require(
        Fixture.pushes == 1 && Fixture.pops == 1 && Fixture.seats == 1,
        "The provider still applies its seat transforms exactly once");

    Fixture remote = new Fixture();
    remote.riding = true;
    remote.walk = 0.6F;
    elephant.passenger = remote;
    Fixture.INSTANCE.firstPerson = true;
    begin(elephant);
    invoke(repairedRender, elephant);
    require(
        Fixture.seen.entity == remote && Fixture.seen.entity != local,
        "A remote passenger must never use the local-player manual state");
    require(
        Fixture.seenRiding && Fixture.seenWalk == 0,
        "The native EMF queries suppress the remote passenger's walking clock");
    restored(elephant);

    elephant.passenger = local;
    begin(elephant);
    invoke(repairedRender, elephant);
    require(
        Fixture.seen == null && RenderContext.factories == 0 && Fixture.pushes == 0,
        "Naturalist's first-person exclusion remains before the wrapped call");
    restored(elephant);

    Fixture.INSTANCE.firstPerson = false;
    begin(elephant);
    Fixture.fail = true;
    try {
      invoke(repairedRender, elephant);
      throw new AssertionError("Expected renderer failure");
    } catch (InvocationTargetException failure) {
      require(failure.getCause() instanceof RenderFailure, "The native failure propagates");
    }
    require(Fixture.seen.entity == local, "The failing render still receives the actual rider");
    restored(elephant);

    elephant.passenger = null;
    begin(elephant);
    invoke(repairedRender, elephant);
    require(
        Fixture.seen == null && RenderContext.factories == 0,
        "An unoccupied mount keeps the native early return");
    restored(elephant);
    System.out.println(
        "Seated rider: native reproduction, actual passenger and state restoration passed");
  }

  private static void begin(Fixture elephant) {
    RenderContext.states.clear();
    RenderContext.states.push(new RenderState(elephant));
    RenderContext.factories = 0;
    RenderContext.isLayerPhase = true;
    RenderContext.isMainPhase = false;
    RenderContext.isRenderingFeatures = true;
    Fixture.seen = null;
    Fixture.fail = false;
    Fixture.pushes = Fixture.pops = Fixture.seats = 0;
  }

  private static void restored(Fixture elephant) {
    require(
        RenderContext.states.size() == 1 && RenderContext.states.peek().entity == elephant,
        "The outer elephant context is restored without a leaked rider state");
    require(
        RenderContext.isLayerPhase
            && !RenderContext.isMainPhase
            && RenderContext.isRenderingFeatures,
        "The outer feature-phase flags are restored");
  }

  private static void invoke(Method render, Fixture elephant) throws ReflectiveOperationException {
    render.invoke(
        null,
        new Fixture(),
        new Fixture(),
        new Fixture(),
        15728880,
        elephant,
        0F,
        1F,
        0.5F,
        20F,
        0F,
        0F);
  }

  private static Method render(boolean repaired) throws IOException, ReflectiveOperationException {
    ClassNode provider = read(PROVIDER);
    MethodNode render = method(provider, "render", RENDER);
    String generatedName =
        "dev/aeroscapes/fauna/NativeSeatedRider" + (repaired ? "Repaired" : "Original");
    ClassWriter generated = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    generated.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, generatedName, null, "java/lang/Object", null);
    MethodNode wrapper = method(read(MIXIN), "aeroscapes$riderState", null);
    require((wrapper.access & Opcodes.ACC_STATIC) != 0, "The scoped render wrapper is static");
    String wrapperDescriptor = remap(wrapper.desc);
    int calls = 0;
    for (var instruction : render.instructions.toArray()) {
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("net/minecraft/client/renderer/entity/EntityRenderer")
          && call.name.equals("render")) {
        calls++;
        if (repaired) {
          render.instructions.insertBefore(
              call,
              new FieldInsnNode(Opcodes.GETSTATIC, FIXTURE, "OPERATION", "L" + OPERATION + ";"));
          call.setOpcode(Opcodes.INVOKESTATIC);
          call.owner = generatedName;
          call.name = wrapper.name;
          call.desc = wrapperDescriptor;
          call.itf = false;
        }
      }
    }
    require(calls == 1, "Naturalist must retain one direct nested rider render");
    render.desc = "(L" + FIXTURE + ";" + render.desc.substring(1);
    prepare(render);
    render.accept(generated);
    if (repaired) {
      prepare(wrapper);
      wrapper.accept(generated);
    }
    generated.visitEnd();
    return MethodHandles.lookup()
        .defineClass(generated.toByteArray())
        .getMethod(
            "render",
            Fixture.class,
            Fixture.class,
            Fixture.class,
            int.class,
            Fixture.class,
            float.class,
            float.class,
            float.class,
            float.class,
            float.class,
            float.class);
  }

  private static void queries() throws IOException, ReflectiveOperationException {
    ClassNode provider = read(MATH);
    ClassWriter generated = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    generated.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, QUERIES, null, "java/lang/Object", null);
    for (String name : List.of("isRiding", "getLimbDistance", "doLimbValues", "emfState")) {
      MethodNode query = method(provider, name, null);
      for (var instruction : query.instructions) {
        if (instruction instanceof MethodInsnNode call && call.owner.equals(MATH)) {
          call.owner =
              List.of("getTickDelta", "getEntityX", "getEntityZ").contains(call.name)
                  ? FIXTURE
                  : QUERIES;
        }
      }
      prepare(query);
      query.accept(generated);
    }
    generated.visitEnd();
    Class<?> queries = MethodHandles.lookup().defineClass(generated.toByteArray());
    Fixture.ridingQuery = queries.getMethod("isRiding");
    Fixture.walkQuery = queries.getMethod("getLimbDistance");
  }

  private static void prepare(MethodNode method) {
    method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    method.desc = remap(method.desc);
    method.signature = null;
    method.visibleAnnotations = null;
    method.invisibleAnnotations = null;
    method.visibleParameterAnnotations = null;
    method.invisibleParameterAnnotations = null;
    for (var instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode call) {
        String mappedOwner = TYPES.get(call.owner);
        if (mappedOwner != null) {
          call.owner = mappedOwner;
          if (call.getOpcode() == Opcodes.INVOKEINTERFACE && !mappedOwner.equals(OPERATION)) {
            call.setOpcode(Opcodes.INVOKEVIRTUAL);
            call.itf = false;
          } else if (call.getOpcode() == Opcodes.INVOKESTATIC) {
            call.itf = false;
          }
        }
        call.desc = remap(call.desc);
      } else if (instruction instanceof FieldInsnNode field) {
        field.owner = TYPES.getOrDefault(field.owner, field.owner);
        field.desc = remap(field.desc);
      } else if (instruction instanceof TypeInsnNode type) {
        type.desc = TYPES.getOrDefault(type.desc, type.desc);
      }
    }
  }

  private static String remap(String descriptor) {
    String mapped = descriptor;
    for (var entry : TYPES.entrySet()) {
      mapped = mapped.replace("L" + entry.getKey() + ";", "L" + entry.getValue() + ";");
    }
    return mapped;
  }

  private static ClassNode read(String name) throws IOException {
    ClassNode node = new ClassNode();
    try (var stream = SeatedRiderAnimationTest.class.getResourceAsStream("/" + name + ".class")) {
      if (stream == null) throw new IOException("Missing pinned render boundary " + name);
      new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    return node;
  }

  private static MethodNode method(ClassNode owner, String name, String descriptor) {
    return owner.methods.stream()
        .filter(
            method ->
                method.name.equals(name) && (descriptor == null || method.desc.equals(descriptor)))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("Missing render contract " + owner.name + "." + name));
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }

  public static final class RenderState {
    private final Fixture entity;
    private float limbDistance = Float.NaN;
    private float limbAngle = Float.NaN;

    public RenderState(Fixture entity) {
      this.entity = entity;
    }

    public static RenderState forEntity(Fixture entity) {
      RenderContext.factories++;
      return new RenderState(entity);
    }

    public boolean hasVehicle() {
      return entity.riding;
    }

    public Fixture emfEntity() {
      return entity;
    }

    public float limbDistance() {
      return limbDistance;
    }

    public float limbAngle() {
      return limbAngle;
    }

    public void setLimbDistance(float value) {
      limbDistance = value;
    }

    public void setLimbAngle(float value) {
      limbAngle = value;
    }
  }

  public static final class RenderContext {
    public static final Deque<RenderState> states = new ArrayDeque<>();
    public static boolean isLayerPhase;
    public static boolean isMainPhase;
    public static boolean isRenderingFeatures;
    public static int factories;

    private RenderContext() {}

    public static void mount(RenderState state) {
      states.push(state);
    }

    public static void unMount() {
      states.pop();
    }

    public static RenderState state() {
      return states.peek();
    }
  }

  public interface RenderOperation {
    Object call(Object... arguments);
  }

  public static final class RenderFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static final class OtherVehicle {
    public float getRowingTime(int side, float partialTick) {
      return 0;
    }
  }

  /** The remapped Minecraft boundary records render inputs and keeps all native control flow. */
  public static final class Fixture {
    public static final Fixture INSTANCE = new Fixture();
    public static final Fixture ZP = new Fixture();
    public static final Fixture YP = new Fixture();
    public static final RenderOperation OPERATION =
        arguments -> {
          ((Fixture) arguments[0])
              .render(
                  (Fixture) arguments[1],
                  (Float) arguments[2],
                  (Float) arguments[3],
                  (Fixture) arguments[4],
                  (Fixture) arguments[5],
                  (Integer) arguments[6]);
          return null;
        };
    public static RenderState seen;
    public static boolean seenRiding;
    public static float seenWalk;
    public static Method ridingQuery;
    public static Method walkQuery;
    public static boolean fail;
    public static int pushes;
    public static int pops;
    public static int seats;
    public Fixture player;
    public final Fixture options = this;
    public final Fixture walkAnimation = this;
    public Fixture passenger;
    public boolean firstPerson;
    public boolean riding;
    public float walk;
    public float yBodyRot;
    public float yBodyRotO;

    public static Fixture getInstance() {
      return INSTANCE;
    }

    public Fixture getFirstPassenger() {
      return passenger;
    }

    public Fixture getParentModel() {
      return this;
    }

    public Fixture getCameraType() {
      return this;
    }

    public boolean isFirstPerson() {
      return firstPerson;
    }

    public Fixture getEntityRenderDispatcher() {
      return this;
    }

    public Fixture getRenderer(Fixture entity) {
      return this;
    }

    public void pushPose() {
      pushes++;
    }

    public void popPose() {
      pops++;
    }

    public void translateToSeat(Fixture pose) {
      seats++;
    }

    public float seatZRot() {
      return 0;
    }

    public float seatHeight() {
      return 0;
    }

    public Quaternionf rotationDegrees(float degrees) {
      return new Quaternionf();
    }

    public void mulPose(Quaternionf rotation) {}

    public void scale(float x, float y, float z) {}

    public void translate(float x, float y, float z) {}

    public static float rotLerp(float partialTick, float previous, float current) {
      return current;
    }

    public static float getTickDelta() {
      return 0.5F;
    }

    public static float getEntityX() {
      return 0;
    }

    public static float getEntityZ() {
      return 0;
    }

    public boolean isBaby() {
      return false;
    }

    public float position(float partialTick) {
      return 1;
    }

    public float speed(float partialTick) {
      return walk;
    }

    public void render(
        Fixture rider, float yaw, float partialTick, Fixture pose, Fixture buffers, int light) {
      seen = RenderContext.states.peek();
      try {
        seenRiding = (Boolean) ridingQuery.invoke(null);
        seenWalk = (Float) walkQuery.invoke(null);
      } catch (ReflectiveOperationException failure) {
        throw new AssertionError("Cannot evaluate the native EMF pose queries", failure);
      }
      RenderContext.isLayerPhase = false;
      RenderContext.isMainPhase = true;
      RenderContext.isRenderingFeatures = false;
      if (fail) throw new RenderFailure();
      RenderContext.isMainPhase = false;
    }
  }
}
