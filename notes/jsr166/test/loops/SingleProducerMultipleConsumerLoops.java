/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
//import jsr166y.*;

public class SingleProducerMultipleConsumerLoops {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    // Number of puts by producers or takes by consumers
    static final int ITERS = 1 << 20;

    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;

    // Number of elements passed around -- must be power of two
    // Elements are reused from pool to minimize alloc impact
    static final int POOL_SIZE = 1 << 8;
    static final int POOL_MASK = POOL_SIZE-1;
    static final Integer[] intPool = new Integer[POOL_SIZE];
    static {
        for (int i = 0; i < POOL_SIZE; ++i)
            intPool[i] = Integer.valueOf(i);
    }

    public static void main(String[] args) throws Exception {
        int maxn = 12;

        if (args.length > 0)
            maxn = Integer.parseInt(args[0]);

        print = false;
        warmup();
        print = true;

        int k = 1;
        for (int i = 1; i <= maxn;) {
            System.out.println("Consumers:" + i);
            oneTest(i, ITERS);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            }
            else
                i = k;
        }

        pool.shutdown();
    }

    static void warmup() throws Exception {
        print = false;
        System.out.print("Warmup ");
        int it = 2000;
        for (int j = 5; j > 0; --j) {
            oneTest(j, it);
            System.out.print(".");
            it += 1000;
        }
        System.gc();
        it = 20000;
        for (int j = 5; j > 0; --j) {
            oneTest(j, it);
            System.out.print(".");
            it += 10000;
        }
        System.gc();
        System.out.println();
    }

    static void oneTest(int n, int iters) throws Exception {
        int fairIters = iters/16;

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedTransferQueue     ");
        oneRun(new LinkedTransferQueue<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedBlockingQueue     ");
        oneRun(new LinkedBlockingQueue<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedBlockingQueue(cap)");
        oneRun(new LinkedBlockingQueue<Integer>(POOL_SIZE), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedBlockingDeque     ");
        oneRun(new LinkedBlockingDeque<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("ArrayBlockingQueue      ");
        oneRun(new ArrayBlockingQueue<Integer>(POOL_SIZE), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("SynchronousQueue        ");
        oneRun(new SynchronousQueue<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("SynchronousQueue(fair)  ");
        oneRun(new SynchronousQueue<Integer>(true), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedTransferQueue(xfer)");
        oneRun(new LTQasSQ<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("LinkedTransferQueue(half)");
        oneRun(new HalfSyncLTQ<Integer>(), n, iters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("PriorityBlockingQueue   ");
        oneRun(new PriorityBlockingQueue<Integer>(), n, fairIters);

        Thread.sleep(100); // System.gc();
        if (print)
            System.out.print("ArrayBlockingQueue(fair)");
        oneRun(new ArrayBlockingQueue<Integer>(POOL_SIZE, true), n, fairIters);

    }

    abstract static class Stage implements Runnable {
        final int iters;
        final BlockingQueue<Integer> queue;
        final CyclicBarrier barrier;
        volatile int result;
        Stage(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            queue = q;
            barrier = b;
            this.iters = iters;
        }
    }

    static class Producer extends Stage {
        Producer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);
        }

        public void run() {
            try {
                barrier.await();
                int r = hashCode();
                for (int i = 0; i < iters; ++i) {
                    r = LoopHelpers.compute7(r);
                    Integer v = intPool[r & POOL_MASK];
                    queue.put(v);
                }
                barrier.await();
                result = 432;
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }
    }

    static class Consumer extends Stage {
        Consumer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);

        }

        public void run() {
            try {
                barrier.await();
                int l = 0;
                int s = 0;
                for (int i = 0; i < iters; ++i) {
                    Integer item = queue.take();
                    s += item.intValue();
                }
                barrier.await();
                result = s;
                if (s == 0) System.out.print(" ");
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int nconsumers, int iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(nconsumers + 2, timer);
        pool.execute(new Producer(q, barrier, iters * nconsumers));
        for (int i = 0; i < nconsumers; ++i) {
            pool.execute(new Consumer(q, barrier, iters));
        }
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * nconsumers)) + " ns per transfer");
    }

    static final class LTQasSQ<T> extends LinkedTransferQueue<T> {
        LTQasSQ() { super(); }
        public void put(T x) {
            try { super.transfer(x);
            } catch (InterruptedException ex) { throw new Error(); }
        }
    }

    static final class HalfSyncLTQ<T> extends LinkedTransferQueue<T> {
        int calls;
        HalfSyncLTQ() { super(); }
        public void put(T x) {
            if ((++calls & 1) == 0)
                super.put(x);
            else {
                try { super.transfer(x);
                } catch (InterruptedException ex) {
                    throw new Error();
                }
            }
        }
    }

}
