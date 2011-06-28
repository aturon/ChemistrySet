/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class AsyncNQueensCS extends LinkedAsyncAction {

    static long lastStealCount;
    static int boardSize;

    static final int[] expectedSolutions = new int[] {
        0, 1, 0, 0, 2, 10, 4, 40, 92, 352, 724, 2680, 14200,
        73712, 365596, 2279184, 14772512, 95815104, 666090624
    }; // see http://www.durangobill.com/N_Queens.html

    static final int FIRST_SIZE = 8; // smaller ones too short to measure well
    static final int LAST_SIZE = 15; // bigger ones too long to wait for

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int procs = 0;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
        }
        catch (Exception e) {
            System.out.println("Usage: java AsyncNQueensCS <threads> ");
            return;
        }
        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
                new ForkJoinPool(procs);
            System.out.println("Number of procs=" + g.getParallelism());
            lastStealCount = g.getStealCount();
            for (int i = FIRST_SIZE; i <= LAST_SIZE; i++)
                test(g, i);
            g.shutdown();
        }
    }

    static void test(ForkJoinPool g, int i) throws Exception {
        boardSize = i;
        int ps = g.getParallelism();
        long start = System.nanoTime();
        AsyncNQueensCS task = new AsyncNQueensCS(null, new int[0]);
        g.invoke(task);
        int solutions = task.solutions;
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        if (solutions != expectedSolutions[i])
            throw new Error();
        System.out.printf("AsyncNQueensCS %3d", i);
        System.out.printf(" Time: %7.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.println();
    }

    // Boards are represented as arrays where each cell
    // holds the column number of the queen in that row

    final int[] sofar;
    volatile int solutions;
    AsyncNQueensCS(AsyncNQueensCS parent, int[] a) {
        super(parent);
        this.sofar = a;
    }

    public final boolean exec() {
        int bs = boardSize;
        int row = sofar.length;
        if (row >= bs)
            solutions = 1;
        else {
            outer:
            for (int q = 0; q < bs; ++q) {
                for (int i = 0; i < row; i++) {
                    int p = sofar[i];
                    if (q == p || q == p - (row - i) || q == p + (row - i))
                        continue outer; // attacked
                }
                int[] next = Arrays.copyOf(sofar, row+1);
                next[row] = q;
                new AsyncNQueensCS(this, next).fork();
            }
        }
        complete();
        return false;
    }

    protected void onCompletion() {
        LinkedAsyncAction p;
        int n = solutions;
        if (n != 0 && (p = getParent()) != null)
            solutionUpdater.addAndGet((AsyncNQueensCS)p, n);
    }

    static final AtomicIntegerFieldUpdater<AsyncNQueensCS> solutionUpdater =
        AtomicIntegerFieldUpdater.newUpdater(AsyncNQueensCS.class, "solutions");


}
