package com.byteeditor.core.build;

import com.byteeditor.core.project.BuildSystem;
import com.byteeditor.core.project.Project;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MavenBuildProviderTest {

    @Test
    void runCommandUsesExecExecWithASeparateJavaProcess() {
        // Regression test for the exec:java -> exec:exec fix: exec:java runs
        // the target's main() inside Maven's own JVM with no real child
        // process for kill() to terminate, which is exactly why Esc
        // couldn't actually stop a long-running class. exec:exec must be
        // used instead, so this locks the goal name and argument shape in
        // place — if this ever silently reverts to exec:java, this test
        // should catch it before a user does.
        MavenBuildProvider provider = new MavenBuildProvider("mvn");

        List<String> command = provider.runCommand("com.example.SlowLoop");

        assertEquals(List.of(
                "mvn", "-B", "-Dstyle.color=never", "-Duser.language=en",
                "-Dexec.executable=java",
                "-Dexec.args=-cp %classpath com.example.SlowLoop",
                "org.codehaus.mojo:exec-maven-plugin:3.5.0:exec"
        ), command);
    }

    @Test
    void runCommandUsesConfiguredMvnExecutable() {
        MavenBuildProvider provider = new MavenBuildProvider("/opt/custom/mvn");
        List<String> command = provider.runCommand("App");
        assertEquals("/opt/custom/mvn", command.get(0));
    }

    @Test
    void mavenCommandForcesBatchModeNoColorAndEnglishLocale() {
        // Regression test: without -B, Maven can emit ANSI escape sequences
        // that previously crashed the TUI renderer (see MavenBuildProvider's
        // class-level rationale). Without -Duser.language=en, build output
        // is at the mercy of the host's default locale. Locking the exact
        // flags in place.
        MavenBuildProvider provider = new MavenBuildProvider("mvn");
        assertEquals(List.of("mvn", "-B", "-Dstyle.color=never", "-Duser.language=en", "package"), provider.mavenCommand("package"));
        assertEquals(List.of("mvn", "-B", "-Dstyle.color=never", "-Duser.language=en", "test"), provider.mavenCommand("test"));
    }

    @Test
    void supportsReturnsTrueOnlyForMavenProjects() {
        MavenBuildProvider provider = new MavenBuildProvider();
        Project mavenProject = new Project(Path.of("."), "demo", BuildSystem.MAVEN, List.of());
        Project plainProject = new Project(Path.of("."), "demo", BuildSystem.NONE, List.of());

        assertEquals(true, provider.supports(mavenProject));
        assertEquals(false, provider.supports(plainProject));
    }

    @Test
    void buildAndTestRejectNonMavenProjects() {
        MavenBuildProvider provider = new MavenBuildProvider();
        Project plainProject = new Project(Path.of("."), "demo", BuildSystem.NONE, List.of());

        assertThrows(IllegalArgumentException.class, () -> provider.build(plainProject));
        assertThrows(IllegalArgumentException.class, () -> provider.test(plainProject));
        assertThrows(IllegalArgumentException.class, () -> provider.run(plainProject, "App"));
    }
}
