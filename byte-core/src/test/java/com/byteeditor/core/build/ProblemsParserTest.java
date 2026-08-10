package com.byteeditor.core.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemsParserTest {

    @Test
    void parsesStandardCompilerErrorLine() {
        String line = "[ERROR] /home/dev/App.java:[42,17] cannot find symbol";
        Optional<Problem> problem = ProblemsParser.parseLine(line);
        assertTrue(problem.isPresent());
        assertEquals(Path.of("/home/dev/App.java"), problem.get().file());
        assertEquals(42, problem.get().line());
        assertEquals(17, problem.get().column());
        assertEquals("cannot find symbol", problem.get().message());
    }

    @Test
    void parsesLineWithoutErrorPrefix() {
        String line = "/home/dev/App.java:[14,23] ';' expected";
        Optional<Problem> problem = ProblemsParser.parseLine(line);
        assertTrue(problem.isPresent());
        assertEquals(14, problem.get().line());
    }

    @Test
    void ignoresUnrelatedOutputLines() {
        assertTrue(ProblemsParser.parseLine("[INFO] BUILD SUCCESS").isEmpty());
        assertTrue(ProblemsParser.parseLine("[INFO] Compiling 4 source files").isEmpty());
    }

    @Test
    void parsesMultipleProblemsFromOutputStream() {
        List<OutputLine> lines = List.of(
                new OutputLine("[INFO] Compiling 2 source files", OutputLine.Stream.STDOUT),
                new OutputLine("[ERROR] App.java:[42,17] cannot find symbol", OutputLine.Stream.STDERR),
                new OutputLine("[ERROR] UserService.java:[81,9] incompatible types", OutputLine.Stream.STDERR),
                new OutputLine("[INFO] BUILD FAILURE", OutputLine.Stream.STDOUT)
        );
        List<Problem> problems = ProblemsParser.parse(lines);
        assertEquals(2, problems.size());
        assertEquals("App.java", problems.get(0).file().getFileName().toString());
        assertEquals("UserService.java", problems.get(1).file().getFileName().toString());
    }

    @Test
    void extractsFileLineAndColumnRegardlessOfMessageLocale() {
        // The parser anchors only on the file.java:[line,col] bracket shape
        // javac emits regardless of JVM locale — everything after it is
        // treated as an opaque message. This proves that holds even when the
        // message itself isn't in English, independent of whatever locale
        // flag MavenBuildProvider forces on the mvn invocation itself.
        String german = "[ERROR] /home/dev/App.java:[42,17] Kann Symbol nicht finden";
        Optional<Problem> problem = ProblemsParser.parseLine(german);
        assertTrue(problem.isPresent());
        assertEquals(Path.of("/home/dev/App.java"), problem.get().file());
        assertEquals(42, problem.get().line());
        assertEquals(17, problem.get().column());
        assertEquals("Kann Symbol nicht finden", problem.get().message());

        String japanese = "/home/dev/App.java:[14,23] \u30b7\u30f3\u30dc\u30eb\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093";
        Optional<Problem> problem2 = ProblemsParser.parseLine(japanese);
        assertTrue(problem2.isPresent());
        assertEquals(14, problem2.get().line());
        assertEquals(23, problem2.get().column());
    }
}
