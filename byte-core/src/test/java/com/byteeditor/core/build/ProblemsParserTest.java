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
}
