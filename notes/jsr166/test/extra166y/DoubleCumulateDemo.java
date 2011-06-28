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

public class DoubleCumulateDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static boolean check = false; // true;

    public static void main(String[] args) throws Exception {
        check = args.length > 0;
        int n = 1 << 19;
        int reps = 1 << 9;
        int tests = 4;
        double[] array = new double[n];
        long last, now;
        double elapsed;

        for (int j = 0; j < tests; ++j) {
            fill(array);
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                seqCumulate(array);
                if (k == 0 && check)
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
                       double[] array, int nthreads,
                       int reps, int tests) throws Exception {
        ParallelDoubleArray pa = ParallelDoubleArray.createUsingHandoff(array, fjp);
        long last, now;
        long steals = fjp.getStealCount();
        //        long syncs = fjp.getSyncCount();
        for (int j = 0; j < tests; ++j) {
            fill(array);
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                pa.cumulateSum();
                if (k == 0 && check)
                    check(array);
            }
            now = System.nanoTime();
            double elapsed = (double)(now - last) / (NPS);
            last = now;
            long sc = fjp.getStealCount();
            long scount = (sc - steals) / reps;
            steals = sc;
            //            long tc = fjp.getSyncCount();
            //            long tcount = (tc - syncs) / reps;
            //            syncs = tc;

            //            System.out.printf("ps %2d:  %9.5f Steals:%6d Syncs %6d\n", nthreads, elapsed, scount, tcount);
            System.out.printf("ps %2d:  %9.5f Steals:%6d\n", nthreads, elapsed, scount);
        }
        Thread.sleep(100);
    }

    static void check(double[] array) {
        for (int i = 0; i < array.length; ++i) {
            double sum = i + 1;
            if (array[i] != sum) {
                System.out.println("i: " + i + " sum: " + sum + " element:" + array[i]);
                throw new Error();
            }
        }
        //        System.out.print("*");
    }

    static void fill(double[] array) {
        for (int i = 0; i < array.length; ++i)
            array[i] = 1;
    }

    static double seqCumulate(double[] array) {
        double sum = 0;
        for (int i = 0; i < array.length; ++i)
            sum = array[i] += sum;
        return sum;
    }
}
