package com.byteeditor.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Dependency-free companion to {@link ExplorerModelTest}. Not part of the
 * shipped build; delete once {@code mvn test} is runnable against your
 * Nexus mirror and the real JUnit suite covers this instead.
 */
public final class ExplorerModelManualCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("explorer-model-check-");
        try {
            buildTempTree(root);

            rootStartsExpandedWithTopLevelEntriesVisible(root);
            hiddenEntriesAreExcluded(root);
            directoriesSortBeforeFilesAndBothAlphabeticallyCaseInsensitive(root);
            expandingADirectoryRevealsItsChildren(root);
            collapsingADirectoryHidesItsChildrenAgain(root);
            toggleSelectedDirectoryFlipsExpandState(root);
            collapseOnRootDoesNotRemoveRoot(root);
            moveUpAndDownClampAtBounds(root);
            moveByClampsToValidRange(root);
            selectIndexRejectsOutOfRangeAndAcceptsValid(root);
            nestedExpansionRevealsGrandchildren(root);
        } finally {
            deleteRecursive(root);
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void buildTempTree(Path root) throws IOException {
        Files.createFile(root.resolve("README.md"));
        Files.createFile(root.resolve("pom.xml"));
        Files.createDirectory(root.resolve("src"));
        Files.createFile(root.resolve("src/App.java"));
        Files.createDirectory(root.resolve("src/util"));
        Files.createFile(root.resolve("src/util/Helper.java"));
        Files.createDirectory(root.resolve(".git"));
        Files.createFile(root.resolve(".git/HEAD"));
        Files.createDirectory(root.resolve(".idea"));
        Files.createFile(root.resolve(".DS_Store"));
    }

    private static void deleteRecursive(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
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
        check(name + " (expected=" + expected + ", actual=" + actual + ")", Objects.equals(expected, actual));
    }

    static void rootStartsExpandedWithTopLevelEntriesVisible(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        List<ExplorerModel.Entry> entries = model.visibleEntries();
        eq("explorer.initialCount", 4, entries.size());
        eq("explorer.rootPath", root.toAbsolutePath().normalize(), entries.get(0).path());
        check("explorer.rootExpanded", entries.get(0).expanded());
    }

    static void hiddenEntriesAreExcluded(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        boolean anyHidden = model.visibleEntries().stream().anyMatch(e -> {
            String name = e.path().getFileName().toString();
            return name.equals(".git") || name.equals(".idea") || name.equals(".DS_Store");
        });
        check("explorer.hiddenExcluded", !anyHidden);
    }

    static void directoriesSortBeforeFilesAndBothAlphabeticallyCaseInsensitive(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        List<ExplorerModel.Entry> entries = model.visibleEntries();
        check("explorer.srcIsDirectory", entries.get(1).directory());
        eq("explorer.sortSrc", "src", entries.get(1).path().getFileName().toString());
        eq("explorer.sortPom", "pom.xml", entries.get(2).path().getFileName().toString());
        eq("explorer.sortReadme", "README.md", entries.get(3).path().getFileName().toString());
    }

    static void expandingADirectoryRevealsItsChildren(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown();
        model.expandSelected();
        boolean seesAppJava = model.visibleEntries().stream()
                .anyMatch(e -> e.path().getFileName().toString().equals("App.java"));
        check("explorer.expandReveals", seesAppJava);
    }

    static void collapsingADirectoryHidesItsChildrenAgain(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown();
        model.expandSelected();
        int expandedCount = model.visibleEntries().size();
        model.collapseSelected();
        int collapsedCount = model.visibleEntries().size();
        check("explorer.collapseHides", collapsedCount < expandedCount);
    }

    static void toggleSelectedDirectoryFlipsExpandState(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown();
        int before = model.visibleEntries().size();
        model.toggleSelectedDirectory();
        int afterExpand = model.visibleEntries().size();
        check("explorer.toggleExpands", afterExpand > before);
        model.toggleSelectedDirectory();
        int afterCollapse = model.visibleEntries().size();
        eq("explorer.toggleCollapsesBack", before, afterCollapse);
    }

    static void collapseOnRootDoesNotRemoveRoot(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.collapseSelected();
        check("explorer.rootSurvivesCollapse", !model.visibleEntries().isEmpty());
        eq("explorer.rootStillFirst", root.toAbsolutePath().normalize(), model.visibleEntries().get(0).path());
    }

    static void moveUpAndDownClampAtBounds(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveUp();
        eq("explorer.moveUpClamp", 0, model.selectedIndex());

        int last = model.visibleEntries().size() - 1;
        for (int i = 0; i < last + 5; i++) {
            model.moveDown();
        }
        eq("explorer.moveDownClamp", last, model.selectedIndex());
    }

    static void moveByClampsToValidRange(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveBy(-100);
        eq("explorer.moveByLow", 0, model.selectedIndex());
        model.moveBy(1000);
        eq("explorer.moveByHigh", model.visibleEntries().size() - 1, model.selectedIndex());
    }

    static void selectIndexRejectsOutOfRangeAndAcceptsValid(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        check("explorer.selectNegativeRejected", !model.selectIndex(-1));
        check("explorer.selectHugeRejected", !model.selectIndex(9999));
        check("explorer.selectValidAccepted", model.selectIndex(1));
        eq("explorer.selectValidIndex", 1, model.selectedIndex());
    }

    static void nestedExpansionRevealsGrandchildren(Path root) {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown(); // "src"
        model.expandSelected();
        boolean found = false;
        for (int i = 0; i < model.visibleEntries().size(); i++) {
            if (model.visibleEntries().get(i).path().getFileName().toString().equals("util")) {
                model.selectIndex(i);
                found = true;
                break;
            }
        }
        check("explorer.foundNestedDir", found);
        model.expandSelected();
        boolean seesHelper = model.visibleEntries().stream()
                .anyMatch(e -> e.path().getFileName().toString().equals("Helper.java"));
        check("explorer.grandchildRevealed", seesHelper);
    }
}
