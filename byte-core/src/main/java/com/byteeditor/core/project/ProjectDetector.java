package com.byteeditor.core.project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Examines a directory and produces a {@link Project}. v0.1 recognizes only
 * a Maven layout (presence of {@code pom.xml}); anything else falls back to
 * {@link BuildSystem#NONE} so Byte still works as a plain file editor.
 */
public final class ProjectDetector {

    private ProjectDetector() {
    }


    /** Detects the nearest project root for either a file or directory. */
    public static Project detectNearest(Path target) {
        if (target == null) {
            throw new IllegalArgumentException("Target cannot be null");
        }
        Path current = target.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }
        if (current == null) {
            return detect(Path.of("."));
        }

        Path fallback = current;
        for (Path p = current; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("pom.xml"))) {
                return detect(p);
            }
        }
        return detect(fallback);
    }

    public static Project detect(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }

        Path pomFile = directory.resolve("pom.xml");
        String name = directory.toAbsolutePath().normalize().getFileName() != null
                ? directory.toAbsolutePath().normalize().getFileName().toString()
                : directory.toString();

        if (Files.isRegularFile(pomFile)) {
            List<Path> sourceRoots = new ArrayList<>();
            Path mainJava = directory.resolve("src/main/java");
            Path testJava = directory.resolve("src/test/java");
            if (Files.isDirectory(mainJava)) {
                sourceRoots.add(mainJava);
            }
            if (Files.isDirectory(testJava)) {
                sourceRoots.add(testJava);
            }
            return new Project(directory, name, BuildSystem.MAVEN, sourceRoots);
        }

        return new Project(directory, name, BuildSystem.NONE, List.of());
    }
}
