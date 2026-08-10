package com.byteeditor.tui;

import com.byteeditor.core.TextBuffer;
import com.byteeditor.core.build.BuildProcess;
import com.byteeditor.core.build.BuildProvider;
import com.byteeditor.core.build.MavenBuildProvider;
import com.byteeditor.core.build.OutputLine;
import com.byteeditor.core.build.Problem;
import com.byteeditor.core.build.ProblemsParser;
import com.byteeditor.core.project.MainClassDetector;
import com.byteeditor.core.project.Project;
import com.byteeditor.core.project.ProjectDetector;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Byte v0.1: a small terminal Java/Maven workbench. */
public final class Main {

    private static final int FRAME_DELAY_MS = 25;

    private enum Focus { EDITOR, EXPLORER, PROBLEMS }

    private TextBuffer buffer = new TextBuffer();
    private Project project;
    private BuildProvider buildProvider;
    private ExplorerModel explorer;
    private final EditorViewport viewport = new EditorViewport();
    private Path openFile;

    // Background workers never mutate UI state directly. They enqueue work
    // which is applied by the single UI thread in mainLoop().
    private final ConcurrentLinkedQueue<Runnable> uiEvents = new ConcurrentLinkedQueue<>();
    private final List<String> buildOutput = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private volatile BuildProcess runningProcess;
    private Project lastProcessProject;

    private Focus focus = Focus.EDITOR;
    private boolean showProblems;
    private int selectedProblem;
    private int explorerScroll;
    private int buildOutputScroll;
    private boolean buildOutputFollowTail = true;
    private boolean needsRedraw = true;
    private boolean forceCompleteRefresh = true;
    private Screen screen;

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

    private void run(String[] args) throws Exception {
        Path target = (args.length > 0 ? Path.of(args[0]) : Path.of("."))
                .toAbsolutePath().normalize();

        project = ProjectDetector.detectNearest(target);
        buildProvider = new MavenBuildProvider();
        explorer = new ExplorerModel(project.getRoot());

        if (Files.isRegularFile(target)) {
            openFile(target);
        } else {
            openInitialFile();
        }

        Terminal terminal = new DefaultTerminalFactory()
                .setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE)
                .createTerminal();
        disableTerminalFlowControl();
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null);

        try {
            mainLoop();
        } finally {
            if (runningProcess != null && runningProcess.isAlive()) {
                runningProcess.kill();
            }
            screen.stopScreen();
            restoreTerminalFlowControl();
        }
    }

    /** Opens a sensible initial source file when Byte is launched with `byte .`. */
    private void openInitialFile() {
        for (Path sourceRoot : project.getSourceRoots()) {
            try (var files = Files.walk(sourceRoot)) {
                var first = files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .findFirst();
                if (first.isPresent()) {
                    openFile(first.get());
                    return;
                }
            } catch (IOException ignored) {
                // Explorer remains usable even when a source root is unreadable.
            }
        }
    }

    private void disableTerminalFlowControl() {
        runSttyBestEffort("stty -ixon < /dev/tty");
    }

    private void restoreTerminalFlowControl() {
        runSttyBestEffort("stty ixon < /dev/tty");
    }

    private void runSttyBestEffort(String command) {
        try {
            new ProcessBuilder("sh", "-c", command).inheritIO().start().waitFor();
        } catch (Exception ignored) {
            // Non-Unix terminals simply keep their normal behavior.
        }
    }

    /** Single-threaded UI loop: only this thread ever renders or mutates screen state. */
    private void mainLoop() throws IOException {
        boolean running = true;
        while (running) {
            // Lanterna does not update Screen dimensions until this is called.
            // Keeping resize handling in the UI thread prevents stale layout math
            // and avoids rendering boxes/footer at the old terminal size.
            TerminalSize resized = screen.doResizeIfNecessary();
            if (resized != null) {
                // A resize invalidates both layout geometry and the terminal's visible
                // front buffer. Force one complete repaint; DELTA refreshes can look
                // frozen on some terminal emulators immediately after a mouse resize.
                screen.clear();
                forceCompleteRefresh = true;
                needsRedraw = true;
            }

            drainUiEvents();

            KeyStroke key;
            while ((key = screen.pollInput()) != null) {
                running = handleKey(key);
                needsRedraw = true;
                if (!running) {
                    break;
                }
            }

            if (needsRedraw) {
                render();
                needsRedraw = false;
            }

            try {
                Thread.sleep(FRAME_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void drainUiEvents() {
        Runnable event;
        while ((event = uiEvents.poll()) != null) {
            event.run();
            needsRedraw = true;
        }
    }

    private void postToUi(Runnable event) {
        uiEvents.offer(event);
    }

    private boolean handleKey(KeyStroke key) {
        if (key instanceof MouseAction mouse) {
            return handleMouse(mouse);
        }
        if (key.getKeyType() == KeyType.F10) {
            return false;
        }
        if (key.getKeyType() == KeyType.Escape) {
            if (processRunning()) {
                runningProcess.kill();
                buildOutput.add("[INFO] Cancelled.");
            }
            focus = Focus.EDITOR;
            showProblems = false;
            return true;
        }
        if (key.getKeyType() == KeyType.F2) {
            focus = focus == Focus.EXPLORER ? Focus.EDITOR : Focus.EXPLORER;
            return true;
        }
        if (key.getKeyType() == KeyType.F5) {
            triggerBuild(false);
            return true;
        }
        if (key.getKeyType() == KeyType.F6) {
            runProgram();
            return true;
        }
        if (key.getKeyType() == KeyType.F7) {
            triggerBuild(true);
            return true;
        }
        if (key.getKeyType() == KeyType.F8) {
            showProblems = true;
            focus = Focus.PROBLEMS;
            selectedProblem = Math.min(selectedProblem, Math.max(0, problems.size() - 1));
            return true;
        }
        if (isCtrl(key, 's', 19)) {
            saveCurrentFile();
            return true;
        }
        if (isCtrl(key, 'z', 26)) {
            if (focus == Focus.EDITOR) buffer.undo();
            return true;
        }
        if (isCtrl(key, 'y', 25)) {
            if (focus == Focus.EDITOR) buffer.redo();
            return true;
        }

        return switch (focus) {
            case EXPLORER -> handleExplorerKey(key);
            case PROBLEMS -> handleProblemsKey(key);
            case EDITOR -> handleEditorKey(key);
        };
    }

    private boolean handleEditorKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case Character -> {
                if (!key.isCtrlDown() && !key.isAltDown()) {
                    buffer.insertChar(key.getCharacter());
                }
            }
            case Enter -> buffer.insertNewline();
            case Backspace -> buffer.backspace();
            case Delete -> buffer.deleteForward();
            case ArrowLeft -> buffer.moveLeft();
            case ArrowRight -> buffer.moveRight();
            case ArrowUp -> buffer.moveUp();
            case ArrowDown -> buffer.moveDown();
            case Home -> buffer.moveHome();
            case End -> buffer.moveEnd();
            case PageUp -> buffer.pageUp(Math.max(1, editorHeight()));
            case PageDown -> buffer.pageDown(Math.max(1, editorHeight()));
            default -> { }
        }
        return true;
    }

    private boolean handleExplorerKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case ArrowUp -> explorer.moveUp();
            case ArrowDown -> explorer.moveDown();
            case ArrowRight -> explorer.expandSelected();
            case ArrowLeft -> explorer.collapseSelected();
            case Enter -> {
                ExplorerModel.Entry selected = explorer.selected();
                if (selected != null) {
                    if (selected.directory()) {
                        explorer.toggleSelectedDirectory();
                    } else {
                        openFile(selected.path());
                        focus = Focus.EDITOR;
                    }
                }
            }
            default -> { }
        }
        return true;
    }

    private boolean handleProblemsKey(KeyStroke key) {
        if (problems.isEmpty()) {
            return true;
        }
        switch (key.getKeyType()) {
            case ArrowUp -> selectedProblem = Math.max(0, selectedProblem - 1);
            case ArrowDown -> selectedProblem = Math.min(problems.size() - 1, selectedProblem + 1);
            case Enter -> jumpToProblem(problems.get(selectedProblem));
            default -> { }
        }
        return true;
    }

    private static boolean isCtrl(KeyStroke key, char letter, int rawControlCode) {
        Character c = key.getCharacter();
        if (c == null) return false;
        return (key.isCtrlDown() && (c == letter || c == Character.toUpperCase(letter))) || c == rawControlCode;
    }

    private void openFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            buffer = TextBuffer.fromFile(path.toAbsolutePath().normalize());
            openFile = path.toAbsolutePath().normalize();
            viewport.reset();
            buildOutput.add("[INFO] Opened " + project.getRoot().relativize(openFile));
        } catch (IOException e) {
            buildOutput.add("[ERROR] Open failed: " + e.getMessage());
        }
    }

    private void saveCurrentFile() {
        if (openFile == null) {
            buildOutput.add("[INFO] Select a file from the explorer first.");
            return;
        }
        try {
            buffer.saveToFile(openFile);
            buildOutput.add("[INFO] Saved " + project.getRoot().relativize(openFile));
        } catch (IOException e) {
            buildOutput.add("[ERROR] Save failed: " + e.getMessage());
        }
    }

    private void triggerBuild(boolean tests) {
        Project buildProject = activeBuildProject();
        if (!buildProvider.supports(buildProject)) {
            buildOutput.add("[INFO] No supported build system detected for the current file.");
            return;
        }
        if (processRunning()) {
            buildOutput.add("[INFO] A process is already running.");
            return;
        }

        buildOutput.clear();
        problems.clear();
        showProblems = false;
        buildOutputScroll = 0;
        buildOutputFollowTail = true;
        buildOutput.add((tests ? "> mvn test" : "> mvn package")
                + "  [" + displayProject(buildProject) + "]");

        lastProcessProject = buildProject;
        startProcess(tests ? "byte-test" : "byte-build", () ->
                tests ? buildProvider.test(buildProject) : buildProvider.build(buildProject), true);
    }

    private void runProgram() {
        if (openFile == null) {
            buildOutput.add("[INFO] Open a Java file first.");
            return;
        }
        if (buffer.isDirty()) {
            buildOutput.add("[INFO] Save the file before running it.");
            return;
        }
        if (processRunning()) {
            buildOutput.add("[INFO] A process is already running.");
            return;
        }

        var mainClass = MainClassDetector.detect(buffer.getText());
        if (mainClass.isEmpty()) {
            buildOutput.add("[INFO] No runnable main method found in this file.");
            return;
        }
        Project runProject = activeBuildProject();
        if (!buildProvider.supports(runProject) || !(buildProvider instanceof MavenBuildProvider maven)) {
            buildOutput.add("[INFO] Run is currently supported for Maven projects only.");
            return;
        }

        buildOutput.clear();
        buildOutputScroll = 0;
        buildOutputFollowTail = true;
        buildOutput.add("> mvn exec:java -Dexec.mainClass=" + mainClass.get()
                + "  [" + displayProject(runProject) + "]");
        lastProcessProject = runProject;
        startProcess("byte-run", () -> maven.run(runProject, mainClass.get()), false);
    }

    @FunctionalInterface
    private interface ProcessStarter {
        BuildProcess start() throws IOException;
    }

    private void startProcess(String threadName, ProcessStarter starter, boolean parseProblems) {
        Thread thread = new Thread(() -> {
            try {
                BuildProcess process = starter.start();
                runningProcess = process;
                process.onOutput(line -> postToUi(() -> {
                    String text = formatOutputLine(line);
                    buildOutput.add(text);
                    if (parseProblems) {
                        ProblemsParser.parseLine(line.text()).ifPresent(problems::add);
                    }
                }));
                int exit = process.waitForExit();
                postToUi(() -> {
                    buildOutput.add("Process finished with exit code " + exit);
                    runningProcess = null;
                });
            } catch (IOException e) {
                postToUi(() -> {
                    buildOutput.add("[ERROR] Failed to start process: " + e.getMessage());
                    runningProcess = null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postToUi(() -> runningProcess = null);
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean processRunning() {
        return runningProcess != null && runningProcess.isAlive();
    }

    private void jumpToProblem(Problem problem) {
        Path path = problem.file();
        if (!path.isAbsolute()) {
            Project sourceProject = lastProcessProject != null ? lastProcessProject : activeBuildProject();
            path = sourceProject.getRoot().resolve(path).normalize();
        }
        openFile(path);
        if (openFile != null && openFile.equals(path.toAbsolutePath().normalize())) {
            buffer.setCursor(Math.max(0, problem.line() - 1), Math.max(0, problem.column() - 1));
            focus = Focus.EDITOR;
            showProblems = false;
        }
    }

    /**
     * Keeps the explorer rooted at the launched workspace, while build/test/run
     * operate on the nearest Maven project containing the currently open file.
     * This is important for repositories that contain nested examples/modules.
     */
    private Project activeBuildProject() {
        if (openFile != null) {
            return ProjectDetector.detectNearest(openFile);
        }
        return project;
    }

    private String displayProject(Project buildProject) {
        Path root = buildProject.getRoot().toAbsolutePath().normalize();
        Path workspace = project.getRoot().toAbsolutePath().normalize();
        if (root.equals(workspace)) {
            return buildProject.getName();
        }
        try {
            return workspace.relativize(root).toString();
        } catch (IllegalArgumentException ignored) {
            return buildProject.getName();
        }
    }

    private String formatOutputLine(OutputLine line) {
        String text = sanitizeForTerminal(line.text());
        return line.stream() == OutputLine.Stream.STDERR && !text.startsWith("[ERROR]")
                ? "[ERROR] " + text : text;
    }

    private static String sanitizeForTerminal(String text) {
        String noAnsi = text.replaceAll("\u001B\\[[0-?]*[ -/]*[@-~]", "");
        StringBuilder cleaned = new StringBuilder(noAnsi.length());
        for (int i = 0; i < noAnsi.length(); i++) {
            char c = noAnsi.charAt(i);
            if (c == '\t') cleaned.append("    ");
            else if (c >= 0x20 && c != 0x7F) cleaned.append(c);
        }
        return cleaned.toString();
    }

    private void render() throws IOException {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();
        TextGraphics tg = screen.newTextGraphics();
        tg.setBackgroundColor(Theme.BACKGROUND);
        tg.fillRectangle(new TerminalPosition(0, 0), size, ' ');

        if (width < 60 || height < 18) {
            tg.setForegroundColor(Theme.ACCENT);
            tg.putString(1, 1, truncate("BYTE needs a terminal of at least 60x18", Math.max(0, width - 2)));
            screen.setCursorPosition(null);
            screen.refresh(Screen.RefreshType.DELTA);
            return;
        }

        tg.setForegroundColor(Theme.ACCENT);
        tg.putString(2, 0, "BYTE v0.1 — Terminal Development Workbench");
        tg.setForegroundColor(Theme.TEXT);
        tg.putString(2, 1, "File  Edit  Search  Build  Run  Tools  Help");

        // Single source of truth for pane geometry, shared with mouse
        // hit-testing (handleMouse -> layout()) and paging (editorHeight()).
        // Previously this method, layout(), and editorHeight() each
        // recomputed the same footer/bottom/top/explorer math independently.
        Layout l = layout();

        drawBox(tg, 0, l.top(), l.explorerWidth(), l.topHeight(), focus == Focus.EXPLORER ? "PROJECT *" : "PROJECT");
        String editorLabel = openFile == null ? "NO FILE" : openFile.getFileName() + (buffer.isDirty() ? " *" : "");
        drawBox(tg, l.explorerWidth(), l.top(), l.width() - l.explorerWidth(), l.topHeight(), editorLabel);
        drawBox(tg, 0, l.bottomTop(), l.width(), l.bottomHeight(), showProblems ? "PROBLEMS (" + problems.size() + ")" : "BUILD OUTPUT");

        renderExplorer(tg, 1, l.contentTop(), l.explorerWidth() - 2, l.editorContentHeight());
        renderEditor(tg, l.explorerWidth() + 1, l.contentTop(), l.width() - l.explorerWidth() - 2, l.editorContentHeight());
        if (showProblems) {
            renderProblems(tg, 1, l.bottomTop() + 1, l.width() - 2, l.bottomContentHeight());
        } else {
            renderBuildOutput(tg, 1, l.bottomTop() + 1, l.width() - 2, l.bottomContentHeight());
        }
        renderFunctionKeyBar(tg, l.width(), l.footerRow());
        positionCursor(l.explorerWidth() + 1, l.contentTop(), l.width() - l.explorerWidth() - 2, l.editorContentHeight());
        screen.refresh(forceCompleteRefresh ? Screen.RefreshType.COMPLETE : Screen.RefreshType.DELTA);
        forceCompleteRefresh = false;
    }

    private boolean handleMouse(MouseAction mouse) {
        Layout layout = layout();
        if (layout == null) {
            return true;
        }

        TerminalPosition pos = mouse.getPosition();
        int col = pos.getColumn();
        int row = pos.getRow();

        // Mouse wheel follows the pane under the pointer instead of stealing
        // keyboard focus. This keeps mouse support useful but unobtrusive.
        if (mouse.getActionType() == MouseActionType.SCROLL_UP
                || mouse.getActionType() == MouseActionType.SCROLL_DOWN) {
            int amount = mouse.getActionType() == MouseActionType.SCROLL_UP ? -3 : 3;
            if (layout.inExplorer(col, row)) {
                explorer.moveBy(amount);
            } else if (layout.inEditor(col, row)) {
                viewport.scrollLines(amount, buffer.getLineCount(), layout.editorContentHeight());
            } else if (layout.inBottom(col, row)) {
                if (showProblems && !problems.isEmpty()) {
                    selectedProblem = Math.max(0, Math.min(problems.size() - 1, selectedProblem + amount));
                } else if (!showProblems) {
                    int maxStart = Math.max(0, buildOutput.size() - layout.bottomContentHeight());
                    buildOutputScroll = Math.max(0, Math.min(maxStart, buildOutputScroll + amount));
                    buildOutputFollowTail = buildOutputScroll >= maxStart;
                }
            }
            return true;
        }

        // Act on mouse-down only; CLICK_RELEASE capture still gives us normal
        // clicks without processing the same click twice.
        if (mouse.getActionType() != MouseActionType.CLICK_DOWN || mouse.getButton() != 1) {
            return true;
        }

        if (row == layout.footerRow()) {
            return handleFooterClick(col, layout.width());
        }

        if (layout.inExplorer(col, row)) {
            focus = Focus.EXPLORER;
            int index = explorerScroll + (row - layout.contentTop());
            if (explorer.selectIndex(index)) {
                ExplorerModel.Entry selected = explorer.selected();
                if (selected != null) {
                    if (selected.directory()) {
                        explorer.toggleSelectedDirectory();
                    } else {
                        openFile(selected.path());
                        focus = Focus.EDITOR;
                    }
                }
            }
            return true;
        }

        if (layout.inEditor(col, row)) {
            focus = Focus.EDITOR;
            showProblems = false;
            int line = viewport.topLine() + (row - layout.contentTop());
            line = Math.max(0, Math.min(buffer.getLineCount() - 1, line));
            int gutter = editorGutter();
            int displayColumn = viewport.leftColumn()
                    + Math.max(0, col - (layout.editorX() + 1 + gutter));
            int rawColumn = EditorViewport.rawColumnForDisplay(buffer.getLine(line), displayColumn);
            buffer.setCursor(line, rawColumn);
            return true;
        }

        if (layout.inBottom(col, row) && showProblems) {
            focus = Focus.PROBLEMS;
            if (!problems.isEmpty()) {
                int visibleHeight = layout.bottomContentHeight();
                int start = Math.max(0, Math.min(selectedProblem, problems.size() - 1) - visibleHeight + 1);
                int index = start + (row - (layout.bottomTop() + 1));
                if (index >= 0 && index < problems.size()) {
                    selectedProblem = index;
                }
            }
            return true;
        }

        return true;
    }

    private boolean handleFooterClick(int col, int width) {
        int slotWidth = Math.max(6, width / 10);
        int slot = Math.min(9, Math.max(0, col / slotWidth));
        return switch (slot) {
            case 1 -> { focus = focus == Focus.EXPLORER ? Focus.EDITOR : Focus.EXPLORER; yield true; }
            case 4 -> { triggerBuild(false); yield true; }
            case 5 -> { runProgram(); yield true; }
            case 6 -> { triggerBuild(true); yield true; }
            case 7 -> {
                showProblems = true;
                focus = Focus.PROBLEMS;
                selectedProblem = Math.min(selectedProblem, Math.max(0, problems.size() - 1));
                yield true;
            }
            case 9 -> false;
            default -> true;
        };
    }

    private int editorGutter() {
        return Math.max(4, String.valueOf(buffer.getLineCount()).length()) + 1;
    }

    private Layout layout() {
        if (screen == null) return null;
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();
        if (width < 60 || height < 18) return null;
        int footerRow = height - 1;
        int bottomHeight = Math.max(7, height / 3);
        int bottomTop = footerRow - bottomHeight;
        int top = 2;
        int topHeight = bottomTop - top;
        int explorerWidth = Math.max(20, Math.min(30, width / 4));
        return new Layout(width, height, footerRow, bottomTop, bottomHeight, top, topHeight, explorerWidth);
    }

    private record Layout(int width, int height, int footerRow, int bottomTop, int bottomHeight,
                          int top, int topHeight, int explorerWidth) {
        int editorX() { return explorerWidth; }
        int contentTop() { return top + 1; }
        int editorContentHeight() { return Math.max(1, topHeight - 2); }
        int bottomContentHeight() { return Math.max(1, bottomHeight - 2); }
        boolean inExplorer(int x, int y) {
            return x > 0 && x < explorerWidth - 1 && y >= contentTop() && y < top + topHeight - 1;
        }
        boolean inEditor(int x, int y) {
            return x > explorerWidth && x < width - 1 && y >= contentTop() && y < top + topHeight - 1;
        }
        boolean inBottom(int x, int y) {
            return x > 0 && x < width - 1 && y > bottomTop && y < footerRow;
        }
    }

    private int editorHeight() {
        Layout l = layout();
        return l != null ? l.editorContentHeight() : 10;
    }

    private void drawBox(TextGraphics tg, int x, int y, int w, int h, String label) {
        if (w < 2 || h < 2) return;
        TextCharacter horizontal = new TextCharacter(Theme.HORIZONTAL, Theme.BORDER, Theme.BACKGROUND);
        TextCharacter vertical = new TextCharacter(Theme.VERTICAL, Theme.BORDER, Theme.BACKGROUND);
        tg.drawLine(new TerminalPosition(x + 1, y), new TerminalPosition(x + w - 2, y), horizontal);
        tg.drawLine(new TerminalPosition(x + 1, y + h - 1), new TerminalPosition(x + w - 2, y + h - 1), horizontal);
        tg.drawLine(new TerminalPosition(x, y + 1), new TerminalPosition(x, y + h - 2), vertical);
        tg.drawLine(new TerminalPosition(x + w - 1, y + 1), new TerminalPosition(x + w - 1, y + h - 2), vertical);
        tg.setCharacter(x, y, new TextCharacter(Theme.TOP_LEFT, Theme.BORDER, Theme.BACKGROUND));
        tg.setCharacter(x + w - 1, y, new TextCharacter(Theme.TOP_RIGHT, Theme.BORDER, Theme.BACKGROUND));
        tg.setCharacter(x, y + h - 1, new TextCharacter(Theme.BOTTOM_LEFT, Theme.BORDER, Theme.BACKGROUND));
        tg.setCharacter(x + w - 1, y + h - 1, new TextCharacter(Theme.BOTTOM_RIGHT, Theme.BORDER, Theme.BACKGROUND));
        String tag = " " + label + " ";
        tg.setForegroundColor(Theme.ACCENT);
        tg.setBackgroundColor(Theme.BACKGROUND);
        tg.putString(x + 2, y, truncate(tag, Math.max(0, w - 4)));
    }

    private void renderExplorer(TextGraphics tg, int x, int y, int width, int height) {
        List<ExplorerModel.Entry> entries = explorer.visibleEntries();
        int selected = explorer.selectedIndex();
        if (selected < explorerScroll) explorerScroll = selected;
        if (selected >= explorerScroll + height) explorerScroll = selected - height + 1;
        explorerScroll = Math.max(0, explorerScroll);

        for (int row = 0; row < height; row++) {
            int index = explorerScroll + row;
            if (index >= entries.size()) break;
            ExplorerModel.Entry entry = entries.get(index);
            String indent = "  ".repeat(Math.max(0, entry.depth()));
            String marker = entry.directory() ? (entry.expanded() ? "▼ " : "▶ ") : "  ";
            String name = entry.path().equals(project.getRoot()) ? project.getName() : entry.path().getFileName().toString();
            boolean active = focus == Focus.EXPLORER && index == selected;
            tg.setForegroundColor(active ? Theme.FKEY_NUM_FG : (entry.directory() ? Theme.ACCENT : Theme.TEXT));
            tg.setBackgroundColor(active ? Theme.FKEY_NUM_BG : Theme.BACKGROUND);
            String line = truncate(indent + marker + name, width);
            tg.putString(x, y + row, pad(line, width));
        }
        tg.setBackgroundColor(Theme.BACKGROUND);
        renderScrollbar(tg, x + width, y, height, entries.size(), explorerScroll);
    }

    private void renderEditor(TextGraphics tg, int x, int y, int width, int height) {
        int gutter = editorGutter();
        int contentWidth = Math.max(1, width - gutter);
        viewport.keepCursorVisible(buffer, contentWidth, height);

        for (int row = 0; row < height; row++) {
            int lineIndex = viewport.topLine() + row;
            if (lineIndex >= buffer.getLineCount()) break;
            tg.setBackgroundColor(Theme.BACKGROUND);
            tg.setForegroundColor(Theme.DIM_TEXT);
            tg.putString(x, y + row, String.format("%" + (gutter - 1) + "d ", lineIndex + 1));
            String expanded = sanitizeForTerminal(EditorViewport.expandTabs(buffer.getLine(lineIndex)));
            String visible = slice(expanded, viewport.leftColumn(), contentWidth);
            tg.setForegroundColor(Theme.TEXT);
            tg.putString(x + gutter, y + row, visible);
        }
        renderScrollbar(tg, x + width, y, height, buffer.getLineCount(), viewport.topLine());
    }

    private void positionCursor(int x, int y, int width, int height) {
        if (focus != Focus.EDITOR) {
            screen.setCursorPosition(null);
            return;
        }
        int gutter = editorGutter();
        int contentWidth = Math.max(1, width - gutter);
        viewport.keepCursorVisible(buffer, contentWidth, height);
        int displayCol = EditorViewport.displayColumn(buffer.getLine(buffer.getCursorRow()), buffer.getCursorCol());
        int screenRow = y + buffer.getCursorRow() - viewport.topLine();
        int screenCol = x + gutter + displayCol - viewport.leftColumn();
        if (screenRow >= y && screenRow < y + height && screenCol >= x + gutter && screenCol < x + width) {
            screen.setCursorPosition(new TerminalPosition(screenCol, screenRow));
        } else {
            screen.setCursorPosition(null);
        }
    }

    private void renderBuildOutput(TextGraphics tg, int x, int y, int width, int height) {
        int maxStart = Math.max(0, buildOutput.size() - height);
        buildOutputScroll = buildOutputFollowTail ? maxStart : Math.min(buildOutputScroll, maxStart);
        int start = buildOutputScroll;
        for (int i = start; i < buildOutput.size() && i - start < height; i++) {
            String line = buildOutput.get(i);
            tg.setBackgroundColor(Theme.BACKGROUND);
            tg.setForegroundColor(line.startsWith("[ERROR]") ? Theme.ERROR_TEXT : Theme.OUTPUT_TEXT);
            tg.putString(x, y + i - start, truncate(line, width));
        }
        renderScrollbar(tg, x + width, y, height, buildOutput.size(), buildOutputScroll);
    }

    private void renderProblems(TextGraphics tg, int x, int y, int width, int height) {
        if (problems.isEmpty()) {
            tg.setForegroundColor(Theme.DIM_TEXT);
            tg.setBackgroundColor(Theme.BACKGROUND);
            tg.putString(x, y, "No compiler problems captured.");
            return;
        }
        int start = Math.max(0, Math.min(selectedProblem, problems.size() - 1) - height + 1);
        for (int row = 0; row < height && start + row < problems.size(); row++) {
            int index = start + row;
            boolean active = focus == Focus.PROBLEMS && index == selectedProblem;
            tg.setForegroundColor(active ? Theme.FKEY_NUM_FG : Theme.ERROR_TEXT);
            tg.setBackgroundColor(active ? Theme.FKEY_NUM_BG : Theme.BACKGROUND);
            tg.putString(x, y + row, pad(truncate(problems.get(index).toString(), width), width));
        }
        tg.setBackgroundColor(Theme.BACKGROUND);
    }

    private void renderFunctionKeyBar(TextGraphics tg, int width, int y) {
        String[] labels = {"Help", "Files", "Search", "Output", "Build", "Run", "Test", "Problems", "Cmds", "Quit"};
        boolean[] enabled = {false, true, false, false, true, true, true, true, false, true};
        int slotWidth = Math.max(6, width / labels.length);
        int x = 0;
        for (int i = 0; i < labels.length && x < width; i++) {
            String number = String.valueOf(i + 1);
            tg.setForegroundColor(Theme.FKEY_NUM_FG);
            tg.setBackgroundColor(Theme.FKEY_NUM_BG);
            tg.putString(x, y, truncate(number, Math.min(2, width - x)));
            int labelX = x + 2;
            if (labelX < width) {
                tg.setForegroundColor(enabled[i] ? Theme.FKEY_LABEL_FG : Theme.FKEY_LABEL_FG_DISABLED);
                tg.setBackgroundColor(enabled[i] ? Theme.FKEY_LABEL_BG : Theme.FKEY_LABEL_BG_DISABLED);
                int labelWidth = Math.max(1, Math.min(slotWidth - 2, width - labelX));
                tg.putString(labelX, y, pad(truncate(labels[i], labelWidth), labelWidth));
            }
            x += slotWidth;
        }
    }

    /**
     * Draws a vertical scrollbar by overwriting the pane's already-drawn
     * right border column with a proportional thumb — no-op (leaving the
     * plain border) when {@code totalItems} fits entirely within
     * {@code height}, so it only appears when a pane's content actually
     * overflows its visible area.
     */
    private void renderScrollbar(TextGraphics tg, int trackX, int y, int height, int totalItems, int viewStart) {
        if (height <= 0 || totalItems <= height) {
            return;
        }
        int thumbSize = Math.max(1, height * height / totalItems);
        int maxThumbTop = Math.max(0, height - thumbSize);
        int maxViewStart = Math.max(1, totalItems - height);
        int clampedViewStart = Math.max(0, Math.min(maxViewStart, viewStart));
        int thumbTop = maxThumbTop == 0 ? 0 : Math.round(clampedViewStart * (float) maxThumbTop / maxViewStart);

        for (int row = 0; row < height; row++) {
            boolean isThumb = row >= thumbTop && row < thumbTop + thumbSize;
            tg.setCharacter(trackX, y + row, new TextCharacter(
                    isThumb ? Theme.SCROLLBAR_THUMB : Theme.VERTICAL,
                    isThumb ? Theme.ACCENT : Theme.BORDER,
                    Theme.BACKGROUND));
        }
    }

    private static String slice(String s, int start, int width) {
        if (width <= 0 || start >= s.length()) return "";
        int from = Math.max(0, start);
        return s.substring(from, Math.min(s.length(), from + width));
    }

    private static String truncate(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        return s.length() <= maxWidth ? s : s.substring(0, maxWidth);
    }

    private static String pad(String s, int width) {
        if (width <= 0) return "";
        return s + " ".repeat(Math.max(0, width - s.length()));
    }
}