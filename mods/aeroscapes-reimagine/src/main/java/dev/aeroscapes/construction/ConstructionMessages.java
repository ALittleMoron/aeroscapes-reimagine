package dev.aeroscapes.construction;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Keeps native command arguments in components localized by the receiving player. */
public final class ConstructionMessages {
  private static final List<Rule> RULES =
      List.of(
          rule("Attempting to convert (\\d+) blocks!", "convert_start"),
          rule("Processed (\\d+)/(\\d+) and converted (\\d+)", "convert_progress"),
          rule("Converted (\\d+) blocks", "convert_done"),
          rule("Spawned level", "spawn_level"),
          rule("Spawned animation", "spawn_animation"),
          rule("Attempting to optimize (\\d+) blocks!", "optimize_start"),
          rule("Processed (\\d+)/(\\d+) blocks", "optimize_progress"),
          rule("Converted (\\d+) to vanilla blocks\\.", "vanilla_done"),
          rule("Optimization could save (\\d+) tiles\\.", "optimize_saving"),
          rule("Scanned (\\d+) levels, (\\d+) chunks, (\\d+) blocks, (\\d+) tiles", "scan"),
          rule("grid:(.*)", "grid"));

  private ConstructionMessages() {}

  public static MutableComponent literal(String source) {
    for (Rule rule : RULES) {
      var match = rule.pattern.matcher(source);
      if (!match.matches()) continue;
      Object[] arguments = new Object[match.groupCount()];
      for (int index = 0; index < arguments.length; index++)
        arguments[index] = match.group(index + 1);
      return Component.translatable(rule.key, arguments);
    }
    return Component.literal(source);
  }

  private static Rule rule(String pattern, String name) {
    return new Rule(Pattern.compile(pattern), "aeroscapes.construction.command." + name);
  }

  private record Rule(Pattern pattern, String key) {}
}
