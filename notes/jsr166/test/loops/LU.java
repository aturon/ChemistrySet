/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

//import jsr166y.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;


/**
 * LU matrix decomposition demo
 * Based on those in Cilk and Hood
 */
public final class LU {

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    // granularity is hard-wired as compile-time constant here
    static final int BLOCK_SIZE = 16;
    static final boolean CHECK = false; // set true to check answer

    public static void main(String[] args) throws Exception {

        final String usage = "Usage: java LU <threads> <matrix size (must be a power of two)> [runs] \n For example, try java LU 2 512";

        int procs = 0;
        int n = 2048;
        int runs = 5;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                n = Integer.parseInt(args[1]);
            if (args.length > 2)
                runs = Integer.parseInt(args[2]);
        } catch (Exception e) {
            System.out.println(usage);
            return;
        }

        if ( ((n & (n - 1)) != 0)) {
            System.out.println(usage);
            return;
        }
        ForkJoinPool pool = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);
        System.out.println("procs: " + pool.getParallelism() +
                           " n: " + n + " runs: " + runs);
        for (int run = 0; run < runs; ++run) {
            double[][] m = new double[n][n];
            randomInit(m, n);
            double[][] copy = null;
            if (CHECK) {
                copy = new double[n][n];
                for (int i = 0; i < n; ++i) {
                    for (int j = 0; j < n; ++j) {
                        copy[i][j] = m[i][j];
                    }
                }
            }
            Block M = new Block(m, 0, 0);
            long start = System.nanoTime();
            pool.invoke(new LowerUpper(n, M));
            long time = System.nanoTime() - start;
            double secs = ((double)time) / NPS;
            System.out.printf("\tTime: %7.3f\n", secs);

            if (CHECK) check(m, copy, n);
        }
        System.out.println(pool.toString());
        pool.shutdown();
    }


    static void randomInit(double[][] M, int n) {
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < n; ++i)
            for (int j = 0; j < n; ++j)
                M[i][j] = rng.nextDouble();
        // for compatibility with hood demo, force larger diagonals
        for (int k = 0; k < n; ++k)
            M[k][k] *= 10.0;
    }

    static void check(double[][] LU, double[][] M, int n) {
        double maxDiff = 0.0; // track max difference
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                double v = 0.0;
                int k;
                for (k = 0; k < i && k <= j; k++ ) v += LU[i][k] * LU[k][j];
                if (k == i && k <= j ) v += LU[k][j];
                double diff = M[i][j] - v;
                if (diff < 0) diff = -diff;
                if (diff > 0.001) {
                    System.out.println("large diff at[" + i + "," + j + "]: " + M[i][j] + " vs " + v);
                }
                if (diff > maxDiff) maxDiff = diff;
            }
        }

        System.out.println("Max difference = " + maxDiff);
    }


    // Blocks record underlying matrix, and offsets into current block
    static final class Block {
        final double[][] m;
        final int loRow;
        final int loCol;

        Block(double[][] mat, int lr, int lc) {
            m = mat; loRow = lr; loCol = lc;
        }
    }

    static final class Schur extends RecursiveAction {
        final int size;
        final Block V;
        final Block W;
        final Block M;

        Schur(int size, Block V, Block W, Block M) {
            this.size = size; this.V = V; this.W = W; this.M = M;
        }

        void schur() { // base case
            for (int j = 0; j < BLOCK_SIZE; ++j) {
                for (int i = 0; i < BLOCK_SIZE; ++i) {
                    double s = M.m[i+M.loRow][j+M.loCol];
                    for (int k = 0; k < BLOCK_SIZE; ++k) {
                        s -= V.m[i+V.loRow][k+V.loCol] * W.m[k+W.loRow][j+W.loCol];
                    }
                    M.m[i+M.loRow][j+M.loCol] = s;
                }
            }
        }

        public void compute() {
            if (size == BLOCK_SIZE) {
                schur();
            }
            else {
                int h = size / 2;

                Block M00 = new Block(M.m, M.loRow,   M.loCol);
                Block M01 = new Block(M.m, M.loRow,   M.loCol+h);
                Block M10 = new Block(M.m, M.loRow+h, M.loCol);
                Block M11 = new Block(M.m, M.loRow+h, M.loCol+h);

                Block V00 = new Block(V.m, V.loRow,   V.loCol);
                Block V01 = new Block(V.m, V.loRow,   V.loCol+h);
                Block V10 = new Block(V.m, V.loRow+h, V.loCol);
                Block V11 = new Block(V.m, V.loRow+h, V.loCol+h);

                Block W00 = new Block(W.m, W.loRow,   W.loCol);
                Block W01 = new Block(W.m, W.loRow,   W.loCol+h);
                Block W10 = new Block(W.m, W.loRow+h, W.loCol);
                Block W11 = new Block(W.m, W.loRow+h, W.loCol+h);

                Seq2 s3 = seq(new Schur(h, V10, W01, M11),
                              new Schur(h, V11, W11, M11));
                s3.fork();
                Seq2 s2 = seq(new Schur(h, V10, W00, M10),
                              new Schur(h, V11, W10, M10));
                s2.fork();
                Seq2 s1 = seq(new Schur(h, V00, W01, M01),
                              new Schur(h, V01, W11, M01));
                s1.fork();
                new Schur(h, V00, W00, M00).compute();
                new Schur(h, V01, W10, M00).compute();
                if (s1.tryUnfork()) s1.compute(); else s1.join();
                if (s2.tryUnfork()) s2.compute(); else s2.join();
                if (s3.tryUnfork()) s3.compute(); else s3.join();
            }
        }
    }

    static final class Lower extends RecursiveAction {
        final int size;
        final Block L;
        final Block M;
        Lower(int size, Block L, Block M) {
            this.size = size; this.L = L; this.M = M;
        }

        void lower() {  // base case
            for (int i = 1; i < BLOCK_SIZE; ++i) {
                for (int k = 0; k < i; ++k) {
                    double a = L.m[i+L.loRow][k+L.loCol];
                    double[] x = M.m[k+M.loRow];
                    double[] y = M.m[i+M.loRow];
                    int n = BLOCK_SIZE;
                    for (int p = n-1; p >= 0; --p) {
                        y[p+M.loCol] -= a * x[p+M.loCol];
                    }
                }
            }
        }

        public void compute() {
            if (size == BLOCK_SIZE) {
                lower();
            }
            else {
                int h = size / 2;

                Block M00 = new Block(M.m, M.loRow,   M.loCol);
                Block M01 = new Block(M.m, M.loRow,   M.loCol+h);
                Block M10 = new Block(M.m, M.loRow+h, M.loCol);
                Block M11 = new Block(M.m, M.loRow+h, M.loCol+h);

                Block L00 = new Block(L.m, L.loRow,   L.loCol);
                Block L01 = new Block(L.m, L.loRow,   L.loCol+h);
                Block L10 = new Block(L.m, L.loRow+h, L.loCol);
                Block L11 = new Block(L.m, L.loRow+h, L.loCol+h);


                Seq3 s1 =
                    seq(new Lower(h, L00, M00),
                        new Schur(h, L10, M00, M10),
                        new Lower(h, L11, M10));
                Seq3 s2 =
                    seq(new Lower(h, L00, M01),
                        new Schur(h, L10, M01, M11),
                        new Lower(h, L11, M11));
                s2.fork();
                s1.compute();
                if (s2.tryUnfork()) s2.compute(); else s2.join();
            }
        }
    }


    static final class Upper extends RecursiveAction {
        final int size;
        final Block U;
        final Block M;
        Upper(int size, Block U, Block M) {
            this.size = size; this.U = U; this.M = M;
        }

        void upper() { // base case
            for (int i = 0; i < BLOCK_SIZE; ++i) {
                for (int k = 0; k < BLOCK_SIZE; ++k) {
                    double a = M.m[i+M.loRow][k+M.loCol] / U.m[k+U.loRow][k+U.loCol];
                    M.m[i+M.loRow][k+M.loCol] = a;
                    double[] x = U.m[k+U.loRow];
                    double[] y = M.m[i+M.loRow];
                    int n = BLOCK_SIZE - k - 1;
                    for (int p = n - 1; p >= 0; --p) {
                        y[p+k+1+M.loCol] -= a * x[p+k+1+U.loCol];
                    }
                }
            }
        }


        public void compute() {
            if (size == BLOCK_SIZE) {
                upper();
            }
            else {
                int h = size / 2;

                Block M00 = new Block(M.m, M.loRow,   M.loCol);
                Block M01 = new Block(M.m, M.loRow,   M.loCol+h);
                Block M10 = new Block(M.m, M.loRow+h, M.loCol);
                Block M11 = new Block(M.m, M.loRow+h, M.loCol+h);

                Block U00 = new Block(U.m, U.loRow,   U.loCol);
                Block U01 = new Block(U.m, U.loRow,   U.loCol+h);
                Block U10 = new Block(U.m, U.loRow+h, U.loCol);
                Block U11 = new Block(U.m, U.loRow+h, U.loCol+h);

                Seq3 s1 =
                    seq(new Upper(h, U00, M00),
                        new Schur(h, M00, U01, M01),
                        new Upper(h, U11, M01));
                Seq3 s2 =
                    seq(new Upper(h, U00, M10),
                        new Schur(h, M10, U01, M11),
                        new Upper(h, U11, M11));
                s2.fork();
                s1.compute();
                if (s2.tryUnfork()) s2.compute(); else s2.join();
            }
        }
    }


    static final class LowerUpper extends RecursiveAction {
        final int size;
        final Block M;
        LowerUpper(int size, Block M) {
            this.size = size; this.M = M;
        }

        void lu() {  // base case
            for (int k = 0; k < BLOCK_SIZE; ++k) {
                for (int i = k+1; i < BLOCK_SIZE; ++i) {
                    double b = M.m[k+M.loRow][k+M.loCol];
                    double a = M.m[i+M.loRow][k+M.loCol] / b;
                    M.m[i+M.loRow][k+M.loCol] = a;
                    double[] x = M.m[k+M.loRow];
                    double[] y = M.m[i+M.loRow];
                    int n = BLOCK_SIZE-k-1;
                    for (int p = n-1; p >= 0; --p) {
                        y[k+1+p+M.loCol] -= a * x[k+1+p+M.loCol];
                    }
                }
            }
        }

        public void compute() {
            if (size == BLOCK_SIZE) {
                lu();
            }
            else {
                int h = size / 2;
                Block M00 = new Block(M.m, M.loRow,   M.loCol);
                Block M01 = new Block(M.m, M.loRow,   M.loCol+h);
                Block M10 = new Block(M.m, M.loRow+h, M.loCol);
                Block M11 = new Block(M.m, M.loRow+h, M.loCol+h);

                new LowerUpper(h, M00).compute();
                Lower sl = new Lower(h, M00, M01);
                Upper su = new Upper(h, M00, M10);
                su.fork();
                sl.compute();
                if (su.tryUnfork()) su.compute(); else su.join();
                new Schur(h, M10, M01, M11).compute();
                new LowerUpper(h, M11).compute();
            }
        }
    }

    static Seq2 seq(RecursiveAction task1,
                               RecursiveAction task2) {
        return new Seq2(task1, task2);
    }

    static final class Seq2 extends RecursiveAction {
        final RecursiveAction fst;
        final RecursiveAction snd;
        public Seq2(RecursiveAction task1, RecursiveAction task2) {
            fst = task1;
            snd = task2;
        }
        public void compute() {
            fst.invoke();
            snd.invoke();
        }
    }

    static Seq3 seq(RecursiveAction task1,
                    RecursiveAction task2,
                    RecursiveAction task3) {
        return new Seq3(task1, task2, task3);
    }

    static final class Seq3 extends RecursiveAction {
        final RecursiveAction fst;
        final RecursiveAction snd;
        final RecursiveAction thr;
        public Seq3(RecursiveAction task1,
                    RecursiveAction task2,
                    RecursiveAction task3) {
            fst = task1;
            snd = task2;
            thr = task3;
        }
        public void compute() {
            fst.invoke();
            snd.invoke();
            thr.invoke();
        }
    }



}

