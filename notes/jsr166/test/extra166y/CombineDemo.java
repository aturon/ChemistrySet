/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import jsr166y.*;
import extra166y.*;
import java.util.*;

class CombineDemo {

    static final Random rng = new Random();
    static final long NPS = (1000L * 1000 * 1000);
    static ForkJoinPool fjpool = new ForkJoinPool();
    static int reps = 16;
    static final long maxValue = 1 << 12;

    public static void main(String[] args) throws Exception {
        int n = 1 << 20;
        Long[] a = new Long[n];
        ParallelArray<Long> pa = ParallelArray.createUsingHandoff(a, fjpool);
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
    static void reset(ParallelArray<Long> pa) {
        pa.replaceWithGeneratedValue(rlg);
        if (nresets++ == 0) System.out.println(pa.summary());
    }

    static void resetToEvens(ParallelArray<Long> pa) {
        pa.replaceWithMappedIndex(evens);
    }

    static class Evens implements Ops.IntToObject<Long> {
        public Long op(int i) {
            return Long.valueOf((long)(i << 1));
        }
    }

    static class IsOdd implements Ops.Predicate<Long> {
        public boolean op(Long x) {
            return (x.longValue() & 1) != 0;
        }
    }

    static class IsEven implements Ops.Predicate<Long> {
        public boolean op(Long x) {
            return (x.longValue() & 1) == 0;
        }
    }

    static final IsOdd isOdd = new IsOdd();
    static final IsEven isEven = new IsEven();
    static final Evens evens = new Evens();

    static void parUniqTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            ParallelArray<Long> u = pa.allUniqueElements();
            elapsed += System.nanoTime() - last;
            u.sort();
            checkSorted(u);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Uniq time             %7.3f\n", de);
    }

    static void seqUniqTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            Long[] u = seqUnique(pa.getArray());
            elapsed += System.nanoTime() - last;
            ParallelArray<Long> pu = ParallelArray.createUsingHandoff(u, fjpool);
            pu.sort();
            checkSorted(pu);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq Uniq time:        %7.3f\n", de);
    }

    static void sortUniqTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            last = System.nanoTime();
            ParallelArray<Long> u = pa.all();
            u.sort();
            u.removeConsecutiveDuplicates();
            elapsed += System.nanoTime() - last;
            checkSorted(u);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Par Uniq Sort time :  %7.3f\n", de);
    }

    static void removeTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            ParallelArray<Long> u = pa.all();
            last = System.nanoTime();
            u.removeAll(isOdd);
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != evenSize)
                throw new Error(usize + " should be " + evenSize);
            Long a = u.withFilter(isOdd).any();
            if (a != null)
                throw new Error("found " + a);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("RemoveAll time :      %7.3f\n", de);
    }

    static void seqRemoveTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            ParallelArray<Long> u = pa.all();
            last = System.nanoTime();
            seqRemoveAll(u, isOdd);
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != evenSize)
                throw new Error(usize + " should be " + evenSize);
            Long a = u.withFilter(isOdd).any();
            if (a != null)
                throw new Error("found " + a);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq RemoveAll time :  %7.3f\n", de);
    }

    static void selectTest(ParallelArray<Long> pa) {
        int n = pa.size();
        long last;
        long elapsed = 0;
        for (int i = 0; i < reps; ++i) {
            reset(pa);
            int psize = pa.size();
            int oddSize = pa.withFilter(isOdd).size();
            int evenSize = psize - oddSize;
            last = System.nanoTime();
            ParallelArray<Long> u = pa.withFilter(isOdd).all();
            elapsed += System.nanoTime() - last;
            int usize = u.size();
            if (usize != oddSize)
                throw new Error(usize + " should be " + evenSize);
            Long a = u.withFilter(isEven).any();
            if (a != null)
                throw new Error("found " + a);
        }
        double de = (double)(elapsed) / NPS;
        System.out.printf("SelectAll time :      %7.3f\n", de);
    }

    static void seqSelectTest(ParallelArray<Long> pa) {
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

    static void parFindTest(ParallelArray<Long> pa) {
        Random rng = new Random();
        int n = pa.size();
        long last;
        long elapsed = 0;
        resetToEvens(pa);
        last = System.nanoTime();
        for (int i = 0; i < reps * 16; ++i) {
            int rnd = rng.nextInt(n * 2);
            boolean expect = (rnd & 1) == 0;
            Long t = Long.valueOf(rnd);
            boolean contains = pa.indexOf(t) >= 0;
            if (expect != contains)
                throw new Error();
        }
        elapsed += System.nanoTime() - last;
        double de = (double)(elapsed) / NPS;
        System.out.printf("Par index time :      %7.3f\n", de);
    }

    static void seqFindTest(ParallelArray<Long> pa) {
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
            Long t = Long.valueOf(rnd);
            boolean contains = pal.indexOf(t) >= 0;
            if (expect != contains)
                throw new Error();
        }
        elapsed += System.nanoTime() - last;
        double de = (double)(elapsed) / NPS;
        System.out.printf("Seq index time :      %7.3f\n", de);
    }

    // ............

    static void seqRemoveAll(ParallelArray<Long> pa,
                             Ops.Predicate<Long> selector) {
        Long[] a = pa.getArray();
        int n = pa.size();
        int k = 0;
        for (int i = 0; i < n; ++i) {
            Long x = a[i];
            if (!selector.op(x))
                a[k++] = x;
        }
        for (int j = k; j < n; ++j)
            a[j] = null;
        pa.setLimit(k);
    }

    static ArrayList<Long> seqSelectAll(ParallelArray<Long> pa,
                                        Ops.Predicate<Long> selector) {
        ArrayList<Long> al = new ArrayList<Long>();
        Long[] a = pa.getArray();
        int n = pa.size();
        for (int i = 0; i < n; ++i) {
            Long x = a[i];
            if (selector.op(x))
                al.add(x);
        }
        return al;
    }

    static Long[] seqUnique(Long[] a) {
        int n = a.length;
        HashSet<Long> m = new HashSet<Long>(n);
        for (int i = 0; i < n; ++i)
            m.add(a[i]);
        int ul = m.size();
        Long[] u = new Long[ul];
        int k = 0;
        for (Long e : m)
            u[k++] = e;
        return u;
    }

    static void checkSorted(ParallelArray<Long> pa) {
        int n = pa.size();
        for (int i = 0; i < n - 1; i++) {
            if (pa.get(i).compareTo(pa.get(i+1)) >= 0) {
                throw new Error("Unsorted at " + i + ": " + pa.get(i) + " / " + pa.get(i+1));
            }
        }
    }

    static final class RandomLongGenerator implements Ops.Generator<Long> {
        public Long op() {
            return new Long(ThreadLocalRandom.current().nextLong(maxValue));
        }
    }

    static final RandomLongGenerator rlg = new RandomLongGenerator();
}
