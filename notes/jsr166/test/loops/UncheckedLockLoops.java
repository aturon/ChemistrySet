/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
/*
 * @test
 * @summary basic safety and liveness of ReentrantLocks, and other locks based on them
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public final class UncheckedLockLoops {
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static boolean doBuiltin = true;

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;
        int iters = 10000000;

        if (args.length > 0)
            maxThreads = Integer.parseInt(args[0]);

        rng.setSeed(3122688L);

        print = false;
        System.out.println("Warmup...");
        oneTest(1, 100000);
        Thread.sleep(1000);
        oneTest(3, 10000);
        Thread.sleep(1000);
        oneTest(2, 10000);
        Thread.sleep(100);
        oneTest(1, 100000);
        Thread.sleep(100);
        oneTest(1, 100000);
        Thread.sleep(1000);
        print = true;

        System.out.println("Threads:" + 1);
        oneTest(1, iters / 1);
        Thread.sleep(100);

        for (int i = 1; i <= maxThreads; i += (i+1) >>> 1) {
            System.out.println("Threads:" + i);
            oneTest(i, iters / i);
            Thread.sleep(100);
        }

    }

    static void oneTest(int nthreads, int iters) throws Exception {
        int fairIters = (nthreads <= 1) ? iters : iters/20;
        int v = rng.next();

        if (print)
            System.out.print("NoLock (1 thread)     ");
        new NoLockLoop().test(v, 1, iters * nthreads);
        Thread.sleep(10);

        if (print)
            System.out.print("ReentrantLock         ");
        new ReentrantLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (false) {
        if (print)
            System.out.print("FairReentrantLock     ");
        new FairReentrantLockLoop().test(v, nthreads, fairIters);
        Thread.sleep(10);
        }
        if (doBuiltin) {
            if (print)
                System.out.print("builtin lock          ");
            new BuiltinLockLoop().test(v, nthreads, fairIters);
            Thread.sleep(10);
        }

        if (print)
            System.out.print("Mutex                 ");
        new MutexLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("LongMutex             ");
        new LongMutexLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("Semaphore             ");
        new SemaphoreLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("FairSemaphore         ");
        new FairSemaphoreLoop().test(v, nthreads, fairIters);
        Thread.sleep(10);

        if (print)
            System.out.print("ReentrantWriteLock    ");
        new ReentrantWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("FairRWriteLock         ");
        new FairReentrantWriteLockLoop().test(v, nthreads, fairIters);
        Thread.sleep(10);

        if (print)
            System.out.print("ReentrantReadWriteLock");
        new ReentrantReadWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("FairRReadWriteLock     ");
        new FairReentrantReadWriteLockLoop().test(v, nthreads, fairIters);
        Thread.sleep(10);

    }

    abstract static class LockLoop implements Runnable {
        int value;
        int checkValue;
        int iters;
        volatile int result;
        volatile int failures;
        final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier;

        final int setValue(int v) {
            checkValue = v ^ 0x55555555;
            value = v;
            return v;
        }

        final int getValue() {
            int v = value;
            if (checkValue != ~(v ^ 0xAAAAAAAA))
                ++failures;
            return v;
        }

        final void test(int initialValue, int nthreads, int iters) throws Exception {
            setValue(initialValue);
            this.iters = iters;
            barrier = new CyclicBarrier(nthreads+1, timer);
            for (int i = 0; i < nthreads; ++i)
                new Thread(this).start();
            barrier.await();
            barrier.await();
            long time = timer.getTime();
            if (print) {
                long tpi = time / (iters * nthreads);
                System.out.print("\t" + LoopHelpers.rightJustify(tpi) + " ns per update");
                //                double secs = (double) time / 1000000000.0;
                //                System.out.print("\t " + secs + "s run time");
                System.out.println();
            }

            if (result == 0) // avoid overoptimization
                System.out.println("useless result: " + result);
            if (failures != 0)
                throw new Error("protection failure?");
        }
        abstract int loop(int n);
        public final void run() {
            try {
                barrier.await();
                result += loop(iters);
                barrier.await();
            }
            catch (Exception ie) {
                return;
            }
        }

    }

    private static class NoLockLoop extends LockLoop {
        private volatile int readBarrier;
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                int r1 = readBarrier;
                x = setValue(LoopHelpers.compute1(getValue()));
                int r2 = readBarrier;
                if (r1 == r2 && (x & 255) == 0)
                    ++readBarrier;
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class BuiltinLockLoop extends LockLoop {
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                synchronized (this) {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantLockLoop extends LockLoop {
        private final ReentrantLock lock = new ReentrantLock();
        final int loop(int n) {
            final ReentrantLock lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class MutexLoop extends LockLoop {
        private final Mutex lock = new Mutex();
        final int loop(int n) {
            final Mutex lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class LongMutexLoop extends LockLoop {
        private final LongMutex lock = new LongMutex();
        final int loop(int n) {
            final LongMutex lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class FairReentrantLockLoop extends LockLoop {
        private final ReentrantLock lock = new ReentrantLock(true);
        final int loop(int n) {
            final ReentrantLock lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantWriteLockLoop extends LockLoop {
        private final Lock lock = new ReentrantReadWriteLock().writeLock();
        final int loop(int n) {
            final Lock lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class FairReentrantWriteLockLoop extends LockLoop {
        final Lock lock = new ReentrantReadWriteLock(true).writeLock();
        final int loop(int n) {
            final Lock lock = this.lock;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class SemaphoreLoop extends LockLoop {
        private final Semaphore sem = new Semaphore(1, false);
        final int loop(int n) {
            final Semaphore sem = this.sem;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                sem.acquireUninterruptibly();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    sem.release();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }
    private static class FairSemaphoreLoop extends LockLoop {
        private final Semaphore sem = new Semaphore(1, true);
        final int loop(int n) {
            final Semaphore sem = this.sem;
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                sem.acquireUninterruptibly();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    sem.release();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantReadWriteLockLoop extends LockLoop {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final int loop(int n) {
            final Lock rlock = lock.readLock();
            final Lock wlock = lock.writeLock();
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                if ((n & 16) != 0) {
                    rlock.lock();
                    try {
                        x = LoopHelpers.compute1(getValue());
                        x = LoopHelpers.compute2(x);
                    }
                    finally {
                        rlock.unlock();
                    }
                }
                else {
                    wlock.lock();
                    try {
                        setValue(x);
                    }
                    finally {
                        wlock.unlock();
                    }
                    sum += LoopHelpers.compute2(x);
                }
            }
            return sum;
        }

    }


    private static class FairReentrantReadWriteLockLoop extends LockLoop {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        final int loop(int n) {
            final Lock rlock = lock.readLock();
            final Lock wlock = lock.writeLock();
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                if ((n & 16) != 0) {
                    rlock.lock();
                    try {
                        x = LoopHelpers.compute1(getValue());
                        x = LoopHelpers.compute2(x);
                    }
                    finally {
                        rlock.unlock();
                    }
                }
                else {
                    wlock.lock();
                    try {
                        setValue(x);
                    }
                    finally {
                        wlock.unlock();
                    }
                    sum += LoopHelpers.compute2(x);
                }
            }
            return sum;
        }

    }
}
