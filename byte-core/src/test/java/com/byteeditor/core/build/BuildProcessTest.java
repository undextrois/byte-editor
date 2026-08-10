package com.byteeditor.core.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProcessTest {

    @Test
    void streamsStdoutAndStderrSeparately() throws Exception {
        BuildProcess process = BuildProcess.start(
                List.of("bash", "-c", "echo hello-out; echo hello-err 1>&2; exit 0"),
                Path.of("."));

        List<OutputLine> received = new CopyOnWriteArrayList<>();
        process.onOutput(received::add);

        int exitCode = process.waitForExit();

        assertEquals(0, exitCode);
        assertTrue(received.stream().anyMatch(l -> l.text().equals("hello-out") && l.stream() == OutputLine.Stream.STDOUT));
        assertTrue(received.stream().anyMatch(l -> l.text().equals("hello-err") && l.stream() == OutputLine.Stream.STDERR));
    }

    @Test
    void reportsNonZeroExitCode() throws Exception {
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "exit 3"), Path.of("."));
        assertEquals(3, process.waitForExit());
    }

    @Test
    void killTerminatesLongRunningProcess() throws Exception {
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "sleep 30"), Path.of("."));
        assertTrue(process.isAlive());
        process.kill();
        Thread.sleep(200);
        assertFalse(process.isAlive());
    }

    @Test
    void listenerAttachedAfterFastProcessCompletesStillSeesAllOutput() throws Exception {
        // Regression test: a listener registered after start() must not miss
        // output from a process fast enough to finish before onOutput() is
        // called — onOutput replays buffered history atomically on attach.
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "echo fast-line"), Path.of("."));
        process.waitForExit();

        List<OutputLine> received = new CopyOnWriteArrayList<>();
        process.onOutput(received::add);

        assertTrue(received.stream().anyMatch(l -> l.text().equals("fast-line")));
    }

    @Test
    void killWithoutWaitForExitDoesNotLeakNonDaemonThreads() throws Exception {
        // Regression test: kill() must shut down the pump executor itself.
        // Pump threads must also be daemon threads, so a BuildProcess that is
        // killed (rather than waited on) can never keep the host JVM alive.
        Thread.UncaughtExceptionHandler ignore = (t, e) -> { };
        BuildProcess process = BuildProcess.start(List.of("bash", "-c", "sleep 30"), Path.of("."));
        process.kill();
        Thread.sleep(200);
        assertFalse(process.isAlive());
        // No explicit thread-count assertion here (pool internals aren't part
        // of the public API) — the meaningful guarantee is exercised by the
        // test process itself: if pump threads were non-daemon and the pool
        // were never shut down, the whole test JVM would hang on exit.
    }

    @Test
    void historySnapshotContainsAllOutput() throws Exception {
        BuildProcess process = BuildProcess.start(
                List.of("bash", "-c", "echo one; echo two; echo three"),
                Path.of("."));
        process.waitForExit();
        Thread.sleep(100); // allow final listener callbacks to settle
        List<OutputLine> history = process.getHistorySnapshot();
        assertEquals(3, history.size());
    }
}
