package dev.aeroscapes.movement;

public interface HookOptionsAccess {
  int aeroscapes$hookControlsVersion();

  void aeroscapes$hookControlsVersion(int version);

  boolean aeroscapes$hookMappingsLoaded();
}
