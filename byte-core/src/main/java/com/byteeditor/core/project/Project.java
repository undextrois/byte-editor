package com.byteeditor.core.project;

import java.nio.file.Path;
import java.util.List;

/**
 * A minimal in-memory model of "what Byte knows about the folder it was
 * opened on." Deliberately thin for v0.1 — no dependency graph, no module
 * resolution, just enough to drive the file explorer and pick a build
 * provider.
 */
public final class Project {

    private final Path root;
    private final String name;
    private final BuildSystem buildSystem;
    private final List<Path> sourceRoots;

    public Project(Path root, String name, BuildSystem buildSystem, List<Path> sourceRoots) {
        this.root = root;
        this.name = name;
        this.buildSystem = buildSystem;
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    public Path getRoot() {
        return root;
    }

    public String getName() {
        return name;
    }

    public BuildSystem getBuildSystem() {
        return buildSystem;
    }

    public List<Path> getSourceRoots() {
        return sourceRoots;
    }

    public boolean hasBuildSystem() {
        return buildSystem != BuildSystem.NONE;
    }
}
