/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.*;

/**
 * A computation that is broken into a series of task executions, each
 * separated by a Phaser arrival.  Concrete subclasses must
 * define method <tt>compute</tt>, that performs the action occurring
 * at each step of the barrier.  Upon invocation of this task, the
 * <tt>compute</tt> method is repeatedly invoked until the barrier
 * <tt>isTerminated</tt> or until its execution throws an exception.
 *
 * <p> <b>Sample Usage.</b> Here is a sketch of a set of CyclicActions
 * that each perform 500 iterations of an imagined image smoothing
 * operation. Note that the aggregate ImageSmoother task itself is not
 * a CyclicTask.
 *
 * <pre>
 * class ImageSmoother extends RecursiveAction {
 *   protected void compute() {
 *     Phaser b = new Phaser() {
 *       protected boolean onAdvance(int cycle, int registeredParties) {
 *          return registeredParties &lt;= 0 || cycle &gt;= 500;
 *       }
 *     }
 *     int n = pool.getParallelismLevel();
 *     CyclicAction[] actions = new CyclicAction[n];
 *     for (int i = 0; i &lt; n; ++i) {
 *       action[i] = new CyclicAction(b) {
 *         protected void compute() {
 *           smoothImagePart(i);
 *         }
 *       }
 *     }
 *     invokeAll(actions);
 *   }
 * }
 * </pre>
 */
public abstract class CyclicAction extends ForkJoinTask<Void> {
    final Phaser barrier;
    boolean deregistered;
    int lastArrived;

    /**
     * Constructs a new CyclicAction using the supplied barrier,
     * registering for this barrier upon construction.
     * @param barrier the barrier
     */
    public CyclicAction(Phaser barrier) {
        this.barrier = barrier;
        lastArrived = barrier.register() - 1;
    }

    /**
     * The computation performed by this task on each cycle of the
     * barrier.  While you must define this method, you should not in
     * general call it directly.
     */
    protected abstract void step();

    /**
     * Returns the barrier
     */
    public final Phaser getBarrier() {
        return barrier;
    }

    /**
     * Returns the current cycle of the barrier
     */
    public final int getCycle() {
        return barrier.getPhase();
    }

    public final Void getRawResult() { return null; }
    protected final void setRawResult(Void mustBeNull) { }

    private void deregister() {
        if (!deregistered) {
            deregistered = true;
            barrier.arriveAndDeregister();
        }
    }

    protected final boolean exec() {
        Phaser b = barrier;
        if (!isDone()) {
            b.awaitAdvance(lastArrived);
            if (b.getPhase() >= 0) {
                try {
                    step();
                } catch (Throwable rex) {
                    deregister();
                    completeExceptionally(rex);
                    return false;
                }
                if ((lastArrived = b.arrive()) >= 0) {
                    this.fork();
                    return false;
                }
            }
        }
        deregister();
        return true;
    }

}
