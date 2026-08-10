package com.byteeditor.core;

import com.byteeditor.core.build.BuildProcess;
import com.byteeditor.core.build.OutputLine;
import com.byteeditor.core.build.Problem;
import com.byteeditor.core.build.ProblemsParser;
import com.byteeditor.core.project.BuildSystem;
import com.byteeditor.core.project.MainClassDetector;
import com.byteeditor.core.project.Project;
import com.byteeditor.core.project.ProjectDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dependency-free harness that mirrors {@link TextBufferTest}, used only to
 * verify logic in environments without Maven Central access to JUnit. Not
 * part of the shipped build; delete once `mvn test` is runnable against your
 * Nexus mirror.
 */
public final class ManualHarness {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        newBufferHasSingleEmptyLine();
        insertSingleCharacter();
        insertBuildsUpAWord();
        insertNewlineSplitsLine();
        backspaceAtStartOfLineMergesWithPrevious();
        backspaceOnEmptyBufferAtOriginDoesNothing();
        deleteForwardAtEndOfLineMergesWithNext();
        deleteForwardAtVeryEndDoesNothing();
        undoReversesInsert();
        redoReappliesUndoneEdit();
        newEditAfterUndoClearsRedoStack();
        undoAcrossLineSplitAndMergeRoundTrips();
        multipleUndoRedoCyclesPreserveHistory();
        insertTextPastesMultilineBlockAsOneUndoStep();
        insertTextHandlesHugePasteEfficientlyAndCorrectly();
        insertTextNormalizesCrlfAndLoneCr();
        cursorMovementNeverSplitsSurrogatePair();
        insertCharAcceptsFullCodePointForAstralCharacters();
        backspaceDoesNotSplitSurrogatePair();
        deleteForwardDoesNotSplitSurrogatePair();
        getTextRoundTripsThroughMultipleLines();
        emptyFileLoadsAsSingleEmptyLine();
        cursorClampsToValidRangeOnSetCursor();
        moveUpAndDownClampColumnToShorterLines();
        saveClearsDirtyFlag();

        parsesStandardCompilerErrorLine();
        parsesLineWithoutErrorPrefix();
        ignoresUnrelatedOutputLines();
        parsesMultipleProblemsFromOutputStream();

        projectDetectorRecognizesMavenLayout();
        projectDetectorFallsBackToNoneWithoutPom();

        buildProcessStreamsStdoutAndStderrSeparately();
        buildProcessReportsNonZeroExitCode();
        buildProcessKillTerminatesLongRunningProcess();
        buildProcessHistorySnapshotContainsAllOutput();

        detectsSimpleClassWithPackage();
        detectsClassWithoutPackage();
        returnsEmptyWhenNoMainMethod();
        returnsEmptyWhenNoPublicClass();
        handlesFinalPublicClass();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        check(name + " (expected=" + expected + ", actual=" + actual + ")",
                java.util.Objects.equals(expected, actual));
    }

    static void newBufferHasSingleEmptyLine() {
        TextBuffer buf = new TextBuffer();
        eq("newBuffer.lineCount", 1, buf.getLineCount());
        eq("newBuffer.line0", "", buf.getLine(0));
        check("newBuffer.notDirty", !buf.isDirty());
    }

    static void insertSingleCharacter() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        eq("insertChar.line", "A", buf.getLine(0));
        eq("insertChar.row", 0, buf.getCursorRow());
        eq("insertChar.col", 1, buf.getCursorCol());
        check("insertChar.dirty", buf.isDirty());
    }

    static void insertBuildsUpAWord() {
        TextBuffer buf = new TextBuffer();
        for (char c : "Byte".toCharArray()) buf.insertChar(c);
        eq("insertWord.line", "Byte", buf.getLine(0));
        eq("insertWord.col", 4, buf.getCursorCol());
    }

    static void insertNewlineSplitsLine() {
        TextBuffer buf = TextBuffer.fromString("HelloWorld");
        buf.setCursor(0, 5);
        buf.insertNewline();
        eq("newline.lineCount", 2, buf.getLineCount());
        eq("newline.line0", "Hello", buf.getLine(0));
        eq("newline.line1", "World", buf.getLine(1));
        eq("newline.row", 1, buf.getCursorRow());
        eq("newline.col", 0, buf.getCursorCol());
    }

    static void backspaceAtStartOfLineMergesWithPrevious() {
        TextBuffer buf = TextBuffer.fromString("Hello\nWorld");
        buf.setCursor(1, 0);
        buf.backspace();
        eq("bsMerge.lineCount", 1, buf.getLineCount());
        eq("bsMerge.line0", "HelloWorld", buf.getLine(0));
        eq("bsMerge.row", 0, buf.getCursorRow());
        eq("bsMerge.col", 5, buf.getCursorCol());
    }

    static void backspaceOnEmptyBufferAtOriginDoesNothing() {
        TextBuffer buf = new TextBuffer();
        buf.backspace();
        eq("bsOrigin.lineCount", 1, buf.getLineCount());
        eq("bsOrigin.line0", "", buf.getLine(0));
    }

    static void deleteForwardAtEndOfLineMergesWithNext() {
        TextBuffer buf = TextBuffer.fromString("Hello\nWorld");
        buf.setCursor(0, 5);
        buf.deleteForward();
        eq("delMerge.lineCount", 1, buf.getLineCount());
        eq("delMerge.line0", "HelloWorld", buf.getLine(0));
    }

    static void deleteForwardAtVeryEndDoesNothing() {
        TextBuffer buf = TextBuffer.fromString("End");
        buf.setCursor(0, 3);
        buf.deleteForward();
        eq("delEnd.line0", "End", buf.getLine(0));
    }

    static void undoReversesInsert() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('X');
        buf.undo();
        eq("undoInsert.line0", "", buf.getLine(0));
        eq("undoInsert.col", 0, buf.getCursorCol());
        check("undoInsert.canUndoFalse", !buf.canUndo());
    }

    static void redoReappliesUndoneEdit() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('X');
        buf.undo();
        buf.redo();
        eq("redo.line0", "X", buf.getLine(0));
        eq("redo.col", 1, buf.getCursorCol());
    }

    static void newEditAfterUndoClearsRedoStack() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        buf.undo();
        buf.insertChar('B');
        check("redoCleared", !buf.canRedo());
        eq("redoCleared.line0", "B", buf.getLine(0));
    }

    static void undoAcrossLineSplitAndMergeRoundTrips() {
        TextBuffer buf = TextBuffer.fromString("HelloWorld");
        buf.setCursor(0, 5);
        buf.insertNewline();
        buf.undo();
        eq("undoSplit.lineCount", 1, buf.getLineCount());
        eq("undoSplit.line0", "HelloWorld", buf.getLine(0));
        eq("undoSplit.row", 0, buf.getCursorRow());
        eq("undoSplit.col", 5, buf.getCursorCol());
    }

    static void multipleUndoRedoCyclesPreserveHistory() {
        TextBuffer buf = new TextBuffer();
        buf.insertChar('A');
        buf.insertChar('B');
        buf.insertChar('C');
        buf.undo();
        buf.undo();
        eq("cycles.afterTwoUndo", "A", buf.getLine(0));
        buf.redo();
        eq("cycles.afterRedo", "AB", buf.getLine(0));
        buf.insertChar('Z');
        eq("cycles.afterNewEdit", "ABZ", buf.getLine(0));
        check("cycles.redoClearedAfterNewEdit", !buf.canRedo());
    }

    static void insertTextPastesMultilineBlockAsOneUndoStep() {
        TextBuffer buf = TextBuffer.fromString("headtail");
        buf.setCursor(0, 4);
        buf.insertText("A\nB\nC");
        eq("paste.lineCount", 3, buf.getLineCount());
        eq("paste.line0", "headA", buf.getLine(0));
        eq("paste.line1", "B", buf.getLine(1));
        eq("paste.line2", "Ctail", buf.getLine(2));
        eq("paste.row", 2, buf.getCursorRow());
        eq("paste.col", 1, buf.getCursorCol());
        buf.undo();
        eq("paste.undoLineCount", 1, buf.getLineCount());
        eq("paste.undoLine0", "headtail", buf.getLine(0));
    }

    static void insertTextHandlesHugePasteEfficientlyAndCorrectly() {
        TextBuffer buf = new TextBuffer();
        StringBuilder sb = new StringBuilder();
        int n = 5000;
        for (int i = 0; i < n; i++) {
            sb.append("line-").append(i);
            if (i < n - 1) sb.append('\n');
        }
        long start = System.nanoTime();
        buf.insertText(sb.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        eq("hugePaste.lineCount", n, buf.getLineCount());
        eq("hugePaste.line0", "line-0", buf.getLine(0));
        eq("hugePaste.lastLine", "line-4999", buf.getLine(n - 1));
        System.out.println("  [INFO] huge paste of " + n + " lines took " + ms + "ms");
        buf.undo();
        eq("hugePaste.undoLineCount", 1, buf.getLineCount());
    }

    static void insertTextNormalizesCrlfAndLoneCr() {
        TextBuffer buf = new TextBuffer();
        buf.insertText("a\r\nb\rc");
        eq("crlf.lineCount", 3, buf.getLineCount());
        eq("crlf.line0", "a", buf.getLine(0));
        eq("crlf.line1", "b", buf.getLine(1));
        eq("crlf.line2", "c", buf.getLine(2));
    }

    static void cursorMovementNeverSplitsSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString("a" + emoji + "b");
        buf.setCursor(0, 0);
        buf.moveRight();
        eq("surrogateMove.afterA", 1, buf.getCursorCol());
        buf.moveRight();
        eq("surrogateMove.afterEmoji", 1 + emoji.length(), buf.getCursorCol());
        buf.moveLeft();
        eq("surrogateMove.backOverEmoji", 1, buf.getCursorCol());
    }

    static void insertCharAcceptsFullCodePointForAstralCharacters() {
        TextBuffer buf = new TextBuffer();
        int cp = 0x1F600;
        buf.insertChar(cp);
        String expected = new String(Character.toChars(cp));
        eq("astralInsert.line0", expected, buf.getLine(0));
        eq("astralInsert.col", expected.length(), buf.getCursorCol());
    }

    static void backspaceDoesNotSplitSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString(emoji);
        buf.setCursor(0, emoji.length());
        buf.backspace();
        eq("bsSurrogate.line0", "", buf.getLine(0));
    }

    static void deleteForwardDoesNotSplitSurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        TextBuffer buf = TextBuffer.fromString(emoji + "x");
        buf.setCursor(0, 0);
        buf.deleteForward();
        eq("delSurrogate.line0", "x", buf.getLine(0));
    }

    static void getTextRoundTripsThroughMultipleLines() {
        String original = "line one\nline two\nline three";
        TextBuffer buf = TextBuffer.fromString(original);
        eq("roundTrip", original, buf.getText());
    }

    static void emptyFileLoadsAsSingleEmptyLine() {
        TextBuffer buf = TextBuffer.fromString("");
        eq("emptyFile.lineCount", 1, buf.getLineCount());
        eq("emptyFile.line0", "", buf.getLine(0));
    }

    static void cursorClampsToValidRangeOnSetCursor() {
        TextBuffer buf = TextBuffer.fromString("short\nlonger line here");
        buf.setCursor(0, 999);
        eq("clamp.col", 5, buf.getCursorCol());
        buf.setCursor(999, 0);
        eq("clamp.row", 1, buf.getCursorRow());
        buf.setCursor(-5, -5);
        eq("clamp.negRow", 0, buf.getCursorRow());
        eq("clamp.negCol", 0, buf.getCursorCol());
    }

    static void moveUpAndDownClampColumnToShorterLines() {
        TextBuffer buf = TextBuffer.fromString("short\na very long line of text");
        buf.setCursor(1, 20);
        buf.moveUp();
        eq("clampVertical.row", 0, buf.getCursorRow());
        eq("clampVertical.col", 5, buf.getCursorCol());
    }

    static void saveClearsDirtyFlag() throws Exception {
        Path tmp = Files.createTempFile("byte-test", ".txt");
        try {
            TextBuffer buf = new TextBuffer();
            buf.insertChar('X');
            check("save.dirtyBeforeSave", buf.isDirty());
            buf.saveToFile(tmp);
            check("save.notDirtyAfterSave", !buf.isDirty());
            eq("save.fileContent", "X", Files.readString(tmp));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static void parsesStandardCompilerErrorLine() {
        Optional<Problem> p = ProblemsParser.parseLine("[ERROR] /home/dev/App.java:[42,17] cannot find symbol");
        check("problems.present", p.isPresent());
        eq("problems.line", 42, p.get().line());
        eq("problems.col", 17, p.get().column());
        eq("problems.message", "cannot find symbol", p.get().message());
    }

    static void parsesLineWithoutErrorPrefix() {
        Optional<Problem> p = ProblemsParser.parseLine("/home/dev/App.java:[14,23] ';' expected");
        check("problems.noPrefix.present", p.isPresent());
        eq("problems.noPrefix.line", 14, p.get().line());
    }

    static void ignoresUnrelatedOutputLines() {
        check("problems.ignoresInfo1", ProblemsParser.parseLine("[INFO] BUILD SUCCESS").isEmpty());
        check("problems.ignoresInfo2", ProblemsParser.parseLine("[INFO] Compiling 4 source files").isEmpty());
    }

    static void parsesMultipleProblemsFromOutputStream() {
        List<OutputLine> lines = List.of(
                new OutputLine("[INFO] Compiling 2 source files", OutputLine.Stream.STDOUT),
                new OutputLine("[ERROR] App.java:[42,17] cannot find symbol", OutputLine.Stream.STDERR),
                new OutputLine("[ERROR] UserService.java:[81,9] incompatible types", OutputLine.Stream.STDERR),
                new OutputLine("[INFO] BUILD FAILURE", OutputLine.Stream.STDOUT)
        );
        List<Problem> problems = ProblemsParser.parse(lines);
        eq("problems.multiCount", 2, problems.size());
        eq("problems.multiFile0", "App.java", problems.get(0).file().getFileName().toString());
        eq("problems.multiFile1", "UserService.java", problems.get(1).file().getFileName().toString());
    }

    static void projectDetectorRecognizesMavenLayout() throws Exception {
        Path tmpDir = Files.createTempDirectory("byte-proj-");
        try {
            Files.writeString(tmpDir.resolve("pom.xml"), "<project></project>");
            Files.createDirectories(tmpDir.resolve("src/main/java"));
            Project project = ProjectDetector.detect(tmpDir);
            eq("project.buildSystem", BuildSystem.MAVEN, project.getBuildSystem());
            check("project.hasSourceRoot", !project.getSourceRoots().isEmpty());
            check("project.hasBuildSystemTrue", project.hasBuildSystem());
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    static void projectDetectorFallsBackToNoneWithoutPom() throws Exception {
        Path tmpDir = Files.createTempDirectory("byte-proj-none-");
        try {
            Project project = ProjectDetector.detect(tmpDir);
            eq("project.none.buildSystem", BuildSystem.NONE, project.getBuildSystem());
            check("project.none.hasBuildSystemFalse", !project.hasBuildSystem());
        } finally {
            deleteRecursive(tmpDir);
        }
    }

    static void buildProcessStreamsStdoutAndStderrSeparately() throws Exception {
        BuildProcess process = BuildProcess.start(
                List.of("bash", "-c", "echo hello-out; echo hello-err 1>&2; exit 0"),
                Path.of("."));
        List<OutputLine> received = new CopyOnWriteArrayList<>();
        process.onOutput(received::add);
        int exitCode = process.waitForExit();
        eq("buildProcess.exitCode", 0, exitCode);
        check("buildProcess.stdoutSeen", received.stream()
                .anyMatch(l -> l.text().equals("hello-out") && l.stream() == OutputLine.Stream.STDOUT));
        check("buildProcess.stderrSeen", received.stream()
                .anyMatch(l -> l.text().equals("hello-err") && l.stream() == OutputLine.Stream.STDERR));
    }

    static void buildProcessReportsNonZeroExitCode() throws Exception {
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "exit 3"), Path.of("."));
        eq("buildProcess.nonZeroExit", 3, process.waitForExit());
    }

    static void buildProcessKillTerminatesLongRunningProcess() throws Exception {
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "sleep 30"), Path.of("."));
        check("buildProcess.aliveBeforeKill", process.isAlive());
        process.kill();
        Thread.sleep(300);
        check("buildProcess.deadAfterKill", !process.isAlive());
    }

    static void buildProcessHistorySnapshotContainsAllOutput() throws Exception {
        BuildProcess process = BuildProcess.start(
                List.of("bash", "-c", "echo one; echo two; echo three"),
                Path.of("."));
        process.waitForExit();
        Thread.sleep(150);
        eq("buildProcess.historySize", 3, process.getHistorySnapshot().size());
    }

    private static void deleteRecursive(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    static void detectsSimpleClassWithPackage() {
        String source = "package com.example;\n\npublic class App {\n"
                + "    public static void main(String[] args) {}\n}\n";
        eq("mainClass.withPackage", Optional.of("com.example.App"), MainClassDetector.detect(source));
    }

    static void detectsClassWithoutPackage() {
        String source = "public class HelloWorld {\n"
                + "    public static void main(String[] args) {}\n}\n";
        eq("mainClass.noPackage", Optional.of("HelloWorld"), MainClassDetector.detect(source));
    }

    static void returnsEmptyWhenNoMainMethod() {
        String source = "package com.example;\npublic class NotRunnable {\n    void helper() {}\n}\n";
        check("mainClass.noMain", MainClassDetector.detect(source).isEmpty());
    }

    static void returnsEmptyWhenNoPublicClass() {
        String source = "class Internal {\n    public static void main(String[] args) {}\n}\n";
        check("mainClass.noPublicClass", MainClassDetector.detect(source).isEmpty());
    }

    static void handlesFinalPublicClass() {
        String source = "package a.b;\npublic final class Runner {\n"
                + "    public static void main(String[] args) {}\n}\n";
        eq("mainClass.finalClass", Optional.of("a.b.Runner"), MainClassDetector.detect(source));
    }
}
