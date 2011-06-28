/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;


public class TimeoutProducerConsumerLoops {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();
    static final ExecutorService pool = Executors.newCachedThreadPool();

    // Number of elements passed around -- must be power of two
    // Elements are reused from pool to minimize alloc impact
    static final int POOL_SIZE = 1 << 8;
    static final int POOL_MASK = POOL_SIZE-1;
    static final Integer[] intPool = new Integer[POOL_SIZE];
    static {
        for (int i = 0; i < POOL_SIZE; ++i)
            intPool[i] = Integer.valueOf(i);
    }

    // max lag between a producer and consumer to avoid
    // this becoming a GC test rather than queue test.
    // Used only per-pair to lessen impact on queue sync
    static final int LAG_MASK = (1 << 12) - 1;

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
        if (producerSum != consumerSum) {
            throw new Error("CheckSum mismatch");
        }
    }

    public static void main(String[] args) throws Exception {
        int maxPairs = NCPUS * 3 / 2;
        int iters = 1000000;

        if (args.length > 0)
            maxPairs = Integer.parseInt(args[0]);

        print = true;
        int k = 1;
        for (int i = 1; i <= maxPairs;) {
            System.out.println("Pairs:" + i);
            oneTest(i, iters);
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

    static void oneTest(int n, int iters) throws Exception {
        if (print)
            System.out.print("LinkedTransferQueue      ");
        oneRun(new LinkedTransferQueue<Integer>(), n, iters);

        if (print)
            System.out.print("LinkedTransferQueue(xfer)");
        oneRun(new LTQasSQ<Integer>(), n, iters);

        if (print)
            System.out.print("LinkedBlockingQueue      ");
        oneRun(new LinkedBlockingQueue<Integer>(), n, iters);

        if (print)
            System.out.print("LinkedBlockingQueue(cap) ");
        oneRun(new LinkedBlockingQueue<Integer>(POOL_SIZE), n, iters);

        if (print)
            System.out.print("ArrayBlockingQueue(cap)  ");
        oneRun(new ArrayBlockingQueue<Integer>(POOL_SIZE), n, iters);

        if (print)
            System.out.print("LinkedBlockingDeque      ");
        oneRun(new LinkedBlockingDeque<Integer>(), n, iters);

        if (print)
            System.out.print("SynchronousQueue         ");
        oneRun(new SynchronousQueue<Integer>(), n, iters);

        if (print)
            System.out.print("SynchronousQueue(fair)   ");
        oneRun(new SynchronousQueue<Integer>(true), n, iters);

        if (print)
            System.out.print("PriorityBlockingQueue    ");
        oneRun(new PriorityBlockingQueue<Integer>(), n, iters / 16);

        if (print)
            System.out.print("ArrayBlockingQueue(fair) ");
        oneRun(new ArrayBlockingQueue<Integer>(POOL_SIZE, true), n, iters/16);

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
        Producer(BlockingQueue<Integer> q, CyclicBarrier b, Phaser s, int iters) {
            super(q, b, s, iters);
        }

        public void run() {
            try {
                barrier.await();
                int s = 0;
                int l = hashCode();
                int i = 0;
                long timeout = 1000;
                while (i < iters) {
                    l = LoopHelpers.compute4(l);
                    Integer v = intPool[l & POOL_MASK];
                    if (queue.offer(v, timeout, TimeUnit.NANOSECONDS)) {
                        s += LoopHelpers.compute4(v.intValue());
                        ++i;
                        if (timeout > 1)
                            timeout--;
                        if ((i & LAG_MASK) == LAG_MASK)
                            lagPhaser.arriveAndAwaitAdvance();
                    }
                    else
                        timeout++;
                }
                addProducerSum(s);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }
    }

    static class Consumer extends Stage {
        Consumer(BlockingQueue<Integer> q, CyclicBarrier b, Phaser s, int iters) {
            super(q, b, s, iters);
        }

        public void run() {
            try {
                barrier.await();
                int l = 0;
                int s = 0;
                int i = 0;
                long timeout = 1000;
                while (i < iters) {
                    Integer e = queue.poll(timeout,
                                           TimeUnit.NANOSECONDS);
                    if (e != null) {
                        l = LoopHelpers.compute4(e.intValue());
                        s += l;
                        ++i;
                        if (timeout > 1)
                            --timeout;
                        if ((i & LAG_MASK) == LAG_MASK)
                            lagPhaser.arriveAndAwaitAdvance();
                    }
                    else
                        ++timeout;
                }
                addConsumerSum(s);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int npairs, int iters) throws Exception {
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(npairs * 2 + 1, timer);
        for (int i = 0; i < npairs; ++i) {
            Phaser s = new Phaser(2);
            pool.execute(new Producer(q, barrier, s, iters));
            pool.execute(new Consumer(q, barrier, s, iters));
        }
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        checkSum();
        q.clear();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * npairs)) + " ns per transfer");
    }

    static final class LTQasSQ<T> extends LinkedTransferQueue<T> {
        LTQasSQ() { super(); }
        public void put(T x) {
            try { super.transfer(x);
            } catch (InterruptedException ex) { throw new Error(); }
        }

        public boolean offer(T x, long timeout, TimeUnit unit) {
            return super.offer(x, timeout, unit);
        }

    }

}
