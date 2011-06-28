/*
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Tries to demonstrate a leaked interrupt from FutureTask.cancel(true).
 */
public class FutureTaskCancelLoops {
    static long millisElapsedSince(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos)/(1000L*1000L);
    }

    public static void main(String[] args) throws Exception {

        long startTime = System.nanoTime();

        final BlockingQueue<Runnable> q = new LinkedBlockingQueue<Runnable>(10000);

        final ThreadPoolExecutor pool =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, q);

        final AtomicLong count = new AtomicLong(0);

        final AtomicReference<Future<?>> cancelMe
            = new AtomicReference<Future<?>>(null);

        final AtomicBoolean leakedInterrupt = new AtomicBoolean(false);
        final AtomicBoolean goHome = new AtomicBoolean(false);

        class InterruptMeTask extends FutureTask<Void> {
            InterruptMeTask() { this(new AtomicReference<Future<?>>()); }
            InterruptMeTask(final AtomicReference<Future<?>> myFuture) {
                super(new Runnable() {
                    public void run() {
                        if (cancelMe.get() != null) {
                            // We're likely to get the interrupt meant for previous task.
                            // Clear interrupts first to prove *we* got interrupted.
                            Thread.interrupted();
                            while (cancelMe.get() != null && !goHome.get()) {
                                if (Thread.interrupted()) {
                                    leakedInterrupt.set(true);
                                    goHome.set(true);
                                    System.err.println("leaked interrupt!");
                                }
                            }
                        } else {
                            cancelMe.set(myFuture.get());
                            do {} while (! myFuture.get().isCancelled() &&
                                         !goHome.get());
                        }
                        count.getAndIncrement();

                        if (q.isEmpty())
                            for (int i = 0, n = q.remainingCapacity(); i < n; i++)
                                pool.execute(new InterruptMeTask());
                    }}, null);
                myFuture.set(this);
            }
        }

        pool.execute(new InterruptMeTask()); // "starter" task

        while (!goHome.get() && millisElapsedSince(startTime) < 1000L) {
            Future<?> future = cancelMe.get();
            if (future != null) {
                future.cancel(true);
                cancelMe.set(null);
            }
        }

        goHome.set(true);
        pool.shutdown();

        if (leakedInterrupt.get()) {
            String msg = String.format
                ("%d tasks run, %d millis elapsed, till leaked interrupt%n",
                 count.get(), millisElapsedSince(startTime));
            throw new IllegalStateException(msg);
        } else {
            System.out.printf
                ("%d tasks run, %d millis elapsed%n",
                 count.get(), millisElapsedSince(startTime));
        }
    }
}
