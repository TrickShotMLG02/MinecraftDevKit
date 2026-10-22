package com.trickshotmlg.devkit.scheduler.testing;

import com.trickshotmlg.devkit.scheduler.BackendTask;
import com.trickshotmlg.devkit.scheduler.TaskBackend;
import com.trickshotmlg.devkit.scheduler.TaskExecutionTarget;
import com.trickshotmlg.devkit.scheduler.TaskSchedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic in-memory backend intended for tests and local simulations, not production scheduling.
 * Time advances only when requested; callbacks run on the calling test thread.
 */
public final class ManualTaskBackend implements TaskBackend {
    private final Object lock = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<Entry> sync = new PriorityQueue<>(ORDER);
    private final PriorityQueue<Entry> async = new PriorityQueue<>(ORDER);
    private long currentTick;

    private static final Comparator<Entry> ORDER = Comparator.comparingLong((Entry entry) -> entry.dueTick)
            .thenComparingLong(entry -> entry.sequence);

    @Override public BackendTask schedule(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(callback, "callback");
        synchronized (lock) {
            long delay = schedule instanceof TaskSchedule.Once once ? once.delay().ticks()
                    : ((TaskSchedule.Repeating) schedule).initialDelay().ticks();
            Entry entry = new Entry(target, schedule, callback, Math.addExact(currentTick, delay), sequence.incrementAndGet());
            queue(target).add(entry);
            return () -> cancel(entry);
        }
    }

    /** Advances virtual server time without executing callbacks.
     * @param ticks non-negative number of virtual ticks
     */
    public void advanceTicks(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must not be negative");
        synchronized (lock) { currentTick = Math.addExact(currentTick, ticks); }
    }

    /**
     * Returns current virtual server tick.
     * @return current virtual tick
     */
    public long currentTick() { synchronized (lock) { return currentTick; } }

    /** Runs one snapshot of currently due tasks for a target in deterministic due-time order.
     * @param target context whose due tasks to execute
     * @return number of callbacks dispatched
     */
    public int runDue(TaskExecutionTarget target) {
        Objects.requireNonNull(target, "target");
        List<Entry> due = new ArrayList<>();
        synchronized (lock) {
            PriorityQueue<Entry> queue = queue(target);
            while (!queue.isEmpty() && queue.peek().dueTick <= currentTick) due.add(queue.remove());
            for (Entry entry : due) entry.queued = false;
        }
        int executions = 0;
        for (Entry entry : due) {
            Runnable callback;
            synchronized (lock) {
                if (entry.cancelled) continue;
                callback = entry.callback;
                if (entry.schedule instanceof TaskSchedule.Once) entry.callback = null;
            }
            callback.run();
            executions++;
            if (entry.schedule instanceof TaskSchedule.Repeating) {
                synchronized (lock) {
                    if (!entry.cancelled && entry.callback != null) {
                        long period = ((TaskSchedule.Repeating) entry.schedule).period().ticks();
                        entry.dueTick = Math.addExact(currentTick, period);
                        entry.sequence = sequence.incrementAndGet();
                        entry.queued = true;
                        queue(target).add(entry);
                    }
                }
            } else {
                synchronized (lock) { entry.callback = null; }
            }
        }
        return executions;
    }

    /** Runs due synchronous and asynchronous callbacks in that order, one snapshot per target.
     * @return number of callbacks dispatched
     */
    public int runAllDue() {
        return runDue(TaskExecutionTarget.SYNC) + runDue(TaskExecutionTarget.ASYNC);
    }

    /** Number of queued tasks, including delayed and repeating tasks, for assertions.
     * @return queued entry count
     */
    public int pendingTaskCount() {
        synchronized (lock) { return sync.size() + async.size(); }
    }

    private void cancel(Entry entry) {
        synchronized (lock) {
            if (entry.cancelled) return;
            entry.cancelled = true;
            if (entry.queued) queue(entry.target).remove(entry);
            entry.queued = false;
            entry.callback = null;
        }
    }

    private PriorityQueue<Entry> queue(TaskExecutionTarget target) {
        return target == TaskExecutionTarget.SYNC ? sync : async;
    }

    private static final class Entry {
        private final TaskExecutionTarget target;
        private final TaskSchedule schedule;
        private Runnable callback;
        private long dueTick;
        private long sequence;
        private boolean queued = true;
        private boolean cancelled;

        private Entry(TaskExecutionTarget target, TaskSchedule schedule, Runnable callback, long dueTick, long sequence) {
            this.target = target;
            this.schedule = schedule;
            this.callback = callback;
            this.dueTick = dueTick;
            this.sequence = sequence;
        }
    }
}
