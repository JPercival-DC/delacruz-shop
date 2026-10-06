package edu.cit.delacruz.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs tasks for a given key strictly in submission order, one at a time,
 * never dropping one and never running two for the same key concurrently.
 * Different keys run fully in parallel, each on its own dedicated thread,
 * so a backlog on one key never delays another key's updates.
 * <p>
 * A single-thread executor per key gives this for free: its work queue is
 * unbounded and FIFO, so every submit() is guaranteed to eventually run,
 * in order, with nothing skipped.
 * <p>
 * Earlier version of this class coalesced bursts (skipped a submission if
 * one for the same key was already queued, on the theory that the task
 * always reads "current state" so only the last run in a burst mattered).
 * That was wrong for this use: the requirement is that EVERY stock change
 * gets its own publish to Tiangge, not just the final value after a
 * burst - coalescing was silently dropping individual accepted-order
 * updates. Plain per-key FIFO trades that away for a different, accepted
 * cost: under a sustained heavy burst on one product, queue depth (and so
 * latency for the last item) can grow, bounded only by queue length times
 * per-call duration rather than a fixed ceiling.
 */
class PerProductSerialDispatcher {

    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

    /** Guarantees {@code task} eventually runs for {@code key}, in submission order, never skipped. */
    void submit(String key, Runnable task) {
        executorFor(key).execute(task);
    }

    private ExecutorService executorFor(String key) {
        return executors.computeIfAbsent(key, k -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tiangge-stock-" + k);
            t.setDaemon(true);
            return t;
        }));
    }
}