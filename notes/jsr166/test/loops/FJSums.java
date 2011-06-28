/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// parallel sums and cumulations

public class FJSums {
    static final long NPS = (1000L * 1000 * 1000);
    static int THRESHOLD;

    public static void main (String[] args) throws Exception {
        int procs = 0;
        int n = 1 << 25;
        int reps = 10;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                n = Integer.parseInt(args[1]);
            if (args.length > 2)
                reps = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java FJSums threads n reps");
            return;
        }
        ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);
        System.out.println("Number of procs=" + g.getParallelism());
        // for now hardwire Cumulate threshold to 8 * #CPUs leaf tasks
        THRESHOLD = 1 + ((n + 7) >>> 3) / g.getParallelism();

        long[] a = new long[n];
        for (int i = 0; i < n; ++i)
            a[i] = i;
        long expected = ((long)n * (long)(n - 1)) / 2;
        for (int i = 0; i < 2; ++i) {
            System.out.print("Seq: ");
            long last = System.nanoTime();
            long ss = seqSum(a, 0, n);
            double elapsed = elapsedTime(last);
            System.out.printf("sum = %24d  time:  %7.3f\n", ss, elapsed);
            if (ss != expected)
                throw new Error("expected " + expected + " != " + ss);
        }
        for (int i = 0; i < reps; ++i) {
            System.out.print("Par: ");
            long last = System.nanoTime();
            Summer s = new Summer(a, 0, a.length, null);
            g.invoke(s);
            long ss = s.result;
            double elapsed = elapsedTime(last);
            System.out.printf("sum = %24d  time:  %7.3f\n", ss, elapsed);
            if (i == 0 && ss != expected)
                throw new Error("expected " + expected + " != " + ss);
            System.out.print("Cum: ");
            last = System.nanoTime();
            g.invoke(new Cumulater(null, a, 0, n));
            long sc = a[n - 1];
            elapsed = elapsedTime(last);
            System.out.printf("sum = %24d  time:  %7.3f\n", ss, elapsed);
            if (sc != ss)
                throw new Error("expected " + ss + " != " + sc);
        }
        System.out.println(g);
        g.shutdown();
    }

    static double elapsedTime(long startTime) {
        return (double)(System.nanoTime() - startTime) / NPS;
    }

    static long seqSum(long[] array, int l, int h) {
        long sum = 0;
        for (int i = l; i < h; ++i)
            sum += array[i];
        return sum;
    }

    static long seqCumulate(long[] array, int lo, int hi, long base) {
        long sum = base;
        for (int i = lo; i < hi; ++i)
            array[i] = sum += array[i];
        return sum;
    }

    /**
     * Adapted from Applyer demo in RecursiveAction docs
     */
    static final class Summer extends RecursiveAction {
        final long[] array;
        final int lo, hi;
        long result;
        Summer next; // keeps track of right-hand-side tasks
        Summer(long[] array, int lo, int hi, Summer next) {
            this.array = array; this.lo = lo; this.hi = hi;
            this.next = next;
        }

        protected void compute() {
            int l = lo;
            int h = hi;
            Summer right = null;
            while (h - l > 1 && getSurplusQueuedTaskCount() <= 3) {
                int mid = (l + h) >>> 1;
                right = new Summer(array, mid, h, right);
                right.fork();
                h = mid;
            }
            long sum = seqSum(array, l, h);
            while (right != null) {
                if (right.tryUnfork()) // directly calculate if not stolen
                    sum += seqSum(array, right.lo, right.hi);
                else {
                    right.join();
                    sum += right.result;
                }
                right = right.next;
            }
            result = sum;
        }
    }

    /**
     * Cumulative scan, adapted from ParallelArray code
     *
     * A basic version of scan is straightforward.
     *  Keep dividing by two to threshold segment size, and then:
     *   Pass 1: Create tree of partial sums for each segment
     *   Pass 2: For each segment, cumulate with offset of left sibling
     * See G. Blelloch's http://www.cs.cmu.edu/~scandal/alg/scan.html
     *
     * This version improves performance within FJ framework mainly by
     * allowing second pass of ready left-hand sides to proceed even
     * if some right-hand side first passes are still executing.  It
     * also combines first and second pass for leftmost segment, and
     * for cumulate (not precumulate) also skips first pass for
     * rightmost segment (whose result is not needed for second pass).
     *
     * To manage this, it relies on "phase" phase/state control field
     * maintaining bits CUMULATE, SUMMED, and FINISHED. CUMULATE is
     * main phase bit. When false, segments compute only their sum.
     * When true, they cumulate array elements. CUMULATE is set at
     * root at beginning of second pass and then propagated down. But
     * it may also be set earlier for subtrees with lo==0 (the
     * left spine of tree). SUMMED is a one bit join count. For leafs,
     * set when summed. For internal nodes, becomes true when one
     * child is summed.  When second child finishes summing, it then
     * moves up tree to trigger cumulate phase. FINISHED is also a one
     * bit join count. For leafs, it is set when cumulated. For
     * internal nodes, it becomes true when one child is cumulated.
     * When second child finishes cumulating, it then moves up tree,
     * executing complete() at the root.
     *
     */
    static final class Cumulater extends ForkJoinTask<Void> {
        static final short CUMULATE = (short)1;
        static final short SUMMED   = (short)2;
        static final short FINISHED = (short)4;

        final Cumulater parent;
        final long[] array;
        Cumulater left, right;
        final int lo;
        final int hi;
        volatile int phase;  // phase/state
        long in, out; // initially zero

        static final AtomicIntegerFieldUpdater<Cumulater> phaseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Cumulater.class, "phase");

        Cumulater(Cumulater parent, long[] array, int lo, int hi) {
            this.parent = parent;
            this.array = array;
            this.lo = lo;
            this.hi = hi;
        }

        public final Void getRawResult() { return null; }
        protected final void setRawResult(Void mustBeNull) { }

        /** Returns true if can CAS CUMULATE bit true */
        final boolean transitionToCumulate() {
            int c;
            while (((c = phase) & CUMULATE) == 0)
                if (phaseUpdater.compareAndSet(this, c, c | CUMULATE))
                    return true;
            return false;
        }

        public final boolean exec() {
            if (hi - lo > THRESHOLD) {
                if (left == null) { // first pass
                    int mid = (lo + hi) >>> 1;
                    left =  new Cumulater(this, array, lo, mid);
                    right = new Cumulater(this, array, mid, hi);
                }

                boolean cumulate = (phase & CUMULATE) != 0;
                if (cumulate) {
                    long pin = in;
                    left.in = pin;
                    right.in = pin + left.out;
                }

                if (!cumulate || right.transitionToCumulate())
                    right.fork();
                if (!cumulate || left.transitionToCumulate())
                    left.exec();
            }
            else {
                int cb;
                for (;;) { // Establish action: sum, cumulate, or both
                    int b = phase;
                    if ((b & FINISHED) != 0) // already done
                        return false;
                    if ((b & CUMULATE) != 0)
                        cb = FINISHED;
                    else if (lo == 0) // combine leftmost
                        cb = (SUMMED|FINISHED);
                    else
                        cb = SUMMED;
                    if (phaseUpdater.compareAndSet(this, b, b|cb))
                        break;
                }

                if (cb == SUMMED)
                    out = seqSum(array, lo, hi);
                else if (cb == FINISHED)
                    seqCumulate(array, lo, hi, in);
                else if (cb == (SUMMED|FINISHED))
                    out = seqCumulate(array, lo, hi, 0L);

                // propagate up
                Cumulater ch = this;
                Cumulater par = parent;
                for (;;) {
                    if (par == null) {
                        if ((cb & FINISHED) != 0)
                            ch.complete(null);
                        break;
                    }
                    int pb = par.phase;
                    if ((pb & cb & FINISHED) != 0) { // both finished
                        ch = par;
                        par = par.parent;
                    }
                    else if ((pb & cb & SUMMED) != 0) { // both summed
                        par.out = par.left.out + par.right.out;
                        int refork =
                            ((pb & CUMULATE) == 0 &&
                             par.lo == 0) ? CUMULATE : 0;
                        int nextPhase = pb|cb|refork;
                        if (pb == nextPhase ||
                            phaseUpdater.compareAndSet(par, pb, nextPhase)) {
                            if (refork != 0)
                                par.fork();
                            cb = SUMMED; // drop finished bit
                            ch = par;
                            par = par.parent;
                        }
                    }
                    else if (phaseUpdater.compareAndSet(par, pb, pb|cb))
                        break;
                }
            }
            return false;
        }

    }

}


