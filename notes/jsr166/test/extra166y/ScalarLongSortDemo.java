/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;

class ScalarLongSortDemo {
    static final long NPS = (1000L * 1000 * 1000);

    public static void main (String[] args) throws Exception {
        int n = 1 << 22;
        int sreps = 4;
        int reps = 10;
        System.out.printf("Sorting %d longs, %d replications\n", n, sreps);
        long[] a = new long[n];
        ForkJoinPool fjpool = new ForkJoinPool();
        ParallelLongArray pa = ParallelLongArray.createUsingHandoff(a, fjpool);
        long max = 1234567890123L;

        for (int i = 0; i < sreps; ++i) {
            pa.replaceWithGeneratedValue(CommonOps.longRandom(max++));
            long last = System.nanoTime();
            java.util.Arrays.sort(a);
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("java.util.Arrays.sort time:  %7.3f\n", elapsed);
            if (i == 0)
                checkSorted(a);
        }

        System.out.printf("Sorting %d longs, %d replications\n", n, reps);
        for (int i = 0; i < reps; ++i) {
            pa.replaceWithGeneratedValue(CommonOps.longRandom(max++));
            long last = System.nanoTime();
            pa.sort();
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("ParallelLongArray.sort time: %7.3f\n", elapsed);
            if (i == 0)
                checkSorted(a);
        }

        fjpool.shutdown();
    }

    static void checkSorted(long[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            if (a[i] > a[i+1]) {
                throw new Error("Unsorted at " + i + ": " + a[i] + " / " + a[i+1]);
            }
        }
    }


}
