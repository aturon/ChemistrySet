/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;

public final class DynamicAsyncFib  extends BinaryAsyncAction {
    int number;

    public DynamicAsyncFib(int n) {
        this.number = n;
    }

    public final boolean exec() {
        DynamicAsyncFib f = this;
        int n = f.number;
        while (n > 1 && getSurplusQueuedTaskCount() <= 3) {
            DynamicAsyncFib l = new DynamicAsyncFib(--n);
            DynamicAsyncFib r = new DynamicAsyncFib(n - 1);
            f.linkSubtasks(l, r);
            r.fork();
            f = l;
        }
        f.number = seqFib(n);
        f.complete();
        return false;
    }

    protected void onComplete(BinaryAsyncAction x, BinaryAsyncAction y) {
        number = ((DynamicAsyncFib)x).number + ((DynamicAsyncFib)y).number;
    }

    static long lastStealCount;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        int num = 45;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                num = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java DynamicAsyncFib <threads> <number>]");
            return;
        }

        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 20; ++i) {
                test(g, num);
                //          Thread.sleep(1000);
            }
            System.out.println(g);
            g.shutdown();
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static void test(ForkJoinPool g, int num) throws Exception {
        int ps = g.getParallelism();
        long start = System.nanoTime();
        DynamicAsyncFib f = new DynamicAsyncFib(num);
        g.invoke(f);
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        long number = f.number;
        System.out.print("DAFib " + num + " = " + number);
        System.out.printf("\tTime: %9.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.printf(" Workers: %8d", g.getPoolSize());
        System.out.println();
    }

    // Sequential version for arguments less than threshold
    static final int seqFib(int n) { // unroll left only
        int r = 1;
        do {
            int m = n - 2;
            r += m <= 1 ? m : seqFib(m);
        } while (--n > 1);
        return r;
    }
}


