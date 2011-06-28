/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// Barrier version of Jacobi iteration

import java.util.*;
import java.util.concurrent.*;
//import jsr166y.*;

public class ThreadPhaserJacobi {

    static final int nprocs = Runtime.getRuntime().availableProcessors();

    /**
     * The maximum submatrix length (both row-wise and column-wise)
     * for any Segment
     */
    static final double EPSILON = 0.0001;  // convergence criterion

    static int dimGran;

    public static void main(String[] args) throws Exception {
        int n = 2048;
        int steps = 1000;
        try {
            if (args.length > 0)
                n = Integer.parseInt(args[0]);
            if (args.length > 1)
                steps = Integer.parseInt(args[1]);
        }

        catch (Exception e) {
            System.out.println("Usage: java ThreadPhaserJacobi <matrix size> <max steps>");
            return;
        }

        int granularity = n * n / nprocs;
        dimGran = (int) Math.sqrt(granularity);

        // allocate enough space for edges
        int dim = n+2;
        int ncells = dim * dim;
        double[][] a = new double[dim][dim];
        double[][] b = new double[dim][dim];
        // Initialize interiors to small value
        double smallVal = 1.0/dim;
        for (int i = 1; i < dim-1; ++i) {
            for (int j = 1; j < dim-1; ++j)
                a[i][j] = smallVal;
        }

        int nreps = 3;
        for (int rep = 0; rep < nreps; ++rep) {
            // Fill all edges with 1's.
            for (int k = 0; k < dim; ++k) {
                a[k][0] += 1.0;
                a[k][n+1] += 1.0;
                a[0][k] += 1.0;
                a[n+1][k] += 1.0;
            }
            Driver driver = new Driver(a, b, 1, n, 1, n, steps);
            long startTime = System.currentTimeMillis();
            driver.compute();

            long time = System.currentTimeMillis() - startTime;
            double secs = (double) time / 1000.0;

            System.out.println("Compute Time: " + secs);
        }
    }

    static class Segment implements Runnable {

        double[][] A; // matrix to get old values from
        double[][] B; // matrix to put new values into

        // indices of current submatrix
        final int loRow;
        final int hiRow;
        final int loCol;
        final int hiCol;
        final int steps;
        final Phaser barrier;
        double maxDiff; // maximum difference between old and new values

        Segment(double[][] A, double[][] B,
                int loRow, int hiRow,
                int loCol, int hiCol,
                int steps,
                Phaser barrier) {
            this.A = A;   this.B = B;
            this.loRow = loRow; this.hiRow = hiRow;
            this.loCol = loCol; this.hiCol = hiCol;
            this.steps = steps;
            this.barrier = barrier;
            barrier.register();
        }


        public void run() {
            try {
                double[][] a = A;
                double[][] b = B;

                for (int i = 0; i < steps; ++i) {
                    maxDiff = update(a, b);
                    if (barrier.awaitAdvance(barrier.arrive()) < 0)
                        break;
                    double[][] tmp = a; a = b; b = tmp;
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
        }

        double update(double[][] a, double[][] b) {
            double md = 0.0; // local for computing max diff

            for (int i = loRow; i <= hiRow; ++i) {
                for (int j = loCol; j <= hiCol; ++j) {
                    double v = 0.25 * (a[i-1][j] + a[i][j-1] +
                                       a[i+1][j] + a[i][j+1]);
                    b[i][j] = v;

                    double diff = v - a[i][j];
                    if (diff < 0) diff = -diff;
                    if (diff > md) md = diff;
                }
            }

            return md;
        }

    }


    static class Driver {
        double[][] A; // matrix to get old values from
        double[][] B; // matrix to put new values into

        final int loRow;   // indices of current submatrix
        final int hiRow;
        final int loCol;
        final int hiCol;
        final int steps;
        Segment[] allSegments;

        Driver(double[][] mat1, double[][] mat2,
               int firstRow, int lastRow,
               int firstCol, int lastCol,
               int steps) {

            this.A = mat1;   this.B = mat2;
            this.loRow = firstRow; this.hiRow = lastRow;
            this.loCol = firstCol; this.hiCol = lastCol;
            this.steps = steps;

            int rows = hiRow - loRow + 1;
            int cols = hiCol - loCol + 1;
            int rblocks = Math.round((float) rows / dimGran);
            int cblocks = Math.round((float) cols / dimGran);

            int n = rblocks * cblocks;

            Segment[] segs = new Segment[n];
            Phaser barrier = new Phaser();
            int k = 0;
            for (int i = 0; i < rblocks; ++i) {
                int lr = loRow + i * dimGran;
                int hr = lr + dimGran;
                if (i == rblocks-1) hr = hiRow;

                for (int j = 0; j < cblocks; ++j) {
                    int lc = loCol + j * dimGran;
                    int hc = lc + dimGran;
                    if (j == cblocks-1) hc = hiCol;

                    segs[k] = new Segment(A, B, lr, hr, lc, hc, steps, barrier);
                    ++k;
                }
            }
            System.out.println("Using " + n + " segments (threads)");
            allSegments = segs;
        }

        public void compute() throws InterruptedException {
            Segment[] segs = allSegments;
            int n = segs.length;
            Thread[] threads = new Thread[n];

            for (int k = 0; k < n; ++k) threads[k] = new Thread(segs[k]);
            for (int k = 0; k < n; ++k) threads[k].start();
            for (int k = 0; k < n; ++k) threads[k].join();

            double maxd = 0;
            for (int k = 0; k < n; ++k) {
                double md = segs[k].maxDiff;
                if (md > maxd) maxd = md;
            }

            System.out.println("Max diff after " + steps + " steps = " + maxd);
        }
    }


}
