package dev.aeroscapes.equipment.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The saved roster owns membership even when a companion's chunk is unloaded. */
final class BeastmasterState extends SavedData {
  private static final String KEY = "aeroscapes_reimagine_beastmaster";
  private static final Factory<BeastmasterState> FACTORY =
      new Factory<>(BeastmasterState::new, BeastmasterState::load, null);
  private static final Owner EMPTY = new Owner(0, null);
  private final Map<UUID, Owner> owners = new HashMap<>();

  enum Species {
    FOX("fox", "minecraft:fox", 3, 10, 2),
    WOLF("wolf", "minecraft:wolf", 2, 18, 4),
    BEAR("bear", "naturalist:bear", 1, 30, 6);

    final String node;
    final ResourceLocation entityType;
    final int limit;
    final int health;
    final int damage;

    Species(String name, String entityType, int limit, int health, int damage) {
      node = "beastmaster_" + name;
      this.entityType = ResourceLocation.parse(entityType);
      this.limit = limit;
      this.health = health;
      this.damage = damage;
    }

    @Nullable
    static Species byNode(String node) {
      for (var species : values()) {
        if (species.node.equals(node)) return species;
      }
      return null;
    }
  }

  record Member(UUID identity, UUID entityId, ResourceLocation dimension, float health) {
    Member withHealth(float remaining) {
      return new Member(identity, entityId, dimension, remaining);
    }
  }

  record Group(
      Species species,
      long expiresAt,
      boolean stronger,
      boolean protectedGroup,
      @Nullable ResourceLocation destination,
      List<Member> members) {
    Group {
      members = List.copyOf(members);
    }

    boolean persistent() {
      return expiresAt == 0;
    }

    Group withMembers(List<Member> next) {
      return new Group(species, expiresAt, stronger, protectedGroup, destination, next);
    }

    @Nullable
    Member member(UUID entityId) {
      for (var member : members) {
        if (member.entityId().equals(entityId)) return member;
      }
      return null;
    }
  }

  record Owner(long cooldownUntil, @Nullable Group group) {}

  static BeastmasterState get(MinecraftServer server) {
    return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
  }

  Owner owner(UUID id) {
    return owners.getOrDefault(id, EMPTY);
  }

  void put(UUID id, long cooldownUntil, @Nullable Group group) {
    var state = new Owner(cooldownUntil, group);
    if (state.equals(owner(id))) return;
    if (state.equals(EMPTY)) owners.remove(id);
    else owners.put(id, state);
    setDirty();
  }

  private static BeastmasterState load(CompoundTag tag, HolderLookup.Provider registries) {
    var state = new BeastmasterState();
    for (Tag entry : tag.getList("owners", Tag.TAG_COMPOUND)) {
      var owner = (CompoundTag) entry;
      if (!owner.hasUUID("owner")) continue;
      long cooldown = Math.max(0, owner.getLong("cooldown"));
      Group group = readGroup(owner.getCompound("group"));
      if (cooldown > 0 || group != null)
        state.owners.put(owner.getUUID("owner"), new Owner(cooldown, group));
    }
    return state;
  }

  @Nullable
  private static Group readGroup(CompoundTag tag) {
    var species = Species.byNode(tag.getString("species"));
    if (species == null) return null;
    var members = new ArrayList<Member>(species.limit);
    var identities = new HashSet<UUID>();
    var entities = new HashSet<UUID>();
    for (Tag entry : tag.getList("members", Tag.TAG_COMPOUND)) {
      if (members.size() == species.limit) break;
      var member = (CompoundTag) entry;
      var dimension = ResourceLocation.tryParse(member.getString("dimension"));
      float health = member.getFloat("health");
      if (dimension != null
          && Float.isFinite(health)
          && health > 0
          && member.hasUUID("identity")
          && member.hasUUID("entity")) {
        UUID identity = member.getUUID("identity");
        UUID entity = member.getUUID("entity");
        if (identities.add(identity) && entities.add(entity))
          members.add(new Member(identity, entity, dimension, Math.min(health, species.health)));
      }
    }
    if (members.isEmpty()) return null;
    return new Group(
        species,
        Math.max(0, tag.getLong("expires")),
        tag.getBoolean("stronger"),
        tag.getBoolean("protected"),
        tag.contains("destination", Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("destination"))
            : null,
        members);
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    var list = new ListTag();
    for (var entry : owners.entrySet()) {
      var owner = new CompoundTag();
      owner.putUUID("owner", entry.getKey());
      owner.putLong("cooldown", entry.getValue().cooldownUntil());
      var group = entry.getValue().group();
      if (group != null) owner.put("group", writeGroup(group));
      list.add(owner);
    }
    tag.put("owners", list);
    return tag;
  }

  private static CompoundTag writeGroup(Group group) {
    var tag = new CompoundTag();
    tag.putString("species", group.species().node);
    tag.putLong("expires", group.expiresAt());
    tag.putBoolean("stronger", group.stronger());
    tag.putBoolean("protected", group.protectedGroup());
    if (group.destination() != null) tag.putString("destination", group.destination().toString());
    var members = new ListTag();
    for (var entry : group.members()) {
      var member = new CompoundTag();
      member.putUUID("identity", entry.identity());
      member.putUUID("entity", entry.entityId());
      member.putString("dimension", entry.dimension().toString());
      member.putFloat("health", entry.health());
      members.add(member);
    }
    tag.put("members", members);
    return tag;
  }
}
