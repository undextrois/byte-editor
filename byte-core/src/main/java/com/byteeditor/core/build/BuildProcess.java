package com.byteeditor.core.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wraps a running external process (e.g. {@code mvn package}) and streams its
 * stdout/stderr line-by-line to listeners as it runs — this is a live process
 * console, not a full terminal emulator.
 *
 * <p>Output is also retained in a bounded ring buffer so a UI can redraw the
 * console pane from scratch (e.g. on resize) without re-running the process.
 * The cap exists specifically so a runaway process (an accidental infinite
 * {@code println} loop in the user's own {@code main}) cannot exhaust heap —
 * oldest lines are evicted first, which is an acceptable trade for a build
 * console.
 */
public final class BuildProcess {

    private static final int MAX_BUFFERED_LINES = 5000;

    private final Process process;
    private final List<Consumer<OutputLine>> listeners = new CopyOnWriteArrayList<>();
    private final Deque<OutputLine> history = new ArrayDeque<>();
    // Daemon threads: a BuildProcess that is killed (rather than waited on)
    // must never keep the host JVM alive just because its pump threads are
    // sitting idle in the pool.
    private final ExecutorService pumpExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "byte-build-process-pump");
        t.setDaemon(true);
        return t;
    });
    private final CountDownLatch pumpsFinished = new CountDownLatch(2);

    private BuildProcess(Process process) {
        this.process = process;
        pumpExecutor.submit(() -> pump(process.getInputStream(), OutputLine.Stream.STDOUT));
        pumpExecutor.submit(() -> pump(process.getErrorStream(), OutputLine.Stream.STDERR));
    }

    /**
     * Starts an external process with the given command line in the given
     * working directory. Both stdout and stderr are captured (not merged) and
     * streamed to registered listeners as lines arrive.
     */
    public static BuildProcess start(List<String> command, Path workingDirectory) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(false);
        Process process = pb.start();
        return new BuildProcess(process);
    }

    /**
     * Registers a listener invoked (on a background thread) for every output
     * line as it arrives. To avoid a race where a fast-exiting process (e.g.
     * a bare {@code echo}) finishes producing output before the caller gets
     * around to calling this method, any lines already buffered are replayed
     * to the listener synchronously before it is added to the live set —
     * attach is atomic with respect to the pump threads, so no line can be
     * skipped or double-delivered.
     */
    public synchronized void onOutput(Consumer<OutputLine> listener) {
        for (OutputLine buffered : history) {
            listener.accept(buffered);
        }
        listeners.add(listener);
    }

    /** Returns a snapshot of buffered output collected so far, oldest first. */
    public synchronized List<OutputLine> getHistorySnapshot() {
        return List.copyOf(history);
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Forcibly terminates the process (and its descendants where the JVM supports it). */
    public void kill() {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        pumpExecutor.shutdown();
    }

    /** Blocks until the process exits, then returns its exit code. */
    public int waitForExit() throws InterruptedException {
        int code = process.waitFor();
        pumpsFinished.await(5, TimeUnit.SECONDS); // let output pumps flush before returning
        pumpExecutor.shutdown();
        return code;
    }

    private void pump(java.io.InputStream stream, OutputLine.Stream which) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                OutputLine outputLine = new OutputLine(line, which);
                // Recording to history and notifying current listeners must be
                // atomic with onOutput's replay-then-attach, or a listener
                // attaching concurrently could miss this exact line.
                synchronized (this) {
                    history.addLast(outputLine);
                    if (history.size() > MAX_BUFFERED_LINES) {
                        history.removeFirst();
                    }
                    for (Consumer<OutputLine> listener : listeners) {
                        listener.accept(outputLine);
                    }
                }
            }
        } catch (IOException e) {
            // Stream closed because the process exited; not an error condition.
        } finally {
            pumpsFinished.countDown();
        }
    }
}
