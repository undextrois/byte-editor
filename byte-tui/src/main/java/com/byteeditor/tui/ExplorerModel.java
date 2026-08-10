package com.byteeditor.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Minimal keyboard-driven project tree for Byte v0.1. */
final class ExplorerModel {

    record Entry(Path path, int depth, boolean directory, boolean expanded) { }

    private final Path root;
    private final Set<Path> expanded = new HashSet<>();
    private List<Entry> visible = List.of();
    private int selectedIndex;

    ExplorerModel(Path root) {
        this.root = root.toAbsolutePath().normalize();
        expanded.add(this.root);
        refresh();
    }

    List<Entry> visibleEntries() {
        return visible;
    }

    int selectedIndex() {
        return selectedIndex;
    }

    Entry selected() {
        return visible.isEmpty() ? null : visible.get(selectedIndex);
    }

    void moveUp() {
        selectedIndex = Math.max(0, selectedIndex - 1);
    }

    void moveDown() {
        selectedIndex = Math.min(Math.max(0, visible.size() - 1), selectedIndex + 1);
    }

    void moveBy(int delta) {
        selectedIndex = Math.max(0, Math.min(Math.max(0, visible.size() - 1), selectedIndex + delta));
    }

    boolean selectIndex(int index) {
        if (index < 0 || index >= visible.size()) {
            return false;
        }
        selectedIndex = index;
        return true;
    }

    void expandSelected() {
        Entry entry = selected();
        if (entry != null && entry.directory()) {
            expanded.add(entry.path());
            refreshKeeping(entry.path());
        }
    }

    void collapseSelected() {
        Entry entry = selected();
        if (entry == null) {
            return;
        }
        if (entry.directory() && expanded.contains(entry.path()) && !entry.path().equals(root)) {
            expanded.remove(entry.path());
            refreshKeeping(entry.path());
            return;
        }
        Path parent = entry.path().getParent();
        if (parent != null && parent.startsWith(root)) {
            selectPath(parent);
        }
    }

    void toggleSelectedDirectory() {
        Entry entry = selected();
        if (entry == null || !entry.directory()) {
            return;
        }
        if (expanded.contains(entry.path())) {
            if (!entry.path().equals(root)) {
                expanded.remove(entry.path());
            }
        } else {
            expanded.add(entry.path());
        }
        refreshKeeping(entry.path());
    }

    private void refreshKeeping(Path path) {
        refresh();
        selectPath(path);
    }

    private void selectPath(Path path) {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).path().equals(path)) {
                selectedIndex = i;
                return;
            }
        }
    }

    private void refresh() {
        List<Entry> entries = new ArrayList<>();
        add(entries, root, 0);
        visible = List.copyOf(entries);
        selectedIndex = Math.min(selectedIndex, Math.max(0, visible.size() - 1));
    }

    private void add(List<Entry> entries, Path path, int depth) {
        boolean directory = Files.isDirectory(path);
        boolean isExpanded = directory && expanded.contains(path);
        entries.add(new Entry(path, depth, directory, isExpanded));
        if (!isExpanded) {
            return;
        }

        try (var stream = Files.list(path)) {
            stream
                    .filter(this::isVisible)
                    .sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(child -> add(entries, child, depth + 1));
        } catch (IOException ignored) {
            // Unreadable directories remain visible but are not expanded.
        }
    }

    private boolean isVisible(Path path) {
        String name = path.getFileName().toString();
        return !name.equals(".git") && !name.equals(".idea") && !name.equals(".DS_Store");
    }
}
