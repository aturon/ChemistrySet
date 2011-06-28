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

public class PhaserLoops {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final int FIRST_SIZE =   10000;
    static final int LAST_SIZE  = 1000000;
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

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

        System.out.printf("max %d Threads\n", nthreads);

        for (int k = 2; k <= nthreads; k *= 2) {
            for (int size = FIRST_SIZE; size <= LAST_SIZE; size *= 10) {
                long startTime = System.nanoTime();

                Phaser phaser = new Phaser();
                PhaserAction[] actions = new PhaserAction[nthreads];
                for (int i = 0; i < k; ++i) {
                    actions[i] = new PhaserAction(i, phaser, size);
                }

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
