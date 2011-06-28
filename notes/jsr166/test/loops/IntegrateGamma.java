/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;

/**
 * Adapted from FJTask version.
 * Sample program using Guassian Quadrature for numerical integration.
 * Inspired by a
 * <A href="http://www.cs.uga.edu/~dkl/filaments/dist.html"> Filaments</A>
 * demo program.
 *
 */

public class IntegrateGamma {
    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);
    public static void main(String[] args) {
        int procs = 0;
        double start = 1.0;
        double end = 96.0;
        int exp = 5;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                start = new Double(args[1]).doubleValue();
            if (args.length > 2)
                end = new Double(args[2]).doubleValue();
            if (args.length > 3)
                exp = Integer.parseInt(args[3]);
        }
        catch (Exception e) {
            System.out.println("Usage: java IntegrateGamma <threads> <lower bound> <upper bound> <exponent>\n (for example 2 1 48 5).");
            return;
        }

        ForkJoinPool g = (procs == 0) ? new ForkJoinPool() :
            new ForkJoinPool(procs);

        System.out.println("Integrating from " + start + " to " + end + " exponent: " + exp + " parallelism " + g.getParallelism());

        Function f = new SampleFunction(exp);
        for (int i = 0; i < 10; ++i) {
            Integrator integrator = new Integrator(f, 0.001, g);
            long last = System.nanoTime();
            double result = integrator.integral(start, end);
            double elapsed = elapsedTime(last);
            System.out.printf("time: %7.3f", elapsed);
            System.out.println(" Answer = " + result);
        }
        System.out.println(g);
        g.shutdown();
    }

    static double elapsedTime(long startTime) {
        return (double)(System.nanoTime() - startTime) / NPS;
    }

    /*
      This is all set up as if it were part of a more serious
      framework, but is for now just a demo, with all
      classes declared as static within Integrate
    */

    /** A function to be integrated */
    static interface Function {
        double compute(double x);
    }

    /**
     * Sample from filaments demo.
     * Computes (2*n-1)*(x^(2*n-1)) for all odd values.
     */
    static class SampleFunction implements Function {
        final int n;
        SampleFunction(int n) { this.n = n; }

        public double compute(double x) {
            double power = x;
            double xsq = x * x;
            double val = power;
            double di = 1.0;
            for (int i = n - 1; i > 0; --i) {
                di += 2.0;
                power *= xsq;
                val += di * power;
            }
            return val;
        }
    }


    static class Integrator {
        final Function f;      // The function to integrate
        final double errorTolerance;
        final ForkJoinPool g;

        Integrator(Function f, double errorTolerance, ForkJoinPool g) {
            this.f = f;
            this.errorTolerance = errorTolerance;
            this.g = g;
        }

        double integral(double lowerBound, double upperBound) {
            double f_lower = f.compute(lowerBound);
            double f_upper = f.compute(upperBound);
            double initialArea = 0.5 * (upperBound-lowerBound) * (f_upper + f_lower);
            Quad q = new Quad(lowerBound, upperBound,
                              f_lower, f_upper,
                              initialArea);
            g.invoke(q);
            return q.area;
        }


        /**
         * FJTask to recursively perform the quadrature.
         * Algorithm:
         *  Compute the area from lower bound to the center point of interval,
         *  and from the center point to the upper bound. If this
         *  differs from the value from lower to upper by more than
         *  the error tolerance, recurse on each half.
         */
        final class Quad extends RecursiveAction {
            final double left;       // lower bound
            final double right;      // upper bound
            final double f_left;     // value of the function evaluated at left
            final double f_right;    // value of the function evaluated at right

            // Area initialized with original estimate from left to right.
            // It is replaced with refined value.
            volatile double area;

            Quad(double left, double right,
                 double f_left, double f_right,
                 double area) {
                this.left = left;
                this.right = right;
                this.f_left = f_left;
                this.f_right = f_right;
                this.area = area;
            }

            public void compute() {
                double center = 0.5 * (left + right);
                double f_center = f.compute(center);

                double leftArea  = 0.5 * (center - left)  * (f_left + f_center);
                double rightArea = 0.5 * (right - center) * (f_center + f_right);
                double sum = leftArea + rightArea;

                double diff = sum - area;
                if (diff < 0) diff = -diff;

                if (diff >= errorTolerance) {
                    Quad q1 = new Quad(left,   center, f_left,   f_center, leftArea);
                    q1.fork();
                    Quad q2 = new Quad(center, right,  f_center, f_right,  rightArea);
                    q2.compute();
                    q1.join();
                    sum = q1.area + q2.area;
                }

                area = sum;
            }
        }
    }

}


