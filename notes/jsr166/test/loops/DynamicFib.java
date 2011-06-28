import java.util.concurrent.*;

public final class DynamicFib extends RecursiveAction {
    static int procs;
    static long lastStealCount; // to display steal counts

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        procs = 0;
        int num = 45;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                num = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java DynamicFib <threads> <number>");
            return;
        }
        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool pool = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            for (int i = 0; i < 20; ++i)
                test(pool, num);
            System.out.println(pool);
            pool.shutdown();
        }
    }

    static void test(ForkJoinPool pool, int num) throws Exception {
        int ps = pool.getParallelism();
        long start = System.nanoTime();
        DynamicFib f = new DynamicFib(num);
        pool.invoke(f);
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        long result = f.number;
        System.out.print("DynamicFib " + num + " = " + result);
        System.out.printf("\tTime: %9.3f", secs);
        long sc = pool.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals: %4d", ns/ps);
        System.out.printf(" Workers: %4d", pool.getPoolSize());
        System.out.println();
    }


    int number;     // Initialized with argument; replaced with result
    DynamicFib(int n) { number = n; }
    public void compute() {
        number = fib(number);
    }

    static int fib(int n) {
        int res;
        if (n <= 1)
            res = n;
        else if (getSurplusQueuedTaskCount() >= 4)
            res = seqFib(n);
        else {
            DynamicFib f2 = new DynamicFib(n - 2);
            f2.fork();
            res = fib(n - 1);
            if (f2.tryUnfork())
                res += fib(n - 2);
            else {
                f2.quietlyJoin();
                res += f2.number;
            }
        }
        return res;
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

