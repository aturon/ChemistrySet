/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;

class LongSetOpsDemo {

    static final Random rng = new Random();
    static final long NPS = (1000L * 1000 * 1000);
    static ForkJoinPool fjpool = new ForkJoinPool();
    static int reps = 16;
    static final long maxValue = 1 << 12;

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        long[] a = new long[n];
        ParallelLongArray pa = ParallelLongArray.createUsingHandoff(a, fjpool);
        System.out.printf("Using %d Longs, %d replications\n", n, reps);
        seqSelectTest(pa);
        selectTest(pa);
        seqRemoveTest(pa);
        removeTest(pa);
        seqUniqTest(pa);
        parUniqTest(pa);
        sortUniqTest(pa);
        seqFindTest(pa);
        parFindTest(pa);
        fjpool.shutdown();
    }

    static int nresets = 0;
    static void reset(ParallelLongArray pa) {
        pa.replaceWithGeneratedValue(CommonOps.longRandom(maxValue));
        if (nresets++ == 0) System.out.println(pa.summary());
    }

    static void resetToEvens(ParallelLongArray pa) {
        pa.replaceWithMappedIndex(evens);
    }

    static class Evens implements Ops.IntToLong {
        public long op(int i) {
            return ((long)(i << 1));
        }
    }

    static class IsOdd implements Ops.LongPredicate {
        public boolean op(long x) {
            return (x & 1) != 0;
        }
    }

    static class IsEven implements Ops.LongPredicate {
        public boolean op(long x) {
            return (x & 1) == 0;
        }
    }


    static final IsOdd isOdd = new IsOdd();
    static final IsEven isEven = new IsEven();
    static final Evens evens = new Evens();

    static void parUniqTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            ParallelLongArray u = pa.allUniqueElements();
            elapsed += System.nanoTime() - last;
            u.sort();
            checkSorted(u);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Uniq time             %7.3f\n", de);
    }

    static void seqUniqTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            long[] u = seqUnique(pa.getArray());
            elapsed += System.nanoTime() - last;
            ParallelLongArray pu = ParallelLongArray.createUsingHandoff(u, fjpool);
            pu.sort();
            checkSorted(pu);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq Uniq time:        %7.3f\n", de);
    }

    static void sortUniqTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            ParallelLongArray u = pa.all();
            u.sort();
            u.removeConsecutiveDuplicates();
            elapsed += System.nanoTime() - last;
            checkSorted(u);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Par Uniq Sort time :  %7.3f\n", de);
    }

    static void removeTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            ParallelLongArray u = pa.all();
            last = System.nanoTime();
            u.removeAll(isOdd);
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != evenSize)
                throw new Error(usize + " should be " + evenSize);
            int ai = u.withFilter(isOdd).anyIndex();
            if (ai >= 0)
                throw new Error("found " + ai);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("RemoveAll time :      %7.3f\n", de);
    }

    static void seqRemoveTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            ParallelLongArray u = pa.all();
            last = System.nanoTime();
            seqRemoveAll(u, isOdd);
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != evenSize)
                throw new Error(usize + " should be " + evenSize);
            int ai = u.withFilter(isOdd).anyIndex();
            if (ai >= 0)
                throw new Error("found " + ai);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq RemoveAll time :  %7.3f\n", de);
    }

    static void selectTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            last = System.nanoTime();
            ParallelLongArray u = pa.withFilter(isOdd).all();
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != oddSize)
                throw new Error(usize + " should be " + evenSize);
            int ai = u.withFilter(isEven).anyIndex();
            if (ai >= 0)
                throw new Error("found " + ai);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("SelectAll time :      %7.3f\n", de);
    }

    static void seqSelectTest(ParallelLongArray pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            last = System.nanoTime();
            ArrayList<Long> u = seqSelectAll(pa, isOdd);
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != oddSize)
                throw new Error(usize + " should be " + evenSize);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq SelectAll time :  %7.3f\n", de);
    }


    static void parFindTest(ParallelLongArray pa) {
        Random rng = new Random();
        int n = pa.size();
        long last;
        long elapsed = 0;
        resetToEvens(pa);
        last = System.nanoTime();
        for (int i = 0; i < reps * 16; ++i) {
            int rnd = rng.nextInt(n * 2);
            boolean expect = (rnd & 1) == 0;
            long t = (long)(rnd);
            boolean contains = pa.indexOf(t) >= 0;
            if (expect != contains)
                throw new Error();
        }
        elapsed += System.nanoTime() - last;
        double de = (double)(elapsed) / NPS;
        System.out.printf("Par index time :      %7.3f\n", de);
    }

    static void seqFindTest(ParallelLongArray pa) {
        List<Long> pal = pa.asList();
        Random rng = new Random();
        int n = pa.size();
        long last;
        long elapsed = 0;
        resetToEvens(pa);
        last = System.nanoTime();
        for (int i = 0; i < reps * 16; ++i) {
            int rnd = rng.nextInt(n * 2);
            boolean expect = (rnd & 1) == 0;
            long t = (long)(rnd);
            boolean contains = pal.indexOf(t) >= 0;
            if (expect != contains)
                throw new Error();
        }
        elapsed += System.nanoTime() - last;
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq index time :      %7.3f\n", de);
    }

    static void seqRemoveAll(ParallelLongArray pa,
                             Ops.LongPredicate selector) {
        long[] a = pa.getArray();
        int n = pa.size();
        int k = 0;
        for (int i = 0; i < n; ++i) {
            long x = a[i];
            if (!selector.op(x))
                a[k++] = x;
        }
        pa.setLimit(k);
    }

    static ArrayList<Long> seqSelectAll(ParallelLongArray pa,
                                        Ops.LongPredicate selector) {
        ArrayList<Long> al = new ArrayList<Long>();
        long[] a = pa.getArray();
        int n = pa.size();
        for (int i = 0; i < n; ++i) {
            long x = a[i];
            if (selector.op(x))
                al.add(Long.valueOf(x));
        }
        return al;
    }

    static long[] seqUnique(long[] a) {
        int n = a.length;
        HashSet<Long> m = new HashSet<Long>(n);
        for (int i = 0; i < n; ++i)
            m.add(Long.valueOf(a[i]));
        int ul = m.size();
        long[] u = new long[ul];
        int k = 0;
        for (Long e : m)
            u[k++] = e;
        return u;
    }

    static void checkSorted(ParallelLongArray pa) {
        int n = pa.size();
        for (int i = 0; i < n - 1; i++) {
            if (pa.get(i) >= pa.get(i+1)) {
                throw new Error("Unsorted at " + i + ": " + pa.get(i) + " / " + pa.get(i+1));
            }
        }
    }


}
