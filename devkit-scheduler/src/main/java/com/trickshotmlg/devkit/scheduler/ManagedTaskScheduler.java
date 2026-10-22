package com.trickshotmlg.devkit.scheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Callable;

final class ManagedTaskScheduler implements TaskScheduler {
    private final ManagedTaskScope root;

    ManagedTaskScheduler(TaskBackend backend, TaskFailureHandler failureHandler) {
        this.root = new ManagedTaskScope(Objects.requireNonNull(backend), Objects.requireNonNull(failureHandler), null);
    }

    @Override public SyncTaskRunner sync() { return root.sync(); }
    @Override public AsyncTaskRunner async() { return root.async(); }
    @Override public TaskScope scope() { return root.childScope(); }
    @Override public boolean isClosed() { return root.isClosed(); }
    @Override public void close() { root.close(); }

    static final class ManagedTaskScope implements TaskScope {
        private final TaskBackend backend;
        private final TaskFailureHandler failureHandler;
        private final ManagedTaskScope parent;
        private final Object lock = new Object();
        private final java.util.Set<TaskRecord> tasks = new java.util.HashSet<>();
        private final java.util.Set<ManagedTaskScope> children = new java.util.HashSet<>();
        private final AtomicLong ids;
        private final SyncTaskRunner sync = new Runner(this, TaskExecutionTarget.SYNC);
        private final AsyncTaskRunner async = new Runner(this, TaskExecutionTarget.ASYNC);
        private boolean closed;

        ManagedTaskScope(TaskBackend backend, TaskFailureHandler failureHandler, ManagedTaskScope parent) {
            this.backend = backend;
            this.failureHandler = failureHandler;
            this.parent = parent;
            this.ids = parent == null ? new AtomicLong() : parent.ids;
        }

        @Override public SyncTaskRunner sync() { return sync; }
        @Override public AsyncTaskRunner async() { return async; }

        @Override public TaskScope childScope() {
            ManagedTaskScope child = new ManagedTaskScope(backend, failureHandler, this);
            synchronized (lock) {
                ensureOpen();
                children.add(child);
            }
            return child;
        }

        @Override public boolean isClosed() { synchronized (lock) { return closed; } }
        @Override public int activeTaskCount() { synchronized (lock) { return tasks.size(); } }

        private void ensureOpen() {
            if (closed) throw new IllegalStateException("Task scope is closed");
        }

        private TaskHandle schedule(TaskExecutionTarget target, TaskSchedule schedule, TaskCallback callback) {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(schedule, "schedule");
            Objects.requireNonNull(callback, "callback");
            TaskRecord record = new TaskRecord(ids.incrementAndGet(), target, schedule, this, callback,
                    failureHandler);
            synchronized (lock) {
                ensureOpen();
                tasks.add(record);
            }
            record.setTerminalCallback(() -> removeTask(record));
            try {
                BackendTask nativeTask = backend.schedule(target, schedule, record::dispatch);
                record.attach(nativeTask);
            } catch (Throwable failure) {
                record.failBeforeDispatch(failure);
            }
            return record;
        }

        private void removeTask(TaskRecord task) {
            synchronized (lock) { tasks.remove(task); }
        }

        private void removeChild(ManagedTaskScope child) {
            synchronized (lock) { children.remove(child); }
        }

        @Override public void close() {
            java.util.List<TaskRecord> taskCopy;
            java.util.List<ManagedTaskScope> childCopy;
            synchronized (lock) {
                if (closed) return;
                closed = true;
                taskCopy = java.util.List.copyOf(tasks);
                childCopy = java.util.List.copyOf(children);
                tasks.clear();
                children.clear();
            }
            childCopy.forEach(ManagedTaskScope::close);
            taskCopy.forEach(TaskRecord::cancel);
            if (parent != null) parent.removeChild(this);
        }
    }

    private static final class Runner implements SyncTaskRunner, AsyncTaskRunner {
        private final ManagedTaskScope scope;
        private final TaskExecutionTarget target;
        private Runner(ManagedTaskScope scope, TaskExecutionTarget target) { this.scope = scope; this.target = target; }

        @Override public TaskHandle run(Runnable action) {
            Objects.requireNonNull(action, "action");
            return run(context -> action.run());
        }
        @Override public TaskHandle run(TaskCallback action) {
            return scope.schedule(target, TaskSchedule.immediate(), action);
        }
        @Override public TaskHandle later(TickDelay delay, Runnable action) {
            Objects.requireNonNull(action, "action");
            return later(delay, context -> action.run());
        }
        @Override public TaskHandle later(TickDelay delay, TaskCallback action) {
            return scope.schedule(target, TaskSchedule.later(Objects.requireNonNull(delay, "delay")), action);
        }
        @Override public TaskHandle repeat(TickDelay initialDelay, TickPeriod period, Runnable action) {
            Objects.requireNonNull(action, "action");
            return repeat(initialDelay, period, context -> action.run());
        }
        @Override public TaskHandle repeat(TickDelay initialDelay, TickPeriod period, TaskCallback action) {
            return scope.schedule(target, TaskSchedule.repeat(initialDelay, period), action);
        }
        @Override public <T> AsyncTaskResult<T> submit(Callable<T> action) {
            if (target != TaskExecutionTarget.ASYNC) {
                throw new UnsupportedOperationException("Results can only be submitted through the async runner");
            }
            return AsyncTaskResult.submit(scope, action);
        }
        @Override public TaskHandle nextTick(Runnable action) {
            Objects.requireNonNull(action, "action");
            return nextTick(context -> action.run());
        }
        @Override public TaskHandle nextTick(TaskCallback action) {
            return scope.schedule(target, TaskSchedule.nextTick(), action);
        }
    }
}
