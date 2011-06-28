/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;

/**
 * Sample program using Guassian Quadrature for numerical integration.
 * This version uses a simplified hardwired function.  Inspired by a
 * <A href="http://www.cs.uga.edu/~dkl/filaments/dist.html">
 * Filaments</A> demo program.
 *
 */
public final class Integrate {

    static final double errorTolerance = 1.0e-12;
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static final int SERIAL = -1;
    static final int DYNAMIC = 0;
    static final int FORK = 1;
    static int forkPolicy = DYNAMIC;
    static String forkArg = "dynamic";

    // the function to integrate
    static double computeFunction(double x) {
        return (x * x + 1.0) * x;
    }

    static final double start = 0.0;
    static final double end = 1536.0;
    /*
     * The number of recursive calls for
     * integrate from start to end.
     * (Empirically determined)
     */
    static final int calls = 263479047;

    public static void main(String[] args) throws Exception {
        int procs = 0;

        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1) {
                forkArg = args[1];
                if (forkArg.startsWith("s"))
                    forkPolicy = SERIAL;
                else if (forkArg.startsWith("f"))
                    forkPolicy = FORK;
            }
        }
        catch (Exception e) {
            System.out.println("Usage: java Integrate3 threads <s[erial] | d[ynamic] | f[ork] - default d>");
            return;
        }
        oneTest(procs);
        oneTest(procs);
        oneTest(procs);
    }

    static void oneTest(int procs) {
        ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);
        System.out.println("Number of procs=" + g.getParallelism());
        System.out.println("Integrating from " + start + " to " + end +
                           " forkPolicy = " + forkArg);
        long lastTime = System.nanoTime();
        for (int i = 0; i < 20; ++i) {
            double a;
            if (forkPolicy == SERIAL)
                a = SQuad.computeArea(g, start, end);
            else if (forkPolicy == FORK)
                a = FQuad.computeArea(g, start, end);
            else
                a = DQuad.computeArea(g, start, end);
            long now = System.nanoTime();
            double s = ((double)(now - lastTime))/NPS;
            lastTime = now;
            System.out.printf("Calls/sec: %12d", (long) (calls / s));
            System.out.printf(" Time: %7.3f", s);
            System.out.printf(" Threads: %5d", g.getPoolSize());
            //            System.out.printf(" Area: %12.1f", a);
            System.out.println();
        }
        System.out.println(g);
        g.shutdown();
    }


    // Sequential version
    static final class SQuad extends RecursiveAction {
        static double computeArea(ForkJoinPool pool, double l, double r) {
            SQuad q = new SQuad(l, r, 0);
            pool.invoke(q);
            return q.area;
        }

        final double left;       // lower bound
        final double right;      // upper bound
        double area;

        SQuad(double l, double r, double a) {
            this.left = l; this.right = r; this.area = a;
        }

        public final void compute() {
            double l = left;
            double r = right;
            area = recEval(l, r, (l * l + 1.0) * l, (r * r + 1.0) * r, area);
        }

        static final double recEval(double l, double r, double fl,
                                    double fr, double a) {
            double h = (r - l) * 0.5;
            double c = l + h;
            double fc = (c * c + 1.0) * c;
            double hh = h * 0.5;
            double al = (fl + fc) * hh;
            double ar = (fr + fc) * hh;
            double alr = al + ar;
            if (Math.abs(alr - a) <= errorTolerance)
                return alr;
            else
                return recEval(c, r, fc, fr, ar) + recEval(l, c, fl, fc, al);
        }

    }

    //....................................

    // ForkJoin version
    static final class FQuad extends RecursiveAction {
        static double computeArea(ForkJoinPool pool, double l, double r) {
            FQuad q = new FQuad(l, r, 0);
            pool.invoke(q);
            return q.area;
        }

        final double left;       // lower bound
        final double right;      // upper bound
        double area;

        FQuad(double l, double r, double a) {
            this.left = l; this.right = r; this.area = a;
        }

        public final void compute() {
            double l = left;
            double r = right;
            area = recEval(l, r, (l * l + 1.0) * l, (r * r + 1.0) * r, area);
        }

        static final double recEval(double l, double r, double fl,
                                    double fr, double a) {
            double h = (r - l) * 0.5;
            double c = l + h;
            double fc = (c * c + 1.0) * c;
            double hh = h * 0.5;
            double al = (fl + fc) * hh;
            double ar = (fr + fc) * hh;
            double alr = al + ar;
            if (Math.abs(alr - a) <= errorTolerance)
                return alr;
            FQuad q = new FQuad(l, c, al);
            q.fork();
            ar = recEval(c, r, fc, fr, ar);
            if (!q.tryUnfork()) {
                q.quietlyJoin();
                return ar + q.area;
            }
            return ar + recEval(l, c, fl, fc, al);
        }

    }

    // ...........................

    // Version using on-demand Fork
    static final class DQuad extends RecursiveAction {
        static double computeArea(ForkJoinPool pool, double l, double r) {
            DQuad q = new DQuad(l, r, 0);
            pool.invoke(q);
            return q.area;
        }

        final double left;       // lower bound
        final double right;      // upper bound
        double area;

        DQuad(double l, double r, double a) {
            this.left = l; this.right = r; this.area = a;
        }

        public final void compute() {
            double l = left;
            double r = right;
            area = recEval(l, r, (l * l + 1.0) * l, (r * r + 1.0) * r, area);
        }

        static final double recEval(double l, double r, double fl,
                                    double fr, double a) {
            double h = (r - l) * 0.5;
            double c = l + h;
            double fc = (c * c + 1.0) * c;
            double hh = h * 0.5;
            double al = (fl + fc) * hh;
            double ar = (fr + fc) * hh;
            double alr = al + ar;
            if (Math.abs(alr - a) <= errorTolerance)
                return alr;
            DQuad q = null;
            if (getSurplusQueuedTaskCount() <= 3)
                (q = new DQuad(l, c, al)).fork();
            ar = recEval(c, r, fc, fr, ar);
            if (q != null && !q.tryUnfork()) {
                q.quietlyJoin();
                return ar + q.area;
            }
            return ar + recEval(l, c, fl, fc, al);
        }

    }

}


