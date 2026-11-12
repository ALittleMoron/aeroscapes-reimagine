package dev.aeroscapes.engineering;

import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Translates verified exp4j errors while retaining user expressions and positions. */
public final class GaugeExpressionTranslations {
  private static final List<ErrorPattern> ERRORS =
      List.of(
          new ErrorPattern(
              Pattern.compile("Expression can not be empty"), "extra_gauges.expression.error.0"),
          new ErrorPattern(
              Pattern.compile("The expression can not be empty"),
              "extra_gauges.expression.error.1"),
          new ErrorPattern(
              Pattern.compile("Misplaced function separator \\',\\' or mismatched parentheses"),
              "extra_gauges.expression.error.2"),
          new ErrorPattern(
              Pattern.compile("Mismatched parentheses detected\\. Please check the expression"),
              "extra_gauges.expression.error.3"),
          new ErrorPattern(
              Pattern.compile("Unknown Token type encountered\\. This should not happen"),
              "extra_gauges.expression.error.4"),
          new ErrorPattern(
              Pattern.compile("Operator is unknown for token\\."),
              "extra_gauges.expression.error.5"),
          new ErrorPattern(
              Pattern.compile("Division by zero in cotangent!"), "extra_gauges.expression.error.6"),
          new ErrorPattern(Pattern.compile("Division by zero!"), "extra_gauges.expression.error.7"),
          new ErrorPattern(
              Pattern.compile("Unable to parse char '(.*)' \\(Code:(\\d+)\\) at \\[(\\d+)\\]"),
              "extra_gauges.expression.error.8"),
          new ErrorPattern(
              Pattern.compile(
                  "Unknown function or variable '(.*)' at pos (\\d+) in expression '(.*)'"),
              "extra_gauges.expression.error.9"),
          new ErrorPattern(
              Pattern.compile("A variable can not have the same name as a function \\[(.*)\\]"),
              "extra_gauges.expression.error.10"),
          new ErrorPattern(
              Pattern.compile("No value has been set for the setVariable '(.*)'\\."),
              "extra_gauges.expression.error.11"),
          new ErrorPattern(
              Pattern.compile("Invalid number of operands available for '(.*)' operator"),
              "extra_gauges.expression.error.12"),
          new ErrorPattern(
              Pattern.compile("Invalid number of arguments available for '(.*)' function"),
              "extra_gauges.expression.error.13"),
          new ErrorPattern(
              Pattern.compile(
                  "Invalid number of items on the output queue\\. Might be caused by an invalid number of arguments for a function\\."),
              "extra_gauges.expression.error.14"));

  private GaugeExpressionTranslations() {}

  public static String translate(String source, BiFunction<String, Object[], String> translations) {
    if (source == null) return null;
    for (var error : ERRORS) {
      var match = error.pattern().matcher(source);
      if (!match.matches()) continue;
      Object[] arguments = new Object[match.groupCount()];
      for (int index = 0; index < arguments.length; index++)
        arguments[index] = match.group(index + 1);
      return translations.apply(error.key(), arguments);
    }
    return source;
  }

  private record ErrorPattern(Pattern pattern, String key) {}
}
