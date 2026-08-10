package com.byteeditor.core.build;

import com.byteeditor.core.project.BuildSystem;
import com.byteeditor.core.project.Project;

import java.io.IOException;
import java.util.List;

/** The v0.1 {@link BuildProvider}: shells out to {@code mvn} on the system PATH. */
public final class MavenBuildProvider implements BuildProvider {

    private final String mvnExecutable;

    public MavenBuildProvider() {
        this("mvn");
    }

    /** Allows tests (or a future settings screen) to point at a specific mvn binary/wrapper. */
    public MavenBuildProvider(String mvnExecutable) {
        this.mvnExecutable = mvnExecutable;
    }

    @Override
    public boolean supports(Project project) {
        return project.getBuildSystem() == BuildSystem.MAVEN;
    }

    @Override
    public BuildProcess build(Project project) throws IOException {
        requireSupported(project);
        return BuildProcess.start(mavenCommand("package"), project.getRoot());
    }

    @Override
    public BuildProcess test(Project project) throws IOException {
        requireSupported(project);
        return BuildProcess.start(mavenCommand("test"), project.getRoot());
    }

    /**
     * Runs a Java main class through Maven so project dependencies are on the
     * runtime classpath.
     *
     * <p>This deliberately uses the {@code exec-maven-plugin}'s {@code exec}
     * goal, not its {@code java} goal. {@code exec:java} runs the target
     * class's {@code main()} on a thread <em>inside Maven's own JVM</em> via
     * a classloader — there is no separate child process for the user's
     * code at all. That means {@link BuildProcess#kill()} has nothing real
     * to terminate: it can kill the {@code mvn} process, but an
     * infinite-loop thread started via {@code exec:java} is not guaranteed
     * to die with it, and can be left running/orphaned depending on how
     * {@code mvn} forks on the host. {@code exec:exec} instead spawns a
     * genuine {@code java} child process (using the plugin's {@code
     * %classpath} token so the resolved dependency classpath still applies),
     * which {@code kill()}'s descendant-killing logic can actually reach.
     */
    public BuildProcess run(Project project, String mainClass) throws IOException {
        requireSupported(project);
        return BuildProcess.start(runCommand(mainClass), project.getRoot());
    }

    /** Package-visible so the exact command line can be asserted on in tests without starting a real process. */
    List<String> runCommand(String mainClass) {
        return List.of(mvnExecutable, "-B", "-Dstyle.color=never", "-Duser.language=en",
                "-Dexec.executable=java",
                "-Dexec.args=-cp %classpath " + mainClass,
                "org.codehaus.mojo:exec-maven-plugin:3.5.0:exec");
    }

    /**
     * Builds the mvn command line, forcing batch mode, disabling color, and
     * pinning the locale to English.
     *
     * <p>Without {@code -B}, Maven may emit ANSI escape sequences (progress
     * bars, colorized log levels) even when its stdout is piped rather than
     * attached to a real terminal — those escape codes are control
     * characters that a TUI renderer cannot safely display as text. Batch
     * mode plus an explicit color override closes that off at the source;
     * {@link com.byteeditor.tui.Main}'s output sanitization is the backstop
     * for anything that still slips through.
     *
     * <p>{@code -Duser.language=en} exists for a different reason: without
     * it, Maven and javac emit diagnostic text in whatever locale the host
     * JVM defaults to, which is confusing for the majority-English build
     * console regardless of parsing concerns. {@link ProblemsParser}
     * happens to already be locale-agnostic — it anchors only on the
     * {@code file.java:[line,col]} bracket shape javac emits regardless of
     * locale, treating everything after it as an opaque message — but
     * forcing English output here removes the mixed-language noise for any
     * developer on a non-English-locale machine, independent of that.
     *
     * <p>Package-visible for the same reason as {@link #runCommand}.
     */
    List<String> mavenCommand(String goal) {
        return List.of(mvnExecutable, "-B", "-Dstyle.color=never", "-Duser.language=en", goal);
    }

    private void requireSupported(Project project) {
        if (!supports(project)) {
            throw new IllegalArgumentException(
                    "MavenBuildProvider cannot build a project without a pom.xml: " + project.getRoot());
        }
    }
}
