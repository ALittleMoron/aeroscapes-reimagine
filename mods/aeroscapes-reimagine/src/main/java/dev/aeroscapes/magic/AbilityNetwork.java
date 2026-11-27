package dev.aeroscapes.magic;

import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.SwingTempo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/** Client requests contain slot identities; the server supplies all knowledge and casting data. */
public final class AbilityNetwork {
  private AbilityNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    SwingTempo.register(event);
    var registrar = event.registrar("6");
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player) PreparedAbilities.sync(player, true);
        });
    registrar.playToServer(
        Edit.TYPE,
        Edit.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            PreparedAbilities.edit(player, message.wheel(), message.slot(), message.abilityId());
        });
    registrar.playToServer(
        Select.TYPE,
        Select.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            PreparedAbilities.select(player, message.wheel(), message.slot(), message.sequence());
        });
    registrar.playToServer(
        Cast.TYPE,
        Cast.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player
              && !dev.aeroscapes.races.RaceIdentity.awaitingChoice(player))
            PreparedAbilities.cast(player);
        });
    registrar.playToServer(
        FirstStudy.TYPE,
        FirstStudy.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            PreparedAbilities.firstStudy(player, message.containerId(), message.spellId());
        });
    registrar.playToClient(
        Snapshot.TYPE,
        Snapshot.CODEC,
        (message, context) -> dev.aeroscapes.magic.client.AbilityClient.accept(message));
    registrar.playToClient(
        StudyCompleted.TYPE,
        StudyCompleted.CODEC,
        (message, context) -> dev.aeroscapes.magic.client.AbilityClient.studyCompleted(message));
    registrar.playToClient(
        Footwork.TYPE,
        Footwork.CODEC,
        (message, context) -> dev.aeroscapes.magic.client.FootworkClient.start(message.travel()));
  }

  public record Footwork(Vec3 travel) implements CustomPacketPayload {
    public static final Type<Footwork> TYPE = AbilityNetwork.type("footwork");
    public static final StreamCodec<RegistryFriendlyByteBuf, Footwork> CODEC =
        StreamCodec.of(
            (buffer, message) -> buffer.writeVec3(message.travel()),
            buffer -> new Footwork(buffer.readVec3()));

    @Override
    public Type<Footwork> type() {
      return TYPE;
    }
  }

  public record Program(int inventorySlot, String customName) {}

  public record Entry(
      String id,
      String school,
      int level,
      int cap,
      int maxLevel,
      boolean usable,
      String reason,
      int cooldownTicks,
      int manaCost,
      @Nullable Program program) {
    public Entry(
        String id,
        String school,
        int level,
        int cap,
        int maxLevel,
        boolean usable,
        String reason,
        int cooldownTicks,
        int manaCost) {
      this(id, school, level, cap, maxLevel, usable, reason, cooldownTicks, manaCost, null);
    }

    public boolean innate() {
      return school.equals(InnateAbility.GROUP);
    }

    public boolean technique() {
      return school.equals(MartialTechnique.GROUP);
    }

    public boolean farming() {
      return school.equals(dev.aeroscapes.races.AnimalLure.GROUP);
    }

    public boolean known() {
      return (granted()
              && !reason.equals("race")
              && !reason.equals("hex_missing")
              && !reason.equals(dev.aeroscapes.races.AnimalLure.UNLEARNED))
          || level > 0;
    }

    public boolean granted() {
      return innate()
          || farming()
          || school.equals(dev.aeroscapes.magic.travel.AmuletStep.GROUP)
          || school.equals(PreparedAbilities.DISCOVERY_GROUP)
          || program != null;
    }
  }

  public record StudyEntry(
      String spellId, int casts, int requiredCasts, int requiredMastery, String reason) {}

  public record StudyCompleted(String spellId, int rank) implements CustomPacketPayload {
    public static final Type<StudyCompleted> TYPE = AbilityNetwork.type("study_completed");
    public static final StreamCodec<RegistryFriendlyByteBuf, StudyCompleted> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeUtf(message.spellId(), 128);
              buffer.writeVarInt(message.rank());
            },
            buffer -> new StudyCompleted(buffer.readUtf(128), buffer.readVarInt()));

    @Override
    public Type<StudyCompleted> type() {
      return TYPE;
    }
  }

  public record Snapshot(
      List<Entry> entries,
      AbilityLoadout loadout,
      int selectionAck,
      boolean dwarfNightVision,
      List<String> talents,
      int practiceLevel,
      long practiceXp,
      long nextTalentXp,
      List<StudyEntry> studyEntries)
      implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE = AbilityNetwork.type("abilities");
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeCollection(
                  message.entries(),
                  (output, entry) -> {
                    output.writeUtf(entry.id(), 128);
                    output.writeUtf(entry.school(), 128);
                    output.writeVarInt(entry.level());
                    output.writeVarInt(entry.cap());
                    output.writeVarInt(entry.maxLevel());
                    output.writeBoolean(entry.usable());
                    output.writeUtf(entry.reason(), 128);
                    output.writeVarInt(entry.cooldownTicks());
                    output.writeVarInt(entry.manaCost());
                    output.writeNullable(
                        entry.program(),
                        (buf, program) -> {
                          buf.writeVarInt(program.inventorySlot());
                          buf.writeUtf(program.customName(), 128);
                        });
                  });
              writeLoadout(buffer, message.loadout());
              buffer.writeVarInt(message.selectionAck());
              buffer.writeBoolean(message.dwarfNightVision());
              buffer.writeCollection(
                  message.talents(), (output, talent) -> output.writeUtf(talent, 128));
              buffer.writeVarInt(message.practiceLevel());
              buffer.writeVarLong(message.practiceXp());
              buffer.writeVarLong(message.nextTalentXp());
              buffer.writeCollection(
                  message.studyEntries(),
                  (output, entry) -> {
                    output.writeUtf(entry.spellId(), 128);
                    output.writeVarInt(entry.casts());
                    output.writeVarInt(entry.requiredCasts());
                    output.writeVarInt(entry.requiredMastery());
                    output.writeUtf(entry.reason(), 128);
                  });
            },
            buffer ->
                new Snapshot(
                    buffer.readList(
                        input ->
                            new Entry(
                                input.readUtf(128),
                                input.readUtf(128),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readBoolean(),
                                input.readUtf(128),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readNullable(
                                    buf -> new Program(buf.readVarInt(), buf.readUtf(128))))),
                    readLoadout(buffer),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readList(input -> input.readUtf(128)),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readList(
                        input ->
                            new StudyEntry(
                                input.readUtf(128),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readUtf(128)))));

    public Snapshot(
        List<Entry> entries,
        AbilityLoadout loadout,
        int selectionAck,
        boolean dwarfNightVision,
        List<String> talents,
        int practiceLevel,
        long practiceXp,
        long nextTalentXp) {
      this(
          entries,
          loadout,
          selectionAck,
          dwarfNightVision,
          talents,
          practiceLevel,
          practiceXp,
          nextTalentXp,
          List.of());
    }

    public Snapshot {
      entries = List.copyOf(entries);
      talents = List.copyOf(talents);
      studyEntries = List.copyOf(studyEntries);
    }

    public List<String> slots() {
      return loadout.active().slots();
    }

    public int selected() {
      return loadout.active().selected();
    }

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }
  }

  private static void writeLoadout(RegistryFriendlyByteBuf buffer, AbilityLoadout loadout) {
    buffer.writeByte(loadout.activeWheel());
    for (Preparation wheel : loadout.wheels()) {
      buffer.writeByte(wheel.selected());
      for (String id : wheel.slots()) buffer.writeUtf(id, 128);
    }
  }

  private static AbilityLoadout readLoadout(RegistryFriendlyByteBuf buffer) {
    int active = buffer.readUnsignedByte();
    var wheels = new ArrayList<Preparation>(AbilityLoadout.WHEEL_COUNT);
    for (int wheel = 0; wheel < AbilityLoadout.WHEEL_COUNT; wheel++) {
      int selected = buffer.readUnsignedByte();
      var slots = new ArrayList<String>(Preparation.SLOT_COUNT);
      for (int slot = 0; slot < Preparation.SLOT_COUNT; slot++) slots.add(buffer.readUtf(128));
      wheels.add(new Preparation(slots, selected));
    }
    return new AbilityLoadout(wheels, active);
  }

  public record Request() implements CustomPacketPayload {
    public static final Type<Request> TYPE = AbilityNetwork.type("abilities_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of((buffer, message) -> {}, buffer -> new Request());

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record FirstStudy(int containerId, String spellId) implements CustomPacketPayload {
    public static final Type<FirstStudy> TYPE = AbilityNetwork.type("abilities_first_study");
    public static final StreamCodec<RegistryFriendlyByteBuf, FirstStudy> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeVarInt(message.containerId());
              buffer.writeUtf(message.spellId(), 128);
            },
            buffer -> new FirstStudy(buffer.readVarInt(), buffer.readUtf(128)));

    @Override
    public Type<FirstStudy> type() {
      return TYPE;
    }
  }

  public record Edit(int wheel, int slot, String abilityId) implements CustomPacketPayload {
    public static final Type<Edit> TYPE = AbilityNetwork.type("abilities_edit");
    public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeByte(message.wheel());
              buffer.writeByte(message.slot());
              buffer.writeUtf(message.abilityId(), 128);
            },
            buffer -> new Edit(buffer.readByte(), buffer.readByte(), buffer.readUtf(128)));

    @Override
    public Type<Edit> type() {
      return TYPE;
    }
  }

  public record Select(int wheel, int slot, int sequence) implements CustomPacketPayload {
    public static final Type<Select> TYPE = AbilityNetwork.type("abilities_select");
    public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeByte(message.wheel());
              buffer.writeByte(message.slot());
              buffer.writeVarInt(message.sequence());
            },
            buffer -> new Select(buffer.readByte(), buffer.readByte(), buffer.readVarInt()));

    @Override
    public Type<Select> type() {
      return TYPE;
    }
  }

  public record Cast() implements CustomPacketPayload {
    public static final Type<Cast> TYPE = AbilityNetwork.type("abilities_cast");
    public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
        StreamCodec.of((buffer, message) -> {}, buffer -> new Cast());

    @Override
    public Type<Cast> type() {
      return TYPE;
    }
  }

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
    return new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path));
  }
}
