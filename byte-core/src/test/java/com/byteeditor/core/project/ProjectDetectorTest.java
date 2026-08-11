package com.byteeditor.core.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectDetectorTest {

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

    @Test
    void findsPomInTheFilesOwnDirectory() throws IOException {
        Path moduleRoot = Files.createTempDirectory("nearest-same-dir-");
        try {
            Files.createFile(moduleRoot.resolve("pom.xml"));
            Path file = moduleRoot.resolve("Standalone.java");
            Files.createFile(file);

            Project project = ProjectDetector.detectNearest(file);
            assertEquals(BuildSystem.MAVEN, project.getBuildSystem());
            assertEquals(moduleRoot.toRealPath(), project.getRoot().toRealPath());
        } finally {
            deleteRecursive(moduleRoot);
        }
    }

    @Test
    void walksUpMultipleLevelsToFindPom() throws IOException {
        Path moduleRoot = Files.createTempDirectory("nearest-multi-level-");
        try {
            Files.createFile(moduleRoot.resolve("pom.xml"));
            Path deep = moduleRoot.resolve("src/main/java/com/example");
            Files.createDirectories(deep);
            Path file = deep.resolve("App.java");
            Files.createFile(file);

            Project project = ProjectDetector.detectNearest(file);
            assertEquals(BuildSystem.MAVEN, project.getBuildSystem());
            assertEquals(moduleRoot.toRealPath(), project.getRoot().toRealPath());
        } finally {
            deleteRecursive(moduleRoot);
        }
    }

    @Test
    void prefersNearestPomOverAnAggregatorPomFurtherUp() throws IOException {
        // This is the exact real-world bug: a workspace root pom.xml
        // (aggregator, packaging=pom) and a nested module's own pom.xml
        // both exist in the file's ancestry. detectNearest must return the
        // nearer module, not the outer aggregator -- returning the
        // aggregator is what produced "@ byte-parent" instead of
        // "@ hello-java" and a ClassNotFoundException, since the aggregator
        // has no compiled classes of its own.
        Path workspaceRoot = Files.createTempDirectory("nearest-aggregator-");
        try {
            Files.createFile(workspaceRoot.resolve("pom.xml")); // outer aggregator
            Path innerModule = workspaceRoot.resolve("examples/hello-java");
            Files.createDirectories(innerModule);
            Files.createFile(innerModule.resolve("pom.xml")); // nested module's own pom

            Path srcFile = innerModule.resolve("src/main/java/com/example/App.java");
            Files.createDirectories(srcFile.getParent());
            Files.createFile(srcFile);

            Project project = ProjectDetector.detectNearest(srcFile);
            assertEquals(innerModule.toRealPath(), project.getRoot().toRealPath());
        } finally {
            deleteRecursive(workspaceRoot);
        }
    }

    @Test
    void acceptsADirectoryDirectlyNotJustAFile() throws IOException {
        Path moduleRoot = Files.createTempDirectory("nearest-dir-input-");
        try {
            Files.createFile(moduleRoot.resolve("pom.xml"));
            Path subDir = moduleRoot.resolve("src");
            Files.createDirectory(subDir);

            Project project = ProjectDetector.detectNearest(subDir);
            assertEquals(moduleRoot.toRealPath(), project.getRoot().toRealPath());
        } finally {
            deleteRecursive(moduleRoot);
        }
    }

    @Test
    void fallsBackToTheOriginalDirectoryWhenNoPomExistsAnywhereUpward() throws IOException {
        // Assumes no ancestor of the OS temp directory itself happens to
        // contain a pom.xml, which holds on essentially any real machine.
        Path isolated = Files.createTempDirectory("nearest-no-pom-");
        try {
            Path nested = isolated.resolve("a/b/c");
            Files.createDirectories(nested);
            Path file = nested.resolve("Something.java");
            Files.createFile(file);

            Project project = ProjectDetector.detectNearest(file);
            assertEquals(BuildSystem.NONE, project.getBuildSystem());
            assertEquals(nested.toRealPath(), project.getRoot().toRealPath());
        } finally {
            deleteRecursive(isolated);
        }
    }

    @Test
    void nullTargetThrows() {
        assertThrows(IllegalArgumentException.class, () -> ProjectDetector.detectNearest(null));
    }
}
