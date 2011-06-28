/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */


import jsr166y.*;
import extra166y.*;
import static extra166y.Ops.*;
import java.util.*;
import java.util.concurrent.*;

public class LongCumulateDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static final boolean reportSteals = false;

    public static void main(String[] args) throws Exception {
        int n = 1 << 19;
        int reps = 1 << 8;
        int tests = 2;
        long[] array = new long[n];
        long last, now;
        double elapsed;

        for (int j = 0; j < tests; ++j) {
            seqFill(array);
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                seqCumulate(array);
                if (j == 0 && k == 0)
                    check(array);
            }
            now = System.nanoTime();
            elapsed = (double)(now - last) / (NPS);
            System.out.printf("seq  :  %9.5f\n", elapsed);
        }

        for (int sweeps = 0; sweeps < 2; ++sweeps) {
            for (int i = 2; i <= NCPU; i <<= 1) {
                ForkJoinPool fjp = new ForkJoinPool(i);
                oneRun(fjp, array, i, reps, tests);
                fjp.shutdown();
            }
            for (int i = NCPU; i >= 1; i >>>= 1) {
                ForkJoinPool fjp = new ForkJoinPool(i);
                oneRun(fjp, array, i, reps, tests);
                fjp.shutdown();
            }
        }
    }

    static void oneRun(ForkJoinPool fjp,
                       long[] array, int nthreads,
                       int reps, int tests) throws Exception {
        ParallelLongArray pa = ParallelLongArray.createUsingHandoff(array, fjp);
        long last, now;
        long steals = fjp.getStealCount();
        //        long syncs = fjp.getSyncCount();
        for (int j = 0; j < tests; ++j) {
            pa.replaceWithValue(1);
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                pa.cumulateSum();
                if (j == 0 && k == 0)
                    check(array);
            }
            now = System.nanoTime();
            double elapsed = (double)(now - last) / (NPS);
            last = now;
            System.out.printf("ps %2d:  %9.5f", nthreads, elapsed);
            if (reportSteals) {
                long sc = fjp.getStealCount();
                long scount = (sc - steals) / reps;
                steals = sc;
                System.out.printf(" Steals:%6d", scount);
            }
            System.out.println();
        }
        Thread.sleep(100);
    }

    static void check(long[] array) {
        for (int i = 0; i < array.length; ++i) {
            long sum = i + 1;
            if (array[i] != sum) {
                System.out.println("i: " + i + " sum: " + sum + " element:" + array[i]);
                throw new Error();
            }
        }
    }

    static void seqFill(long[] array) {
        for (int i = 0; i < array.length; ++i)
            array[i] = 1;
    }

    static long seqCumulate(long[] array) {
        long sum = 0;
        for (int i = 0; i < array.length; ++i)
            sum = array[i] += sum;
        return sum;
    }
}
