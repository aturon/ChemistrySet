import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
//import java.util.concurrent.*;

public final class DynamicLeftSpineFib extends RecursiveAction {

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
            System.out.println("Usage: java DynamicLeftSpineFib <threads> <number> [<sequntialThreshold>]");
            return;
        }


        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 20; ++i) {
                test(g, num);
            }
            System.out.println(g);
            g.shutdown();
            if (!g.awaitTermination(10, TimeUnit.SECONDS))
                throw new Error();
            Thread.sleep(1000);
        }
    }

    static void test(ForkJoinPool g, int num) throws Exception {
        int ps = g.getParallelism();
        long start = System.currentTimeMillis();
        DynamicLeftSpineFib f = new DynamicLeftSpineFib(num, null);
        g.invoke(f);
        long time = System.currentTimeMillis() - start;
        double secs = ((double)time) / 1000.0;
        long result = f.getAnswer();
        System.out.print("DLSFib " + num + " = " + result);
        System.out.printf("\tTime: %7.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.printf(" Workers: %8d", g.getPoolSize());
        System.out.println();
    }


    // Initialized with argument; replaced with result
    int number;
    DynamicLeftSpineFib next;

    DynamicLeftSpineFib(int n, DynamicLeftSpineFib nxt) {
        number = n; next = nxt;
    }

    int getAnswer() {
        return number;
    }
    public void compute() {
        number = fib(number);
    }

    static final int fib(int n) {
        if (n <= 1)
            return n;
        int r = 0;
        DynamicLeftSpineFib rt = null;
        while (getSurplusQueuedTaskCount() <= 3) {
            int m = n - 2;
            n -= 1;
            if (m <= 1) {
                r += m;
                if (n > 1) {
                    r += n - 2;
                    n -= 1;
                }
                break;
            }
            (rt = new DynamicLeftSpineFib(m, rt)).fork();
        }
        if (n <= 1)
            r += n;
        else
            r += seqFib(n - 2) + fib(n - 1);
        if (rt != null)
            r += collectRights(rt);
        return r;
    }

    static final int collectRights(DynamicLeftSpineFib rt) {
        int r = 0;
        while (rt != null) {
            DynamicLeftSpineFib rn = rt.next;
            rt.next = null;
            if (rt.tryUnfork())
                r += fib(rt.number);
            else {
                rt.join();
                r += rt.number;
            }
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

