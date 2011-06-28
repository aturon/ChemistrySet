/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

//import jsr166y.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;


/**
 * Divide and Conquer matrix multiply demo
 */
public class MatrixMultiply {

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static final int DEFAULT_GRANULARITY = 32;

    /**
     * The quadrant size at which to stop recursing down
     * and instead directly multiply the matrices.
     * Must be a power of two. Minimum value is 2.
     */
    static int granularity = DEFAULT_GRANULARITY;

    public static void main(String[] args) throws Exception {

        final String usage = "Usage: java MatrixMultiply <threads> <matrix size (must be a power of two)> [<granularity>] \n Size and granularity must be powers of two.\n For example, try java MatrixMultiply 2 512 16";

        int procs = 0;
        int n = 2048;
        int runs = 5;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                n = Integer.parseInt(args[1]);
            if (args.length > 2)
                granularity = Integer.parseInt(args[2]);
            if (args.length > 3)
                runs = Integer.parseInt(args[2]);
        }

        catch (Exception e) {
            System.out.println(usage);
            return;
        }

        if ( ((n & (n - 1)) != 0) ||
             ((granularity & (granularity - 1)) != 0) ||
             granularity < 2) {
            System.out.println(usage);
            return;
        }

        ForkJoinPool pool = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);
        System.out.println("procs: " + pool.getParallelism() +
                           " n: " + n + " granularity: " + granularity +
                           " runs: " + runs);

        float[][] a = new float[n][n];
        float[][] b = new float[n][n];
        float[][] c = new float[n][n];

        for (int i = 0; i < runs; ++i) {
            init(a, b, n);
            long start = System.nanoTime();
            pool.invoke(new Multiplier(a, 0, 0, b, 0, 0, c, 0, 0, n));
            long time = System.nanoTime() - start;
            double secs = ((double)time) / NPS;
            Thread.sleep(100);
            System.out.printf("\tTime: %7.3f\n", secs);
            // check(c, n);
        }
        System.out.println(pool.toString());
        pool.shutdown();
    }


    // To simplify checking, fill with all 1's. Answer should be all n's.
    static void init(float[][] a, float[][] b, int n) {
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                a[i][j] = 1.0F;
                b[i][j] = 1.0F;
            }
        }
    }

    static void check(float[][] c, int n) {
        for (int i = 0; i < n; i++ ) {
            for (int j = 0; j < n; j++ ) {
                if (c[i][j] != n) {
                    throw new Error("Check Failed at [" + i +"]["+j+"]: " + c[i][j]);
                }
            }
        }
    }

    /**
     * Multiply matrices AxB by dividing into quadrants, using algorithm:
     * <pre>
     *      A      x      B
     *
     *  A11 | A12     B11 | B12     A11*B11 | A11*B12     A12*B21 | A12*B22
     * |----+----| x |----+----| = |--------+--------| + |---------+-------|
     *  A21 | A22     B21 | B21     A21*B11 | A21*B21     A22*B21 | A22*B22
     * </pre>
     */


    static class Multiplier extends RecursiveAction {
        final float[][] A;   // Matrix A
        final int aRow;      // first row    of current quadrant of A
        final int aCol;      // first column of current quadrant of A

        final float[][] B;   // Similarly for B
        final int bRow;
        final int bCol;

        final float[][] C;   // Similarly for result matrix C
        final int cRow;
        final int cCol;

        final int size;      // number of elements in current quadrant

        Multiplier(float[][] A, int aRow, int aCol,
                   float[][] B, int bRow, int bCol,
                   float[][] C, int cRow, int cCol,
                   int size) {
            this.A = A; this.aRow = aRow; this.aCol = aCol;
            this.B = B; this.bRow = bRow; this.bCol = bCol;
            this.C = C; this.cRow = cRow; this.cCol = cCol;
            this.size = size;
        }

        public void compute() {

            if (size <= granularity) {
                multiplyStride2();
            }

            else {
                int h = size / 2;

                invokeAll(new Seq2[] {
                    seq(new Multiplier(A, aRow,   aCol,    // A11
                                       B, bRow,   bCol,    // B11
                                       C, cRow,   cCol,    // C11
                                       h),
                        new Multiplier(A, aRow,   aCol+h,  // A12
                                       B, bRow+h, bCol,    // B21
                                       C, cRow,   cCol,    // C11
                                       h)),

                    seq(new Multiplier(A, aRow,   aCol,    // A11
                                       B, bRow,   bCol+h,  // B12
                                       C, cRow,   cCol+h,  // C12
                                       h),
                        new Multiplier(A, aRow,   aCol+h,  // A12
                                       B, bRow+h, bCol+h,  // B22
                                       C, cRow,   cCol+h,  // C12
                                       h)),

                    seq(new Multiplier(A, aRow+h, aCol,    // A21
                                       B, bRow,   bCol,    // B11
                                       C, cRow+h, cCol,    // C21
                                       h),
                        new Multiplier(A, aRow+h, aCol+h,  // A22
                                       B, bRow+h, bCol,    // B21
                                       C, cRow+h, cCol,    // C21
                                       h)),

                    seq(new Multiplier(A, aRow+h, aCol,    // A21
                                       B, bRow,   bCol+h,  // B12
                                       C, cRow+h, cCol+h,  // C22
                                       h),
                        new Multiplier(A, aRow+h, aCol+h,  // A22
                                       B, bRow+h, bCol+h,  // B22
                                       C, cRow+h, cCol+h,  // C22
                                       h))
                });
            }
        }

        /**
         * Version of matrix multiplication that steps 2 rows and columns
         * at a time. Adapted from Cilk demos.
         * Note that the results are added into C, not just set into C.
         * This works well here because Java array elements
         * are created with all zero values.
         */
        void multiplyStride2() {
            for (int j = 0; j < size; j+=2) {
                for (int i = 0; i < size; i +=2) {

                    float[] a0 = A[aRow+i];
                    float[] a1 = A[aRow+i+1];

                    float s00 = 0.0F;
                    float s01 = 0.0F;
                    float s10 = 0.0F;
                    float s11 = 0.0F;

                    for (int k = 0; k < size; k+=2) {

                        float[] b0 = B[bRow+k];

                        s00 += a0[aCol+k]   * b0[bCol+j];
                        s10 += a1[aCol+k]   * b0[bCol+j];
                        s01 += a0[aCol+k]   * b0[bCol+j+1];
                        s11 += a1[aCol+k]   * b0[bCol+j+1];

                        float[] b1 = B[bRow+k+1];

                        s00 += a0[aCol+k+1] * b1[bCol+j];
                        s10 += a1[aCol+k+1] * b1[bCol+j];
                        s01 += a0[aCol+k+1] * b1[bCol+j+1];
                        s11 += a1[aCol+k+1] * b1[bCol+j+1];
                    }

                    C[cRow+i]  [cCol+j]   += s00;
                    C[cRow+i]  [cCol+j+1] += s01;
                    C[cRow+i+1][cCol+j]   += s10;
                    C[cRow+i+1][cCol+j+1] += s11;
                }
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


}
