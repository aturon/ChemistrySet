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

public class LongMapReduceDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    static final Random rng = new Random();

    /**
     * Sequential version, for performance comparison
     */
    static long seqMapReduce(long[] array,
                            LongOp mapper,
                            LongReducer reducer,
                            long base) {
        long n = array.length;
        long x = base;
        for (int i = 0; i < n; ++i)
            x = reducer.op(x, mapper.op(array[i]));
        return x;
    }

    // sample functions
    static final class GetNext implements LongOp {
        public long op(long seed) {
            long x = seed;
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= (x << 17);
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= (x << 17);
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= (x << 17);
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= (x << 17);
            return x;
        }
    }

    static final class Accum implements LongReducer {
        public long op(long x, long y) {
            return x + y;
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int n = 1 << 18;
        int reps = 1 << 10;
        long[] array = new long[n];
        for (int i = 0; i < n; ++i)
            array[i] = i + 1L;
        ForkJoinPool fjp = new ForkJoinPool();
        ParallelLongArray pa = ParallelLongArray.createUsingHandoff(array, fjp);
        final GetNext getNext = new GetNext();
        final Accum accum = new Accum();
        final long zero = 0L;
        long last, now;
        double elapsed;
        for (int j = 0; j < 2; ++j) {
            long rseed = rng.nextLong();
            resetSeeds(array, rseed);
            long seqsum = 0;
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                seqsum += seqMapReduce(array, getNext, accum, zero);
                long tmp = array[k];
                array[k] = array[n - k - 1];
                array[n - k - 1] = tmp;
            }
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("sequential:    %7.3f\n", elapsed);
            for (int i = NCPU; i >= 1; i >>>= 1) {
                resetSeeds(array, rseed);
                long sum = 0;
                last = System.nanoTime();
                for (int k = 0; k < reps; ++k) {
                    sum += pa.withMapping(getNext).reduce(accum, zero);
                    long tmp = array[k];
                    array[k] = array[n - k - 1];
                    array[n - k - 1] = tmp;
                }
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("poolSize %3d:  %7.3f\n", fjp.getParallelism(), elapsed);
                if (sum != seqsum) throw new Error("checksum");
            }
            for (int i = 2; i <= NCPU; i <<= 1) {
                resetSeeds(array, rseed);
                long sum = 0;
                //                fjp.setParallelism(i);
                last = System.nanoTime();
                for (int k = 0; k < reps; ++k) {
                    sum += pa.withMapping(getNext).reduce(accum, zero);
                    long tmp = array[k];
                    array[k] = array[n - k - 1];
                    array[n - k - 1] = tmp;
                }
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("poolSize %3d:  %7.3f\n", fjp.getParallelism(), elapsed);
                if (sum != seqsum) throw new Error("checksum");
            }
        }
        fjp.shutdownNow();
        fjp.awaitTermination(1, TimeUnit.SECONDS);
        Thread.sleep(100);
    }

    static void resetSeeds(long[] array, long s) {
        for (int i = 0; i < array.length; ++i)
            array[i] = s++;
    }

}
