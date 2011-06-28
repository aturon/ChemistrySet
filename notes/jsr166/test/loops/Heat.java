/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// Adapted from a cilk benchmark

import java.util.concurrent.*;

public class Heat {
    static final long NPS = (1000L * 1000 * 1000);

    // Parameters
    static int nx;
    static int ny;
    static int nt;
    static int leafmaxcol;

    // the matrix representing the cells
    static double[][] newm;

    // alternating workspace matrix
    static double[][] oldm;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        nx = 4096;
        ny = 1024;
        nt = 1000;
        leafmaxcol = 16;

        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                nx = Integer.parseInt(args[1]);
            if (args.length > 2)
                ny = Integer.parseInt(args[2]);
            if (args.length > 3)
                nt = Integer.parseInt(args[3]);
            if (args.length > 4)
                leafmaxcol = Integer.parseInt(args[4]);
        }
        catch (Exception e) {
            System.out.println("Usage: java Heat threads rows cols steps granularity");
            return;
        }

        ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);

        System.out.print("parallelism = " + g.getParallelism());
        System.out.print(" granularity = " + leafmaxcol);
        System.out.print(" rows = " + nx);
        System.out.print(" columns = " + ny);
        System.out.println(" steps = " + nt);

        oldm = new double[nx][ny];
        newm = new double[nx][ny];

        for (int i = 0; i < 5; ++i) {
            long last = System.nanoTime();
            RecursiveAction main = new RecursiveAction() {
                    public void compute() {
                        for (int timestep = 0; timestep <= nt; timestep++) {
                            (new Compute(0, nx, timestep)).invoke();
                        }
                    }
                };
            g.invoke(main);
            double elapsed = elapsedTime(last);
            System.out.printf("time: %7.3f", elapsed);
            System.out.println();
        }
        System.out.println(g);
        g.shutdown();
    }

    static double elapsedTime(long startTime) {
        return (double)(System.nanoTime() - startTime) / NPS;
    }

    // constants (at least for this demo)
    static final double xu = 0.0;
    static final double xo = 1.570796326794896558;
    static final double yu = 0.0;
    static final double yo = 1.570796326794896558;
    static final double tu = 0.0;
    static final double to = 0.0000001;

    static final double dx = (xo - xu) / (nx - 1);
    static final double dy = (yo - yu) / (ny - 1);
    static final double dt = (to - tu) / nt;
    static final double dtdxsq = dt / (dx * dx);
    static final double dtdysq = dt / (dy * dy);


    // the function being applied across the cells
    static final double f(double x, double y) {
        return Math.sin(x) * Math.sin(y);
    }

    // random starting values

    static final double randa(double x, double t) {
        return 0.0;
    }
    static final double randb(double x, double t) {
        return Math.exp(-2*t) * Math.sin(x);
    }
    static final double randc(double y, double t) {
        return 0.0;
    }
    static final double randd(double y, double t) {
        return Math.exp(-2*t) * Math.sin(y);
    }
    static final double solu(double x, double y, double t) {
        return Math.exp(-2*t) * Math.sin(x) * Math.sin(y);
    }




    static final class Compute extends RecursiveAction {
        final int lb;
        final int ub;
        final int time;

        Compute(int lowerBound, int upperBound, int timestep) {
            lb = lowerBound;
            ub = upperBound;
            time = timestep;
        }

        public void compute() {
            if (ub - lb > leafmaxcol) {
                int mid = (lb + ub) >>> 1;
                Compute left = new Compute(lb, mid, time);
                left.fork();
                new Compute(mid, ub, time).compute();
                left.join();
            }
            else if (time == 0)     // if first pass, initialize cells
                init();
            else if (time %2 != 0)  // alternate new/old
                compstripe(newm, oldm);
            else
                compstripe(oldm, newm);
        }


        /** Updates all cells. */
        final void compstripe(double[][] newMat, double[][] oldMat) {

            // manually mangled to reduce array indexing

            final int llb = (lb == 0)  ? 1 : lb;
            final int lub = (ub == nx) ? nx - 1 : ub;

            double[] west;
            double[] row = oldMat[llb-1];
            double[] east = oldMat[llb];

            for (int a = llb; a < lub; a++) {

                west = row;
                row =  east;
                east = oldMat[a+1];

                double prev;
                double cell = row[0];
                double next = row[1];

                double[] nv = newMat[a];

                for (int b = 1; b < ny-1; b++) {

                    prev = cell;
                    cell = next;
                    double twoc = 2 * cell;
                    next = row[b+1];

                    nv[b] = cell
                        + dtdysq * (prev    - twoc + next)
                        + dtdxsq * (east[b] - twoc + west[b]);

                }
            }

            edges(newMat, llb, lub,  tu + time * dt);
        }


        // the original version from cilk
        final void origcompstripe(double[][] newMat, double[][] oldMat) {

            final int llb = (lb == 0)  ? 1 : lb;
            final int lub = (ub == nx) ? nx - 1 : ub;

            for (int a = llb; a < lub; a++) {
                for (int b = 1; b < ny-1; b++) {
                    double cell = oldMat[a][b];
                    double twoc = 2 * cell;
                    newMat[a][b] = cell
                        + dtdxsq * (oldMat[a+1][b] - twoc + oldMat[a-1][b])
                        + dtdysq * (oldMat[a][b+1] - twoc + oldMat[a][b-1]);

                }
            }

            edges(newMat, llb, lub,  tu + time * dt);
        }


        /** Initializes all cells. */
        final void init() {
            final int llb = (lb == 0) ? 1 : lb;
            final int lub = (ub == nx) ? nx - 1 : ub;

            for (int a = llb; a < lub; a++) {   /* inner nodes */
                double[] ov = oldm[a];
                double x = xu + a * dx;
                double y = yu;
                for (int b = 1; b < ny-1; b++) {
                    y += dy;
                    ov[b] = f(x, y);
                }
            }

            edges(oldm, llb, lub, 0);

        }

        /** Fills in edges with boundary values. */
        final void edges(double [][] m, int llb, int lub, double t) {

            for (int a = llb; a < lub; a++) {
                double[] v = m[a];
                double x = xu + a * dx;
                v[0] = randa(x, t);
                v[ny-1] = randb(x, t);
            }

            if (lb == 0) {
                double[] v = m[0];
                double y = yu;
                for (int b = 0; b < ny; b++) {
                    y += dy;
                    v[b] = randc(y, t);
                }
            }

            if (ub == nx) {
                double[] v = m[nx - 1];
                double y = yu;
                for (int b = 0; b < ny; b++) {
                    y += dy;
                    v[b] = randd(y, t);
                }
            }
        }
    }
}
