/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;

class SortDemo {

    static final Random rng = new Random();
    static final long NPS = (1000L * 1000 * 1000);

    public static void main (String[] args) throws Exception {
        int n = 1 << 22;
        int sreps = 4;
        int reps = 20;
        Long[] a = new Long[n];
        ForkJoinPool fjpool = new ForkJoinPool();
        ParallelArray<Long> pa = ParallelArray.createUsingHandoff(a, fjpool);

        System.out.printf("Parallel Sort %d Longs, %d replications\n", n, reps);
        for (int i = 0; i < reps; ++i) {
            pa.replaceWithGeneratedValue(rlg);
            parSort(pa);
            if (i == 0)
                checkSorted(a);
        }
        System.out.printf("Sequential Sort %d Longs, %d replications\n", n, sreps);
        for (int i = 0; i < sreps; ++i) {
            pa.replaceWithGeneratedValue(rlg);
            seqSort(a);
            if (i == 0)
                checkSorted(a);
        }

        System.out.println(fjpool);
        fjpool.shutdown();
    }

    static void seqSort(Long[] a) {
        long last = System.nanoTime();
        java.util.Arrays.sort(a);
        double elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("java.util.Arrays.sort time:  %7.3f\n", elapsed);
    }

    static void parSort(ParallelArray<Long> pa) {
        long last = System.nanoTime();
        pa.sort();
        double elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("ParallelArray.sort time:     %7.3f\n", elapsed);
    }

    static void checkSorted(Long[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            if (a[i].compareTo(a[i+1]) > 0) {
                throw new Error("Unsorted at " + i + ": " + a[i] + " / " + a[i+1]);
            }
        }
    }

    static final class RandomLongGenerator implements Ops.Generator<Long> {
        public Long op() {
            return new Long(ThreadLocalRandom.current().nextLong());
        }
    }

    static final RandomLongGenerator rlg = new RandomLongGenerator();



}
