/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

//import jsr166y.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * This is reworked version of one of the tests reported on in the
 * paper: Guojing Cong, Sreedhar Kodali, Sriram Krishnamoorty, Doug
 * Lea, Vijay Saraswat and Tong Wen, "Solving irregular graph problems
 * using adaptive work-stealing", ICPP, 2008.
 *
 * It runs the main batching algorithm discussed there for spanning
 * trees, for a simple regular torus graph, where each node is
 * connected to its left. right, up, and down neighbors.
 */
public class TorusSpanningTree {
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    // Dimensions for test runs.
    // graphs have side*side nodes, each with 4 neighbors
    static final int MIN_SIDE = 1000;
    static final int MAX_SIDE = 3000;
    static final int SIDE_STEP = 500;

    public static void main(String[] args) throws Exception {
        Random rng = new Random();
        int procs = NCPUS;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
        }
        catch (Exception e) {
            System.out.println("Usage: java TorusSpanningTree <threads>");
            return;
        }
        System.out.println("Number of threads: " + procs);
        System.out.println("Printing nanosec per edge across replications");
        System.out.print("for Toruses with side lengths");
        System.out.printf(" from %5d to %5d step %5d\n",
                          MIN_SIDE, MAX_SIDE, SIDE_STEP);
        ForkJoinPool pool = new ForkJoinPool(procs);

        boolean checked = false;
        for (int side = MIN_SIDE; side <= MAX_SIDE; side += SIDE_STEP) {
            int n = side * side;
            Node[] graph = makeGraph(side);
            System.out.printf( "N:%9d", n);
            for (int j = 0; j < 8; ++j) {
                Node root = graph[rng.nextInt(n)];
                long start = System.nanoTime();
                pool.invoke(new Driver(root));
                long elapsed = System.nanoTime() - start;
                double nanosPerEdge = (double) elapsed / (4 * n);
                System.out.printf(" %7.2f", nanosPerEdge);
                if (!checked) {
                    checked = true;
                    checkSpanningTree(graph, root);
                }
                pool.invoke(new Resetter(graph, 0, graph.length));
            }
            System.out.println();
        }
        System.out.println(pool);
        pool.shutdown();
    }

    static final class Node extends ForkJoinTask<Void> {
        final Node[] neighbors;
        Node parent;
        Node next;
        volatile int mark;

        Node(Node[] nbrs) {
            neighbors = nbrs;
            parent = this;
        }

        static final AtomicIntegerFieldUpdater<Node> markUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Node.class, "mark");

        boolean tryMark() {
            return mark == 0 && markUpdater.compareAndSet(this, 0, 1);
        }
        void setMark() { mark = 1; }

        /*
         * Traverse the list ("oldList") embedded across .next fields,
         * starting at this node, placing newly discovered neighboring
         * nodes in newList. If the oldList becomes exhausted, swap in
         * newList and continue. Otherwise, whenever the length of
         * newList exceeds current number of tasks in work-stealing
         * queue, push list onto queue.
         */

        static final int LOG_MAX_BATCH_SIZE = 7;

        /**
         * Since tasks are never joined, we bypass Recursive{Action,Task}
         * and just directly implement exec
         */
        public boolean exec() {
            int batchSize = 0; // computed lazily
            Node newList = null;
            int newLength = 0;
            Node oldList = this;
            Node par = parent;
            do {
                Node v = oldList;
                Node[] edges = v.neighbors;
                oldList = v.next;
                int nedges = edges.length;
                for (int k = 0; k < nedges; ++k) {
                    Node e = edges[k];
                    if (e != null && e.tryMark()) {
                        e.parent = par;
                        e.next = newList;
                        newList = e;
                        if (batchSize == 0) {
                            int s = getQueuedTaskCount();
                            batchSize = ((s >= LOG_MAX_BATCH_SIZE) ?
                                         (1 << LOG_MAX_BATCH_SIZE) :
                                         (1 << s));
                        }
                        if (++newLength >= batchSize) {
                            newLength = 0;
                            batchSize = 0;
                            if (oldList == null)
                                oldList = newList;
                            else
                                newList.fork();
                            newList = null;
                        }
                    }
                }
                if (oldList == null) {
                    oldList = newList;
                    newList = null;
                    newLength = 0;
                }
            } while (oldList != null);
            return false;
        }

        // required abstract implementations for ForkJoinTask
        public final Void getRawResult() { return null; }
        protected final void setRawResult(Void mustBeNull) { }

        public void reset() {
            reinitialize();
            parent = this;
            next = null;
            mark = 0;
        }

    }

    static final class Driver extends RecursiveAction {
        final Node root;
        Driver(Node root) {
            this.root = root;
        }
        public void compute() {
            root.setMark();
            root.fork();
            helpQuiesce();
        }
    }

    static Node[] makeGraph(int sideLength) {
        int n = sideLength * sideLength;
        Node[] vs = new Node[n];
        for (int i = 0; i < n; ++i)
            vs[i] = new Node(new Node[4]);

        // connect each node to left, right, up, down neighbors
        int maxcol = n - sideLength;
        int col = 0;
        for (int i = 0; i < sideLength; ++i) {
            for (int j = 0; j < sideLength; ++j) {
                Node[] a = vs[col + j].neighbors;
                a[0] = vs[col + ((j < sideLength-1) ? (j+1) : 0)];
                a[1] = vs[col + ((j != 0) ? (j-1) : (sideLength-1))];
                a[2] = vs[j + ((i < sideLength-1) ? (col + sideLength) : 0)];
                a[3] = vs[j + ((i != 0) ? (col - sideLength) : maxcol)];
            }
            col += sideLength;
        }
        return vs;
    }

    static void resetAll(Node[] g) {
        for (int i = 0; i < g.length; ++i)
            g[i].reset();
    }

    // check that all nodes have parents, and no cycles
    static void checkSpanningTree(Node[] g, Node root) {
        int n = g.length;
        for (int i = 0; i < n; ++i) {
            Node v = g[i];
            Node p = v;
            int k = n;
            while (p != root) {
                if (p == null)
                    throw new RuntimeException("null parent");
                if (--k <= 0)
                    throw new RuntimeException("cycle");
                p = p.parent;
            }
            v.parent = root;
        }
    }

    static final class Resetter extends RecursiveAction {
        final Node[] g;
        final int lo, hi;
        Resetter(Node[] g, int lo, int hi) {
            this.g = g; this.lo = lo; this.hi = hi;
        }
        public void compute() {
            int mid = (lo + hi) >>> 1;
            if (mid == lo || getSurplusQueuedTaskCount() > 3) {
                for (int i = lo; i < hi; ++i)
                    g[i].reset();
            }
            else
                invokeAll(new Resetter(g, lo, mid), new Resetter(g, mid, hi));
        }
    }
}
