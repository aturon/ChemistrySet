
import java.util.*;
import java.util.concurrent.*;

public final class LeftSpineFib extends RecursiveAction {

    // Performance-tuning constant:
    static int sequentialThreshold;
    static long lastStealCount;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        int num = 45;
        sequentialThreshold = 2;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                num = Integer.parseInt(args[1]);
            if (args.length > 2)
                sequentialThreshold = Integer.parseInt(args[2]);
        }
        catch (Exception e) {
            System.out.println("Usage: java LeftSpineFib <threads> <number> [<sequntialThreshold>]");
            return;
        }


        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            //            g.setMaintainsParallelism(false);
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 20; ++i) {
                test(g, num);
                Thread.sleep(50);
            }
            System.out.println(g);
            g.shutdown();
            if (!g.awaitTermination(8, TimeUnit.SECONDS)) {
                System.out.println(g);
                throw new Error();
            }
            g = null;
            //            System.gc();
            Thread.sleep(500);
        }
    }

    static void test(ForkJoinPool g, int num) throws Exception {
        int ps = g.getParallelism();
        long start = System.currentTimeMillis();
        LeftSpineFib f = new LeftSpineFib(num, null);
        g.invoke(f);
        long time = System.currentTimeMillis() - start;
        double secs = ((double)time) / 1000.0;
        long result = f.getAnswer();
        System.out.print("JLSFib " + num + " = " + result);
        System.out.printf("\tTime: %7.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        //        System.out.printf(" Workers: %8d", g.getRunningThreadCount());
        System.out.printf(" Workers: %8d", g.getPoolSize());
        System.out.println();
    }


    // Initialized with argument; replaced with result
    int number;
    LeftSpineFib next;

    LeftSpineFib(int n, LeftSpineFib nxt) { number = n; next = nxt; }

    int getAnswer() {
        return number;
    }

    public final void compute() {
        int n = number;
        if (n > 1) {
            LeftSpineFib rt = null;
            int r = 0;
            while (n > sequentialThreshold) {
                int m = n - 2;
                if (m <= 1)
                    r += m;
                else
                    (rt = new LeftSpineFib(m, rt)).fork();
                n -= 1;
            }
            r += n <= 1 ? n : seqFib(n);
            if (rt != null)
                r += collectRights(rt);
            number = r;
        }
    }

    static final int collectRights(LeftSpineFib rt) {
        int r = 0;
        while (rt != null) {
            LeftSpineFib rn = rt.next;
            rt.next = null;
            if (rt.tryUnfork()) rt.compute(); else rt.join();
            //            rt.join();
            r += rt.number;
            rt = rn;
        }
        return r;
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

