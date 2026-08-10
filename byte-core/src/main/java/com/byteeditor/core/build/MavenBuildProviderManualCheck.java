package com.byteeditor.core.build;

import com.byteeditor.core.project.BuildSystem;
import com.byteeditor.core.project.Project;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Dependency-free companion to {@link com.byteeditor.core.ManualHarness},
 * kept in this package specifically to reach {@link MavenBuildProvider}'s
 * package-private {@code runCommand}/{@code mavenCommand} methods. Not part
 * of the shipped build; delete once {@code mvn test} is runnable against
 * your Nexus mirror and {@link MavenBuildProviderTest} covers this instead.
 */
public final class MavenBuildProviderManualCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        runCommandUsesExecExecWithASeparateJavaProcess();
        runCommandUsesConfiguredMvnExecutable();
        mavenCommandForcesBatchModeAndNoColor();
        buildAndTestAndRunRejectNonMavenProjects();

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
        check(name + " (expected=" + expected + ", actual=" + actual + ")", Objects.equals(expected, actual));
    }

    static void runCommandUsesExecExecWithASeparateJavaProcess() {
        // Regression test for the exec:java -> exec:exec fix: exec:java runs
        // the target's main() inside Maven's own JVM with no real child
        // process for kill() to terminate, which is exactly why Esc
        // couldn't stop a long-running class. Locking the exec:exec goal
        // and argument shape in place so this can't silently regress.
        MavenBuildProvider provider = new MavenBuildProvider("mvn");
        List<String> command = provider.runCommand("com.example.SlowLoop");
        eq("mavenRun.command", List.of(
                "mvn", "-B", "-Dstyle.color=never",
                "-Dexec.executable=java",
                "-Dexec.args=-cp %classpath com.example.SlowLoop",
                "org.codehaus.mojo:exec-maven-plugin:3.5.0:exec"
        ), command);
    }

    static void runCommandUsesConfiguredMvnExecutable() {
        MavenBuildProvider provider = new MavenBuildProvider("/opt/custom/mvn");
        eq("mavenRun.customExecutable", "/opt/custom/mvn", provider.runCommand("App").get(0));
    }

    static void mavenCommandForcesBatchModeAndNoColor() {
        MavenBuildProvider provider = new MavenBuildProvider("mvn");
        eq("mavenBuild.package", List.of("mvn", "-B", "-Dstyle.color=never", "package"), provider.mavenCommand("package"));
        eq("mavenBuild.test", List.of("mvn", "-B", "-Dstyle.color=never", "test"), provider.mavenCommand("test"));
    }

    static void buildAndTestAndRunRejectNonMavenProjects() {
        MavenBuildProvider provider = new MavenBuildProvider();
        Project plainProject = new Project(Path.of("."), "demo", BuildSystem.NONE, List.of());

        check("mavenProvider.buildRejectsNonMaven", threw(() -> provider.build(plainProject)));
        check("mavenProvider.testRejectsNonMaven", threw(() -> provider.test(plainProject)));
        check("mavenProvider.runRejectsNonMaven", threw(() -> provider.run(plainProject, "App")));
    }

    private interface ThrowingCall {
        void run() throws Exception;
    }

    private static boolean threw(ThrowingCall call) {
        try {
            call.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
