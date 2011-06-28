/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;

class ParallelArraySortDemo {

    static final Random rng = new Random();
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        int reps = 9;
        System.out.printf("Sorting %d Longs, %d replications\n", n, reps);
        Long[] a = new Long[n];
        randomFill(a);

        for (int i = 0; i < reps; ++i) {
            long last = System.nanoTime();
            java.util.Arrays.sort(a);
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("java.util.Arrays.sort time:  %7.3f\n", elapsed);
            checkSorted(a);
            shuffle(a);
            //            System.gc();
        }

        ForkJoinPool fjpool = new ForkJoinPool();
        ParallelArray<Long> pa = ParallelArray.createUsingHandoff(a, fjpool);
        for (int i = 0; i < reps; ++i) {
            long last = System.nanoTime();
            pa.sort();
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("ArrayTasks.sort time:        %7.3f\n", elapsed);
            checkSorted(a);
            shuffle(a);
            //            System.gc();
        }
        fjpool.shutdown();
    }

    static void checkSorted(Long[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            if (a[i].compareTo(a[i+1]) > 0) {
                throw new Error("Unsorted at " + i + ": " + a[i] + " / " + a[i+1]);
            }
        }
    }

    static void randomFill(Long[] a) {
        for (int i = 0; i < a.length; ++i)
            a[i] = new Long(rng.nextLong());
    }

    static void shuffle(Long[] a) {
        int n = a.length;
        for (int i = n; i > 1; --i) {
            int r = rng.nextInt(i);
            Long t = a[i-1];
            a[i-1] = a[r];
            a[r] = t;
        }
    }


}
