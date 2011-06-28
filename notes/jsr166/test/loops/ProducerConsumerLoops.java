/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;
//import jsr166y.*;

public class ProducerConsumerLoops {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();
    static final Random rng = new Random();
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;
    static int producerSum;
    static int consumerSum;
    static synchronized void addProducerSum(int x) {
        producerSum += x;
    }

    static synchronized void addConsumerSum(int x) {
        consumerSum += x;
    }

    static synchronized void checkSum() {
        if (producerSum != consumerSum)
            throw new Error("CheckSum mismatch");
    }

    // Number of elements passed around -- must be power of two
    // Elements are reused from pool to minimize alloc impact
    static final int POOL_SIZE = 1 << 7;
    static final int POOL_MASK = POOL_SIZE-1;
    static final Integer[] intPool = new Integer[POOL_SIZE];
    static {
        for (int i = 0; i < POOL_SIZE; ++i)
            intPool[i] = Integer.valueOf(i);
    }

    // Number of puts by producers or takes by consumers
    static final int ITERS = 1 << 20;

    // max lag between a producer and consumer to avoid
    // this becoming a GC test rather than queue test.
    // Used only per-pair to lessen impact on queue sync
    static final int LAG_MASK = (1 << 12) - 1;

    public static void main(String[] args) throws Exception {
        int maxPairs = NCPUS * 3 / 2;

        if (args.length > 0)
            maxPairs = Integer.parseInt(args[0]);

        warmup();
        print = true;
        int k = 1;
        for (int i = 1; i <= maxPairs;) {
            System.out.println("Pairs:" + i);
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
        final Phaser lagPhaser;
        Stage(BlockingQueue<Integer> q, CyclicBarrier b, Phaser s, int iters) {
            queue = q;
            barrier = b;
            lagPhaser = s;
            this.iters = iters;
        }
    }

    static class Producer extends Stage {
        Producer(BlockingQueue<Integer> q, CyclicBarrier b, Phaser s,
                 int iters) {
            super(q, b, s, iters);
        }

        public void run() {
            try {
                barrier.await();
                int ps = 0;
                int r = hashCode();
                for (int i = 0; i < iters; ++i) {
                    r = LoopHelpers.compute7(r);
                    Integer v = intPool[r & POOL_MASK];
                    int k = v.intValue();
                    queue.put(v);
                    ps += k;
                    if ((i & LAG_MASK) == LAG_MASK)
                        lagPhaser.arriveAndAwaitAdvance();
                }
                addProducerSum(ps);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }
    }

    static class Consumer extends Stage {
        Consumer(BlockingQueue<Integer> q, CyclicBarrier b, Phaser s,
                 int iters) {
            super(q, b, s, iters);
        }

        public void run() {
            try {
                barrier.await();
                int cs = 0;
                for (int i = 0; i < iters; ++i) {
                    Integer v = queue.take();
                    int k = v.intValue();
                    cs += k;
                    if ((i & LAG_MASK) == LAG_MASK)
                        lagPhaser.arriveAndAwaitAdvance();
                }
                addConsumerSum(cs);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int n, int iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(n * 2 + 1, timer);
        for (int i = 0; i < n; ++i) {
            Phaser s = new Phaser(2);
            pool.execute(new Producer(q, barrier, s, iters));
            pool.execute(new Consumer(q, barrier, s, iters));
        }
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        checkSum();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * n)) + " ns per transfer");
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
