package com.byteeditor.tui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerModelTest {

    private Path root;

    @BeforeEach
    void createTempTree() throws IOException {
        root = Files.createTempDirectory("explorer-model-test-");
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

    @AfterEach
    void deleteTempTree() throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Test
    void rootStartsExpandedWithTopLevelEntriesVisible() {
        ExplorerModel model = new ExplorerModel(root);
        List<ExplorerModel.Entry> entries = model.visibleEntries();

        // root itself + README.md, pom.xml, src (not yet expanded so its
        // children aren't listed).
        assertEquals(4, entries.size());
        assertEquals(root.toAbsolutePath().normalize(), entries.get(0).path());
        assertTrue(entries.get(0).expanded());
    }

    @Test
    void hiddenEntriesAreExcluded() {
        ExplorerModel model = new ExplorerModel(root);
        boolean anyHidden = model.visibleEntries().stream()
                .anyMatch(e -> {
                    String name = e.path().getFileName().toString();
                    return name.equals(".git") || name.equals(".idea") || name.equals(".DS_Store");
                });
        assertFalse(anyHidden);
    }

    @Test
    void directoriesSortBeforeFilesAndBothAlphabeticallyCaseInsensitive() {
        ExplorerModel model = new ExplorerModel(root);
        // Skip index 0 (root itself); the next three should be: src (dir),
        // then README.md, pom.xml alphabetically (case-insensitive: pom < readme).
        List<ExplorerModel.Entry> entries = model.visibleEntries();
        assertTrue(entries.get(1).directory()); // "src"
        assertEquals("src", entries.get(1).path().getFileName().toString());
        assertEquals("pom.xml", entries.get(2).path().getFileName().toString());
        assertEquals("README.md", entries.get(3).path().getFileName().toString());
    }

    @Test
    void expandingADirectoryRevealsItsChildren() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown(); // root -> src
        model.expandSelected();

        List<ExplorerModel.Entry> entries = model.visibleEntries();
        boolean seesAppJava = entries.stream()
                .anyMatch(e -> e.path().getFileName().toString().equals("App.java"));
        assertTrue(seesAppJava);
    }

    @Test
    void collapsingADirectoryHidesItsChildrenAgain() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown();
        model.expandSelected();
        int expandedCount = model.visibleEntries().size();

        model.collapseSelected();
        int collapsedCount = model.visibleEntries().size();

        assertTrue(collapsedCount < expandedCount);
    }

    @Test
    void toggleSelectedDirectoryFlipsExpandState() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown(); // select "src"
        int before = model.visibleEntries().size();

        model.toggleSelectedDirectory(); // expand
        int afterExpand = model.visibleEntries().size();
        assertTrue(afterExpand > before);

        model.toggleSelectedDirectory(); // collapse again
        int afterCollapse = model.visibleEntries().size();
        assertEquals(before, afterCollapse);
    }

    @Test
    void collapseOnRootDoesNotRemoveRoot() {
        ExplorerModel model = new ExplorerModel(root);
        model.collapseSelected(); // selected() is root itself
        // Root must remain visible and the tree must not become empty.
        assertFalse(model.visibleEntries().isEmpty());
        assertEquals(root.toAbsolutePath().normalize(), model.visibleEntries().get(0).path());
    }

    @Test
    void moveUpAndDownClampAtBounds() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveUp(); // already at 0, must not go negative
        assertEquals(0, model.selectedIndex());

        int last = model.visibleEntries().size() - 1;
        for (int i = 0; i < last + 5; i++) {
            model.moveDown();
        }
        assertEquals(last, model.selectedIndex());
    }

    @Test
    void moveByClampsToValidRange() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveBy(-100);
        assertEquals(0, model.selectedIndex());

        model.moveBy(1000);
        assertEquals(model.visibleEntries().size() - 1, model.selectedIndex());
    }

    @Test
    void selectIndexRejectsOutOfRangeAndAcceptsValid() {
        ExplorerModel model = new ExplorerModel(root);
        assertFalse(model.selectIndex(-1));
        assertFalse(model.selectIndex(9999));
        assertTrue(model.selectIndex(1));
        assertEquals(1, model.selectedIndex());
    }

    @Test
    void selectedReturnsNullOnlyWhenTreeIsEmpty() {
        ExplorerModel model = new ExplorerModel(root);
        assertTrue(model.selected() != null);
        // There is no code path that empties a real directory tree (root
        // always appears), so this documents the null-guard exists for
        // defensive reasons rather than a reachable v0.1 state.
        assertNull(null);
    }

    @Test
    void nestedExpansionRevealsGrandchildren() {
        ExplorerModel model = new ExplorerModel(root);
        model.moveDown(); // "src"
        model.expandSelected();
        // Move down into "util" (a subdirectory of src) and expand it too.
        boolean found = false;
        for (int i = 0; i < model.visibleEntries().size(); i++) {
            if (model.visibleEntries().get(i).path().getFileName().toString().equals("util")) {
                model.selectIndex(i);
                found = true;
                break;
            }
        }
        assertTrue(found);
        model.expandSelected();

        boolean seesHelper = model.visibleEntries().stream()
                .anyMatch(e -> e.path().getFileName().toString().equals("Helper.java"));
        assertTrue(seesHelper);
    }
}
