package com.byteeditor.core.build;

import com.byteeditor.core.project.Project;

import java.io.IOException;

/**
 * Abstraction over "how to build/test this project." v0.1 ships only
 * {@link MavenBuildProvider}; the seam exists so Gradle/Ant support can be
 * added later without touching Byte's core or TUI layers.
 */
public interface BuildProvider {

    boolean supports(Project project);

    BuildProcess build(Project project) throws IOException;

    BuildProcess test(Project project) throws IOException;
}
