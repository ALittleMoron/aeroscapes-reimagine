package dev.aeroscapes.backup;

import java.io.IOException;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the exact native failure branches and barriers used by the backup adapter. */
public final class NativeBackupSaveContractTest {
  private static final String PATH = "Ljava/nio/file/Path;";
  private static final String TAG = "Lnet/minecraft/nbt/CompoundTag;";
  private static final String CHUNK = "Lnet/minecraft/world/level/ChunkPos;";
  private static final String LOGGER = "org/slf4j/Logger";
  private static final String OBJECT_ERROR =
      "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V";
  private static final String FUTURE = "Ljava/util/concurrent/CompletableFuture;";

  private NativeBackupSaveContractTest() {}

  public static void main(String[] arguments) throws IOException {
    verify();
  }

  public static void verify() throws IOException {
    nativeSaveBranches();
    regionStorage();
    sableStorage();
    System.out.println("Pinned native backup save failure and barrier contracts passed");
  }

  private static void nativeSaveBranches() throws IOException {
    ClassNode level =
        readClass("net/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess");
    MethodNode saveLevel = method(level, "saveLevelData", "(" + TAG + ")V");
    failureBranch(saveLevel, "java/lang/Exception", "error", OBJECT_ERROR);
    call(
        saveLevel,
        "net/minecraft/world/level/storage/LevelStorageSource$LevelDirectory",
        "path",
        "()" + PATH);

    ClassNode player = readClass("net/minecraft/world/level/storage/PlayerDataStorage");
    field(player, "playerDir", "Ljava/io/File;");
    MethodNode savePlayer = method(player, "save", "(Lnet/minecraft/world/entity/player/Player;)V");
    failureBranch(
        savePlayer, "java/lang/Exception", "warn", "(Ljava/lang/String;Ljava/lang/Object;)V");
    require(
        savePlayer.localVariables.stream()
            .anyMatch(local -> local.desc.equals("Ljava/lang/Exception;")),
        "Player save catch must retain the exception local captured by the adapter");

    ClassNode savedData = readClass("net/minecraft/world/level/saveddata/SavedData");
    MethodNode dataWrite = method(savedData, "lambda$save$0", "(" + TAG + "Ljava/io/File;)V");
    call(
        dataWrite,
        "net/neoforged/neoforge/common/IOUtilities",
        "writeNbtCompressed",
        "(" + TAG + PATH + ")V");
    failureBranch(dataWrite, "java/io/IOException", "error", OBJECT_ERROR);
    ClassNode io = readClass("net/neoforged/neoforge/common/IOUtilities");
    method(io, "writeNbtCompressed", "(" + TAG + PATH + ")V");
    method(io, "waitUntilIOWorkerComplete", "()V");

    ClassNode util = readClass("net/minecraft/Util");
    method(util, "safeReplaceOrMoveFile", "(" + PATH + PATH + PATH + "Z)Z");
    call(
        method(util, "safeReplaceFile", "(" + PATH + PATH + PATH + ")V"),
        "net/minecraft/Util",
        "safeReplaceOrMoveFile",
        "(" + PATH + PATH + PATH + "Z)Z");

    ClassNode stats = readClass("net/minecraft/stats/ServerStatsCounter");
    field(stats, "file", "Ljava/io/File;");
    failureBranch(
        method(stats, "save", "()V"),
        "java/io/IOException",
        "error",
        "(Ljava/lang/String;Ljava/lang/Throwable;)V");
    ClassNode advancement = readClass("net/minecraft/server/PlayerAdvancements");
    field(advancement, "playerSavePath", PATH);
    failureBranch(method(advancement, "save", "()V"), "java/io/IOException", "error", OBJECT_ERROR);

    ClassNode server = readClass("net/minecraft/server/MinecraftServer");
    method(
        server,
        "reportChunkSaveFailure",
        "(Ljava/lang/Throwable;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;"
            + CHUNK
            + ")V");
    ClassNode entities = readClass("net/minecraft/world/level/chunk/storage/EntityStorage");
    MethodNode entitySave =
        method(
            entities,
            "lambda$storeEntities$1",
            "(Lnet/minecraft/nbt/ListTag;Lnet/minecraft/world/entity/Entity;)V");
    require(
        (entitySave.access & Opcodes.ACC_STATIC) != 0,
        "Entity serialization callback remains static");
    failureBranch(entitySave, "java/lang/Exception", "error", OBJECT_ERROR);
  }

  private static void regionStorage() throws IOException {
    ClassNode region = readClass("net/minecraft/world/level/chunk/storage/RegionFileStorage");
    field(region, "folder", PATH);
    method(region, "write", "(" + CHUNK + TAG + ")V");
    method(region, "flush", "()V");
    ClassNode sections = readClass("net/minecraft/world/level/chunk/storage/SectionStorage");
    field(
        sections,
        "simpleRegionStorage",
        "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;");
    method(sections, "tick", "(Ljava/util/function/BooleanSupplier;)V");
    ClassNode poi = readClass("net/minecraft/world/entity/ai/village/poi/PoiManager");
    method(poi, "tick", "(Ljava/util/function/BooleanSupplier;)V");
    ClassNode storage = readClass("net/minecraft/world/level/chunk/storage/SimpleRegionStorage");
    method(storage, "synchronize", "(Z)" + FUTURE);
  }

  private static void sableStorage() throws IOException {
    String prefix = "dev/ryanhcode/sable/sublevel/storage/";
    String data = "L" + prefix + "serialization/SubLevelData;";
    String pointer = "L" + prefix + "holding/GlobalSavedSubLevelPointer;";
    ClassNode sable = readClass(prefix + "serialization/SubLevelStorage");
    field(sable, "folder", PATH);
    failureBranch(
        method(
            sable,
            "attemptSaveHoldingChunk",
            "(" + CHUNK + "L" + prefix + "holding/SubLevelHoldingChunk;)V"),
        "java/io/IOException",
        "error",
        OBJECT_ERROR);
    failureBranch(
        method(sable, "attemptRemoveHoldingChunk", "(" + CHUNK + ")V"),
        "java/io/IOException",
        "error",
        OBJECT_ERROR);
    failureBranch(
        method(sable, "attemptSaveSubLevel", "(" + CHUNK + data + ")" + pointer),
        "java/io/IOException",
        "error",
        OBJECT_ERROR);
    failureBranch(
        method(sable, "attemptSaveSubLevel", "(" + pointer + data + ")V"),
        "java/io/IOException",
        "error",
        OBJECT_ERROR);
    method(sable, "flush", "()V");
    method(sable, "pruneCache", "()V");
    ClassNode region = readClass(prefix + "region/SubLevelStorageFile");
    field(region, "path", PATH);
    method(region, "write", "(I" + TAG + ")V");
    method(region, "flush", "()V");
    ClassNode holding = readClass(prefix + "region/SubLevelRegionFile");
    failureBranch(
        method(holding, "trySave", "(IIL" + prefix + "holding/SubLevelHoldingChunk;)V"),
        "java/io/IOException",
        "error",
        "(Ljava/lang/String;[Ljava/lang/Object;)V");
    failureBranch(
        method(holding, "tryRemove", "(II)V"),
        "java/io/IOException",
        "error",
        "(Ljava/lang/String;[Ljava/lang/Object;)V");
  }

  private static ClassNode readClass(String name) throws IOException {
    try (InputStream input =
        NativeBackupSaveContractTest.class.getResourceAsStream("/" + name + ".class")) {
      if (input == null) throw new IOException("Missing pinned class: " + name);
      ClassNode node = new ClassNode();
      new ClassReader(input).accept(node, 0);
      return node;
    }
  }

  private static MethodNode method(ClassNode owner, String name, String descriptor) {
    return owner.methods.stream()
        .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "Missing pinned method: " + owner.name + "." + name + descriptor));
  }

  private static void field(ClassNode owner, String name, String descriptor) {
    require(
        owner.fields.stream()
            .anyMatch(
                field ->
                    field.name.equals(name)
                        && field.desc.equals(descriptor)
                        && (field.access & Opcodes.ACC_FINAL) != 0),
        "Missing pinned final storage field: " + owner.name + "." + name);
  }

  private static void failureBranch(
      MethodNode method, String caught, String logMethod, String logDescriptor) {
    require(
        method.tryCatchBlocks.stream().anyMatch(block -> caught.equals(block.type)),
        "Missing pinned save catch: " + method.name + method.desc);
    call(method, LOGGER, logMethod, logDescriptor);
  }

  private static void call(MethodNode method, String owner, String name, String descriptor) {
    int count = 0;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode invocation
          && invocation.owner.equals(owner)
          && invocation.name.equals(name)
          && invocation.desc.equals(descriptor)) count++;
    }
    require(
        count == 1,
        "Expected one pinned call to "
            + owner
            + "."
            + name
            + " in "
            + method.name
            + method.desc
            + ", got "
            + count);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
