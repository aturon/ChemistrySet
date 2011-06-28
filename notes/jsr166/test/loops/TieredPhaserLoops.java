/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;
//import jsr166y.*;

/*
 * Based loosely on Java Grande Forum barrierBench
 */

public class TieredPhaserLoops {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final int FIRST_SIZE = 10000;
    static final int LAST_SIZE = 1000000;
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static int tasksPerPhaser = Math.max(NCPUS / 8, 4);

    static void build(Runnable[] actions, int sz, int lo, int hi, Phaser b) {
        if (hi - lo > tasksPerPhaser) {
            for (int i = lo; i < hi; i += tasksPerPhaser) {
                int j = Math.min(i + tasksPerPhaser, hi);
                build(actions, sz, i, j, new Phaser(b));
            }
        } else {
            for (int i = lo; i < hi; ++i)
                actions[i] = new PhaserAction(i, b, sz);
        }
    }


    static final class PhaserAction implements Runnable {
        final int id;
        final int size;
        final Phaser phaser;
        public PhaserAction(int id, Phaser b, int size) {
            this.id = id;
            this.phaser = b;
            this.size = size;
            phaser.register();
        }


        public void run() {
            int n = size;
            Phaser b = phaser;
            for (int i = 0; i < n; ++i)
                b.arriveAndAwaitAdvance();
        }
    }

    public static void main(String[] args) throws Exception {
        int nthreads = NCPUS;
        if (args.length > 0)
            nthreads = Integer.parseInt(args[0]);
        if (args.length > 1)
            tasksPerPhaser = Integer.parseInt(args[1]);

        System.out.printf("Max %d Threads, %d tasks per phaser\n", nthreads, tasksPerPhaser);

        for (int k = 2; k <= nthreads; k *= 2) {
            for (int size = FIRST_SIZE; size <= LAST_SIZE; size *= 10) {
                long startTime = System.nanoTime();

                Runnable[] actions = new Runnable [k];
                build(actions, size, 0, k, new Phaser());
                Future[] futures = new Future[k];
                for (int i = 0; i < k; ++i) {
                    futures[i] = pool.submit(actions[i]);
                }
                for (int i = 0; i < k; ++i) {
                    futures[i].get();
                }
                long elapsed = System.nanoTime() - startTime;
                long bs = (NPS * size) / elapsed;
                System.out.printf("%4d Threads %8d iters: %11d barriers/sec\n",
                                  k, size, bs);
            }
        }
        pool.shutdown();
    }

}
