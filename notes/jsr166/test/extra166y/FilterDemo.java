/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;
import java.util.concurrent.*;


public class FilterDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Sequential version, for performance comparison
     */
    static<T> List<T> seqFilter(T[] list,
                                Ops.Predicate<T> pred) {
        ArrayList<T> result = new ArrayList<T>();
        int n = list.length;
        for (int i = 0; i < n; ++i) {
            T x = list[i];
            if (pred.op(x))
                result.add(x);
        }
        return result;
    }

    /**
     * Slow/dumb prime check
     */
    static final class IsPrime implements Ops.Predicate<Rand> {
        public boolean op(Rand r) {
            long n = r.seed;
            if ((n & 1) == 0)
                return false;
            int bound = (int)(Math.sqrt(n));
            for (int i = 3; i <= bound; i += 2)
                if (n % i == 0)
                    return false;
            return true;
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static final class NextRand implements Ops.Procedure<Rand> {
        public void op(Rand r) {
            r.next();
        }
    }

    public static void main(String[] args) throws Exception {
        int n = 1 << 9;
        Rand[] array = new Rand[n];
        for (int i = 0; i < n; ++i)
            array[i] = new Rand(i);
        final IsPrime pred = new IsPrime();
        final NextRand nextRand = new NextRand();
        long last, now;
        double elapsed;
        last = System.nanoTime();
        List<Rand> seqResult = null;
        int resultLength = -1;
        boolean checked = false;
        for (int k = 0; k < 2; ++k) {
            List<Rand> result = seqFilter(array, pred);
            now = System.nanoTime();
            if (resultLength < 0) {
                resultLength = result.size();
                seqResult = result;
            }
            else if (resultLength != result.size())
                throw new Error("wrong result size");
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("seq:    %7.3f\n", elapsed);
            //            for (Rand r : array) r.next();
        }
        int pass = 0;
        int ps = 2;
        ForkJoinPool fjp = new ForkJoinPool();
        ParallelArray<Rand> pa = ParallelArray.createUsingHandoff(array, fjp);
        for (;;) {
            last = System.nanoTime();
            for (int k = 0; k < 4; ++k) {
                List<Rand> result = pa.withFilter(pred).all().asList();
                now = System.nanoTime();
                if (!checked) {
                    checked = true;
                    if (!result.equals(seqResult))
                        throw new Error("res" + result + " seq" + seqResult);
                }
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("ps %2d:  %7.3f\n", ps, elapsed);
            }
            if (pass == 0) {
                if (ps >= NCPU)
                    pass = 1;
                else
                    ps <<= 1;
            }
            else {
                if (ps == 1)
                    break;
                else
                    ps >>>= 1;
            }
            //            pa.apply(nextRand);
            //            fjp.setParallelism(ps);
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
            next();
            next();
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
