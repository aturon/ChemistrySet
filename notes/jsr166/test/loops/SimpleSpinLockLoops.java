/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public final class SimpleSpinLockLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static int iters = 2000000;

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;
        if (args.length > 0)
            maxThreads = Integer.parseInt(args[0]);

        new LockLoop(1).test();
        new LockLoop(1).test();
        print = true;

        int k = 1;
        for (int i = 1; i <= maxThreads;) {
            System.out.print("Threads: " + i);
            new LockLoop(i).test();
            Thread.sleep(100);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            }
            else
                i = k;
        }
        pool.shutdown();
    }

    static final class LockLoop implements Runnable {
        private int v = rng.next();
        private volatile int result = 17;
        private final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        private final CyclicBarrier barrier;
        private final int nthreads;
        private volatile int readBarrier;
        private final AtomicInteger spinlock = new AtomicInteger();
        LockLoop(int nthreads) {
            this.nthreads = nthreads;
            barrier = new CyclicBarrier(nthreads+1, timer);
        }

        final void test() throws Exception {
            for (int i = 0; i < nthreads; ++i)
                pool.execute(this);
            barrier.await();
            barrier.await();
            if (print) {
                long time = timer.getTime();
                long tpi = time / ((long) iters * nthreads);
                System.out.print("\t" + LoopHelpers.rightJustify(tpi) + " ns per lock");
                double secs = (double) time / 1000000000.0;
                System.out.println("\t " + secs + "s run time");
            }

            int r = result;
            if (r == 0) // avoid overoptimization
                System.out.println("useless result: " + r);
        }

        public final void run() {
            final AtomicInteger lock = this.spinlock;
            try {
                barrier.await();
                int sum = v + 1;
                int x = 0;
                int n = iters;
                while (n-- > 0) {
                    while (!lock.compareAndSet(0, 1)) ;
                    int k = (sum & 3);
                    if (k > 0) {
                        x = v;
                        while (k-- > 0)
                            x = LoopHelpers.compute6(x);
                        v = x;
                    }
                    else x = sum + 1;
                    lock.set(0);
                    if ((x += readBarrier) == 0)
                        ++readBarrier;
                    for (int l = x & 1; l > 0; --l)
                        sum += LoopHelpers.compute6(sum);
                }
                barrier.await();
                result += sum;
            }
            catch (Exception ie) {
                return;
            }
        }
    }

}
