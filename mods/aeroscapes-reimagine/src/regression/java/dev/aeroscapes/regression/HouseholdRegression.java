package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

public final class HouseholdRegression {
  private HouseholdRegression() {}

  public static void verify(ServerLevel level) {
    List<Exception> failures = new ArrayList<>();
    for (var scenario :
        List.of(
            new Scenario("Chalk", ChalkRegression::verify),
            new Scenario("AgriCraft", AgricultureRegression::verify),
            new Scenario("Animal Husbandry", HusbandryRegression::verify),
            new Scenario("Whale encounters", WhaleEncounterRegression::verify),
            new Scenario("Preflight", PreflightRegression::verify))) {
      try {
        scenario.check().run(level);
        LogUtils.getLogger().info("Household regression passed: {}", scenario.name());
      } catch (IOException | ReflectiveOperationException | RuntimeException failure) {
        failures.add(new IllegalStateException(scenario.name(), failure));
      }
    }
    if (!failures.isEmpty()) {
      var combined = new IllegalStateException("Household regression failed");
      failures.forEach(combined::addSuppressed);
      throw combined;
    }
  }

  private record Scenario(String name, Check check) {}

  @FunctionalInterface
  private interface Check {
    void run(ServerLevel level) throws IOException, ReflectiveOperationException;
  }
}
