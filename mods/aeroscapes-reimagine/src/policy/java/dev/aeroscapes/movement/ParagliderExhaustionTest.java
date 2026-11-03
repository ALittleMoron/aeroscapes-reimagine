package dev.aeroscapes.movement;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes the pinned provider's update branches against a client-free movement fixture. */
public final class ParagliderExhaustionTest {
  private static final String PROVIDER = "tictim/paraglider/impl/movement/ClientPlayerMovement";
  private static final String FIXTURE = Type.getInternalName(Fixture.class);

  private ParagliderExhaustionTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    Method nativeUpdate = update(false);
    Method guardedUpdate = update(true);
    Fixture original = depleted();
    nativeUpdate.invoke(null, original);
    require(
        !original.sprinting && !original.swimming, "Native depletion cancels ordinary movement");

    Fixture running = depleted();
    for (int tick = 0; tick < 40; tick++) guardedUpdate.invoke(null, running);
    require(
        running.sprinting && running.swimming && running.sprintChanges == 0,
        "Holding sprint remains stable throughout depletion when running costs are disabled");
    require(
        running.staminaUpdates == 40 && running.movementUpdates == 40,
        "The native stamina and movement updates still run every tick");

    Fixture configured = depleted();
    configured.runningConsumesStamina = true;
    guardedUpdate.invoke(null, configured);
    require(
        !configured.sprinting && !configured.swimming, "Configured running costs keep exhaustion");
    Fixture gliding = depleted();
    gliding.paragliding = true;
    guardedUpdate.invoke(null, gliding);
    require(
        !gliding.sprinting
            && !gliding.swimming
            && gliding.wasParagliding
            && gliding.deploySounds == 0,
        "Exhausted paragliding retains native cancellation and state bookkeeping");

    Fixture refreshed = new Fixture();
    guardedUpdate.invoke(null, refreshed);
    require(refreshed.sprinting && refreshed.swimming, "Available stamina leaves movement alone");
    Fixture landed = depleted();
    landed.wasParagliding = true;
    guardedUpdate.invoke(null, landed);
    require(
        !landed.sprinting && !landed.swimming && !landed.wasParagliding,
        "Exhausted glide exit clears the previous pose once");
    landed.sprinting = true;
    landed.swimming = true;
    guardedUpdate.invoke(null, landed);
    require(
        landed.sprinting && landed.swimming && landed.sprintChanges == 1,
        "Holding Ctrl can resume ordinary movement after the one-time glide cleanup");
    Fixture newlyDepleted = new Fixture();
    newlyDepleted.depleteOnUpdate = true;
    newlyDepleted.runningConsumesStamina = true;
    guardedUpdate.invoke(null, newlyDepleted);
    require(!newlyDepleted.sprinting, "Exhaustion is read after the native stamina update");
    Fixture creative = depleted();
    creative.creative = true;
    creative.fallFlying = true;
    guardedUpdate.invoke(null, creative);
    require(
        creative.sprinting && creative.swimming && creative.fallFlying,
        "Creative movement still bypasses depletion cancellation");

    Fixture transition = new Fixture();
    transition.paragliding = true;
    guardedUpdate.invoke(null, transition);
    require(
        transition.sprinting && transition.sprintChanges == 1 && transition.deploySounds == 1,
        "The separate native glider-pose sprint call and deploy sound remain active");
    transition.paragliding = false;
    guardedUpdate.invoke(null, transition);
    require(
        !transition.sprinting && transition.sprintChanges == 2 && transition.deploySounds == 1,
        "Leaving the glider preserves the native pose transition");
    Fixture elytra = depleted();
    elytra.fallFlying = true;
    guardedUpdate.invoke(null, elytra);
    require(!elytra.fallFlying && elytra.sprinting, "Native elytra exhaustion remains independent");
    System.out.println(
        "Paraglider exhaustion: native client update branches and guarded movement passed");
  }

  private static Fixture depleted() {
    Fixture fixture = new Fixture();
    fixture.depleted = true;
    return fixture;
  }

  private static Method update(boolean guarded) throws IOException, ReflectiveOperationException {
    ClassNode provider = new ClassNode();
    try (var stream =
        ParagliderExhaustionTest.class.getClassLoader().getResourceAsStream(PROVIDER + ".class")) {
      if (stream == null) throw new AssertionError("Missing pinned Paraglider client movement");
      new ClassReader(stream).accept(provider, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    var update =
        provider.methods.stream()
            .filter(method -> method.name.equals("update") && method.desc.equals("()V"))
            .findFirst()
            .orElseThrow();
    int sprintCalls = 0;
    int swimCalls = 0;
    for (var instruction : update.instructions) {
      if (instruction instanceof FieldInsnNode field) {
        require(
            field.owner.equals(PROVIDER), "Only native movement bookkeeping fields are remapped");
        field.owner = FIXTURE;
      } else if (instruction instanceof MethodInsnNode call) {
        if (call.name.equals("setSprinting")) {
          if (guarded && sprintCalls == 0) call.name = "stopSprintingIfRequired";
          sprintCalls++;
        } else if (call.name.equals("setSwimming")) {
          if (guarded) call.name = "stopSwimmingIfRequired";
          swimCalls++;
        }
        require(
            call.owner.equals(PROVIDER)
                || call.owner.equals("net/minecraft/client/player/LocalPlayer")
                || call.owner.equals("tictim/paraglider/api/movement/PlayerState")
                || call.owner.equals("tictim/paraglider/api/stamina/Stamina")
                || call.owner.equals("tictim/paraglider/ParagliderUtils"),
            "Unexpected native update dependency: " + call.owner);
        call.owner = FIXTURE;
        call.desc =
            call.desc
                .replace("Lnet/minecraft/client/player/LocalPlayer;", "L" + FIXTURE + ";")
                .replace("Lnet/minecraft/world/entity/player/Player;", "L" + FIXTURE + ";")
                .replace("Ltictim/paraglider/api/movement/PlayerState;", "L" + FIXTURE + ";")
                .replace("Ltictim/paraglider/api/stamina/Stamina;", "L" + FIXTURE + ";");
        if (call.getOpcode() == Opcodes.INVOKEINTERFACE) call.setOpcode(Opcodes.INVOKEVIRTUAL);
        call.itf = false;
      }
    }
    require(
        sprintCalls == 2 && swimCalls == 1,
        "Pinned cancellation and glider-pose call sites changed");
    update.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
    update.desc = "(L" + FIXTURE + ";)V";
    ClassWriter generated = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    generated.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC,
        "dev/aeroscapes/movement/NativeParagliderUpdate" + (guarded ? "Guarded" : "Original"),
        null,
        "java/lang/Object",
        null);
    update.accept(generated);
    generated.visitEnd();
    return MethodHandles.lookup()
        .defineClass(generated.toByteArray())
        .getMethod("update", Fixture.class);
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }

  public static final class Fixture {
    public boolean wasParagliding;
    public boolean depleted;
    public boolean depleteOnUpdate;
    public boolean creative;
    public boolean paragliding;
    public boolean runningConsumesStamina;
    public boolean sprinting = true;
    public boolean swimming = true;
    public boolean fallFlying;
    public int sprintChanges;
    public int deploySounds;
    public int staminaUpdates;
    public int movementUpdates;

    public Fixture player() {
      return this;
    }

    public Fixture state() {
      return this;
    }

    public Fixture stamina() {
      return this;
    }

    public boolean isCreative() {
      return creative;
    }

    public boolean isDepleted() {
      return depleted;
    }

    public boolean paragliding() {
      return paragliding;
    }

    public boolean isFallFlying() {
      return fallFlying;
    }

    public void stopFallFlying() {
      fallFlying = false;
    }

    public void updateStamina() {
      staminaUpdates++;
      depleted |= depleteOnUpdate;
    }

    public void applyMovement() {
      movementUpdates++;
    }

    public void setSprinting(boolean value) {
      sprinting = value;
      sprintChanges++;
    }

    public void setSwimming(boolean value) {
      swimming = value;
    }

    public static void playParagliderDeploySound(Fixture player) {
      player.deploySounds++;
    }

    public void stopSprintingIfRequired(boolean value) {
      if (ParagliderExhaustion.shouldStopMovement(
          runningConsumesStamina, paragliding, wasParagliding)) setSprinting(value);
    }

    public void stopSwimmingIfRequired(boolean value) {
      if (ParagliderExhaustion.shouldStopMovement(
          runningConsumesStamina, paragliding, wasParagliding)) setSwimming(value);
    }
  }
}
