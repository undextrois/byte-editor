package com.byteeditor.core.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Java compiler diagnostics out of {@code maven-compiler-plugin} output.
 *
 * <p>The compiler plugin's format is regular enough for a single pattern:
 * <pre>
 * [ERROR] /abs/path/App.java:[42,17] cannot find symbol
 * </pre>
 * v0.1 deliberately targets just this one, well-understood format rather than
 * building a general multi-tool "problem matcher" abstraction — that
 * generalization is premature until a second build tool's output has
 * actually been seen.
 */
public final class ProblemsParser {

    private static final Pattern COMPILER_ERROR_LINE = Pattern.compile(
            "^(?:\\[(?:ERROR|WARNING)]\\s*)?(.+\\.java):\\[(\\d+),(\\d+)]\\s*(.*)$");

    private ProblemsParser() {
    }

    public static List<Problem> parse(List<OutputLine> lines) {
        List<Problem> problems = new ArrayList<>();
        for (OutputLine line : lines) {
            parseLine(line.text()).ifPresent(problems::add);
        }
        return problems;
    }

    public static Optional<Problem> parseLine(String line) {
        Matcher m = COMPILER_ERROR_LINE.matcher(line.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        Path file = Path.of(m.group(1));
        int lineNo = Integer.parseInt(m.group(2));
        int col = Integer.parseInt(m.group(3));
        String message = m.group(4).strip();
        return Optional.of(new Problem(file, lineNo, col, message));
    }
}
