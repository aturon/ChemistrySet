/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
/*
 * @test
 * @bug 4486658
 * @compile -source 1.5 TimeoutLockLoops.java
 * @run main TimeoutLockLoops
 * @summary Checks for responsiveness of locks to timeouts.
 * Runs under the assumption that ITERS computations require more than
 * TIMEOUT msecs to complete, which seems to be a safe assumption for
 * another decade.
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public final class TimeoutLockLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static final int ITERS = Integer.MAX_VALUE;
    static final long TIMEOUT = 100;

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;
        if (args.length > 0)
            maxThreads = Integer.parseInt(args[0]);

        print = true;

        for (int i = 1; i <= maxThreads; i += (i+1) >>> 1) {
            System.out.print("Threads: " + i);
            new ReentrantLockLoop(i).test();
            //            Thread.sleep(10);
        }
        pool.shutdown();
    }

    static final class ReentrantLockLoop implements Runnable {
        private int v = rng.next();
        private volatile boolean completed;
        private volatile int result = 17;
        private final ReentrantLock lock = new ReentrantLock();
        private final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        private final CyclicBarrier barrier;
        private final int nthreads;
        ReentrantLockLoop(int nthreads) {
            this.nthreads = nthreads;
            barrier = new CyclicBarrier(nthreads+1, timer);
        }

        final void test() throws Exception {
            for (int i = 0; i < nthreads; ++i) {
                lock.lock();
                pool.execute(this);
                lock.unlock();
            }
            barrier.await();
            Thread.sleep(TIMEOUT);
            while (!lock.tryLock()); // Jam lock
            //            lock.lock();
            barrier.await();
            if (print) {
                long time = timer.getTime();
                double secs = (double) time / 1000000000.0;
                System.out.println("\t " + secs + "s run time");
            }

            if (completed)
                throw new Error("Some thread completed instead of timing out");
            int r = result;
            if (r == 0) // avoid overoptimization
                System.out.println("useless result: " + r);
        }

        public final void run() {
            try {
                barrier.await();
                int sum = v;
                int x = 17;
                int n = ITERS;
                final ReentrantLock lock = this.lock;
                for (;;) {
                    if (x != 0) {
                        if (n-- <= 0)
                            break;
                    }
                    if (!lock.tryLock(TIMEOUT, TimeUnit.MILLISECONDS))
                        break;
                    try {
                        v = x = LoopHelpers.compute1(v);
                    }
                    finally {
                        lock.unlock();
                    }
                    sum += LoopHelpers.compute2(x);
                }
                if (n <= 0)
                    completed = true;
                barrier.await();
                result += sum;
            }
            catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
        }
    }


}
