/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;
import java.util.concurrent.*;


public class FindAnyDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Sequential version, for performance comparison
     */
    static<T> int seqIndexOf(T[] array,
                             Ops.Predicate<T> pred) {
        int n = array.length;
        for (int i = 0; i < n; ++i) {
            T x = array[i];
            if (pred.op(x))
                return i;
        }
        return -1;
    }

    /**
     * Slow/dumb prime check
     */
    static class IsPrime implements Ops.Predicate<Rand> {
        public boolean op(Rand r) {
            long n = r.seed;
            int bound = (int)(Math.sqrt(n));
            if (bound >= 3) {
                for (int i = 3; i <= bound; ++i)
                    if ((n & 1) == 0 || n % i == 0)
                        return false;
            }
            return true;
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static class NextRand implements Ops.Procedure<Rand> {
        public void op(Rand r) {
            r.next();
        }
    }

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        ArrayList<Rand> list = new ArrayList<Rand>(n);
        long rs = 256203225;
        for (int i = 0; i < n >>> 1; ++i)
            list.add(new Rand(rs+=3));
        list.add(new Rand(256203221));
        for (int i = n >>> 1; i < n >>> 1; ++i)
            list.add(new Rand(rs+=3));
        Rand[] array = list.toArray(new Rand[0]);
        final IsPrime pred = new IsPrime();
        long last, now;
        double elapsed;
        boolean present = false;
        for (int k = 0; k < 2; ++k) {
            last = System.nanoTime();
            for (int reps = 0; reps < 9; ++reps) {
                int result = seqIndexOf(array, pred);
                if (k == 0 && reps == 0)
                    present = result != -1;
                else if (present != (result != -1))
                    throw new Error("Inconsistent result");
            }
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("seq:    %7.3f\n", elapsed);
        }
        Thread.sleep(100);
        ForkJoinPool fjp = new ForkJoinPool();
        ParallelArray<Rand> pa = ParallelArray.createUsingHandoff(array, fjp);
        for (int i = 1; i <= NCPU; i <<= 1) {
            last = System.nanoTime();
            for (int k = 0; k < 2; ++k) {
                for (int reps = 0; reps < 9; ++reps) {
                    int result = pa.withFilter(pred).anyIndex();
                    if (present != (result != -1))
                        throw new Error("Inconsistent result");
                }
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("ps %2d:  %7.3f\n", i, elapsed);
            }
        }
        fjp.shutdownNow();
        fjp.awaitTermination(1, TimeUnit.SECONDS);
    }

    /**
     * Unsynchronized version of java.util.Random algorithm.
     */
    static final class Rand {
        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;
        private long seed;

        Rand(long s) {
            seed = s;
            //            next();
            //            next();
        }
        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
        }

        public String toString() {
            return String.valueOf(seed);
        }
    }

}
