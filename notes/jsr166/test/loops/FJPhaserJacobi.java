// Barrier version of Jacobi iteration

import java.util.concurrent.*;

public class FJPhaserJacobi {

    static int dimGran;

    static final double EPSILON = 0.0001;  // convergence criterion

    public static void main(String[] args) {
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

        ForkJoinPool fjp = new ForkJoinPool();
        //        int granularity = (n * n / fjp.getParallelism()) / 2;
        int granularity = n * n / fjp.getParallelism();
        dimGran = (int)(Math.sqrt(granularity));

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
            fjp.invoke(driver);

            long time = System.currentTimeMillis() - startTime;
            double secs = ((double)time) / 1000.0;

            System.out.println("Compute Time: " + secs);
            System.out.println(fjp);

        }

    }

    static class Segment extends CyclicAction {
        double[][] A; // matrix to get old values from
        double[][] B; // matrix to put new values into
        // indices of current submatrix
        final int loRow;
        final int hiRow;
        final int loCol;
        final int hiCol;
        volatile double maxDiff; // maximum difference between old and new values

        Segment(double[][] A, double[][] B,
                int loRow, int hiRow,
                int loCol, int hiCol,
                Phaser br) {
            super(br);
            this.A = A;   this.B = B;
            this.loRow = loRow; this.hiRow = hiRow;
            this.loCol = loCol; this.hiCol = hiCol;
        }

        public void step() {
            maxDiff = update(A, B);
            double[][] tmp = A; A = B; B = tmp;
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

    static class MyPhaser extends Phaser {
        final int max;
        MyPhaser(int steps) { this.max = steps - 1; }
        public boolean onAdvance(int phase, int registeredParties) {
            return phase >= max || registeredParties <= 0;
        }
    }

    static class Driver extends RecursiveAction {
        double[][] A; // matrix to get old values from
        double[][] B; // matrix to put new values into
        final int loRow;   // indices of current submatrix
        final int hiRow;
        final int loCol;
        final int hiCol;
        final int steps;
        Driver(double[][] mat1, double[][] mat2,
               int firstRow, int lastRow,
               int firstCol, int lastCol,
               int steps) {

            this.A = mat1;   this.B = mat2;
            this.loRow = firstRow; this.hiRow = lastRow;
            this.loCol = firstCol; this.hiCol = lastCol;
            this.steps = steps;
        }

        public void compute() {
            int rows = hiRow - loRow + 1;
            int cols = hiCol - loCol + 1;
            int rblocks = (int)(Math.round((float)rows / dimGran));
            int cblocks = (int)(Math.round((float)cols / dimGran));

            int n = rblocks * cblocks;

            System.out.println("Using " + n + " segments");

            Segment[] segs = new Segment[n];
            Phaser barrier = new MyPhaser(steps);
            int k = 0;
            for (int i = 0; i < rblocks; ++i) {
                int lr = loRow + i * dimGran;
                int hr = lr + dimGran;
                if (i == rblocks-1) hr = hiRow;

                for (int j = 0; j < cblocks; ++j) {
                    int lc = loCol + j * dimGran;
                    int hc = lc + dimGran;
                    if (j == cblocks-1) hc = hiCol;

                    segs[k] = new Segment(A, B, lr, hr, lc, hc, barrier);
                    ++k;
                }
            }
            invokeAll(segs);
            double maxd = 0;
            for (k = 0; k < n; ++k) {
                double md = segs[k].maxDiff;
                if (md > maxd) maxd = md;
            }
            System.out.println("Max diff after " + steps + " steps = " + maxd);
        }
    }
}

