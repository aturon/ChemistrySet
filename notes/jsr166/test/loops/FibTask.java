import java.util.concurrent.*;

/**
 * Recursive task-based version of Fibonacci. Computes:
 * <pre>
 * Computes fibonacci(n) = fibonacci(n-1) + fibonacci(n-2);  for n> 1
 *          fibonacci(0) = 0;
 *          fibonacci(1) = 1.
 * </pre>
 */
public final class FibTask extends RecursiveTask<Integer> {

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
            System.out.println("Usage: java Fib <threads> <number> [<sequentialThreshold>]");
            return;
        }

        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 20; ++i) {
                test(g, num);
                //            Thread.sleep(1000);
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
        int result = g.invoke(new FibTask(num));
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        System.out.print("FibTask " + num + " = " + result);
        System.out.printf("\tTime: %7.3f", secs);

        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.printf(" Workers: %5d", g.getPoolSize());
        System.out.println();
    }


    // Initialized with argument; replaced with result
    int number;

    FibTask(int n) { number = n; }

    public Integer compute() {
        int n = number;

        // Handle base cases:
        if (n <= 1) {
            return n;
        }
        // Use sequential code for small problems:
        else if (n <= sequentialThreshold) {
            return seqFib(n);
        }
        // Otherwise use recursive parallel decomposition:
        else {
            FibTask f1 = new FibTask(n - 1);
            f1.fork();
            FibTask f2 = new FibTask(n - 2);
            return f2.compute() + f1.join();
        }
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

