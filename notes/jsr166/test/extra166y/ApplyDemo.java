/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;
import java.util.concurrent.*;

public class ApplyDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    /**
     * Sequential version, for performance comparison
     */
    static <T> void seqApply(T[] array, Ops.Procedure<T> f) {
        int n = array.length;
        for (int i = 0; i < n; ++i)
            f.op(array[i]);
    }

    /**
     * A sample procedure to apply
     */
    static final class Proc implements Ops.Procedure<Rand> {
        public void op(Rand x) {
            for (int k = 0; k < (1 << 10); ++k)
                x.next();
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        test();
        test();
    }

    public static void test() throws Exception {
        int n = 1 << 18;
        Rand[] array = new Rand[n];
        for (int i = 0; i < n; ++i)
            array[i] = new Rand(i);
        final Proc proc = new Proc();
        long last, now;
        double elapsed;
        last = System.nanoTime();
        for (int k = 0; k < 2; ++k) {
            for (int j = 0; j < (1 << 3); ++j)
                seqApply(array, proc);
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("seq:    %7.3f\n", elapsed);
        }
        for (int i = 1; i <= NCPU; i <<= 1) {
            ForkJoinPool fjp = new ForkJoinPool(i);
            ParallelArray pa = ParallelArray.createUsingHandoff(array, fjp);
            last = System.nanoTime();
            for (int k = 0; k < 2; ++k) {
                for (int j = 0; j < (1 << 3); ++j)
                    pa.apply(proc);
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("ps %2d:  %7.3f\n", fjp.getParallelism(), elapsed);
            }
            fjp.shutdownNow();
            fjp.awaitTermination(1, TimeUnit.SECONDS);
            Thread.sleep(10);
        }
        int sum = 0;
        for (int i = 0; i < array.length; ++i) sum += array[i].seed;
        if (sum == 0)
            System.out.print(" ");
    }

    /**
     * Unsynchronized version of java.util.Random algorithm.
     */
    static final class Rand {
        static final long multiplier = 0x5DEECE66DL;
        static final long addend = 0xBL;
        static final long mask = (1L << 48) - 1;
        long seed;

        Rand(long s) {
            seed = s;
        }
        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
        }
    }

}
