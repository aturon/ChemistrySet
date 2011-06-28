/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 *
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * AsyncActions that may be linked in parent-child relationships.
 *
 * <p> Upon construction, an LinkedAsyncAction may register as a
 * subtask of a given parent task. In this case, completion of this
 * task will propagate to its parent. If the parent's pending subtask
 * completion count becomes zero, it too will complete.
 * LinkedAsyncActions rarely use methods <tt>join</tt> or
 * <tt>invoke</tt> but instead propagate completion to parents
 * implicitly via <tt>complete</tt>.  While typical, it is not necessary
 * for each task to <tt>complete</tt> itself. For example, it is
 * possible to treat one subtask as a continuation of the current task
 * by not registering it on construction.  In this case, a
 * <tt>complete</tt> of the subtask will trigger <tt>complete</tt> of the
 * parent without the parent explicitly doing so.
 *
 * <p> In addition to supporting these different computation styles
 * compared to Recursive tasks, LinkedAsyncActions may have smaller
 * stack space footprints while executing, but may have greater
 * per-task overhead.
 *
 * <p> <b>Sample Usage.</b> Here is a sketch of an LinkedAsyncAction
 * that visits all of the nodes of a graph. The details of the graph's
 * Node and Edge classes are omitted, but we assume each node contains
 * an <tt>AtomicBoolean</tt> mark that starts out false. To execute
 * this, you would create a GraphVisitor for the root node with null
 * parent, and <tt>invoke</tt> in a ForkJoinPool. Upon return, all
 * reachable nodes will have been visited.
 *
 * <pre>
 * class GraphVisitor extends LinkedAsyncAction {
 *    final Node node;
 *    GraphVisitor(GraphVistor parent, Node node) {
 *      super(parent); this.node = node;
 *    }
 *    protected void compute() {
 *      if (node.mark.compareAndSet(false, true)) {
 *         for (Edge e : node.edges()) {
 *            Node dest = e.getDestination();
 *            if (!dest.mark.get())
 *               new GraphVisitor(this, dest).fork();
 *         }
 *         visit(node);
 *      }
 *      complete();
 *   }
 * }
 * </pre>
 *
 */
public abstract class LinkedAsyncAction extends ForkJoinTask<Void> {

    /**
     * The maximum number of pending children
     */
    private static int maxPending = 0xffff;

    private volatile int controlState;

    static final AtomicIntegerFieldUpdater<LinkedAsyncAction> controlStateUpdater =
        AtomicIntegerFieldUpdater.newUpdater(LinkedAsyncAction.class, "controlState");


    /**
     * Parent to notify on completion
     */
    private LinkedAsyncAction parent;

    /**
     * Creates a new action with no parent. (You can add a parent
     * later (but before forking) via <tt>reinitialize</tt>).
     */
    protected LinkedAsyncAction() {
    }

    /**
     * Creates a new action with the given parent. If the parent is
     * non-null, this tasks registers with the parent, in which case,
     * the parent task cannot complete until this task completes.
     * @param parent the parent task, or null if none
     */
    protected LinkedAsyncAction(LinkedAsyncAction parent) {
        this.parent = parent;
        if (parent != null)
            parent.incrementControlState();
    }

    /**
     * Creates a new action with the given parent, optionally
     * registering with the parent. If the parent is non-null and
     * <tt>register</tt> is true, this tasks registers with the
     * parent, in which case, the parent task cannot complete until
     * this task completes.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     */
    protected LinkedAsyncAction(LinkedAsyncAction parent, boolean register) {
        this.parent = parent;
        if (parent != null && register)
            parent.incrementControlState();
    }

    /**
     * Creates a new action with the given parent, optionally
     * registering with the parent, and setting the pending join count
     * to the given value. If the parent is non-null and
     * <tt>register</tt> is true, this tasks registers with the
     * parent, in which case, the parent task cannot complete until
     * this task completes. Setting the pending join count requires
     * care -- it is correct only if child tasks do not themselves
     * register.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     * @param pending the pending join count
     */
    protected LinkedAsyncAction(LinkedAsyncAction parent,
                          boolean register,
                          int pending) {
        this.parent = parent;
        setControlState(pending);
        if (parent != null && register)
            parent.incrementControlState();
    }

    public final Void getRawResult() { return null; }
    protected final void setRawResult(Void mustBeNull) { }

    /**
     * Overridable callback action triggered by <tt>complete</tt>.  Upon
     * invocation, all subtasks have completed.  After return, this
     * task <tt>isDone</tt> and is joinable by other tasks. The
     * default version of this method does nothing. But it may may be
     * overridden in subclasses to perform some action when this task
     * is about to complete.
     */
    protected void onCompletion() {
    }

    /**
     * Overridable callback action triggered by
     * <tt>completeExceptionally</tt>.  Upon invocation, this task has
     * aborted due to an exception (accessible via
     * <tt>getException</tt>). If this method returns <tt>true</tt>,
     * the exception propagates to the current task's
     * parent. Otherwise, normal completion is propagated.  The
     * default version of this method does nothing and returns
     * <tt>true</tt>.
     * @return true if this task's exception should be propagated to
     * this tasks parent.
     */
    protected boolean onException() {
        return true;
    }

    /** Basic per-task complete */
    private void completeThis() {
        complete(null);
    }

    /** Basic per-task completeExceptionally */
    private void completeThisExceptionally(Throwable ex) {
        super.completeExceptionally(ex);
    }

    /**
     * Completes this task. If the pending subtask completion count is
     * zero, invokes <tt>onCompletion</tt>, then causes this task to
     * be joinable (<tt>isDone</tt> becomes true), and then
     * recursively applies to this tasks's parent, if it exists. If an
     * exception is encountered in any <tt>onCompletion</tt>
     * invocation, that task and its ancestors
     * <tt>completeExceptionally</tt>.
     */
    public final void complete() {
        LinkedAsyncAction a = this;
        while (a != null && !a.isDone()) {
            int c = a.getControlState();
            if (c <= 0) {
                try {
                    a.onCompletion();
                    a.completeThis();
                } catch (Throwable rex) {
                    a.completeExceptionally(rex);
                    return;
                }
                a = a.parent;
            }
            else if (a.compareAndSetControlState(c, c-1))
                return;
        }
    }

    /**
     * Completes this task abnormally. Unless this task already
     * cancelled or aborted, upon invocation, this method invokes
     * <tt>onException</tt>, and then, depending on its return value,
     * completees parent (if one exists) exceptionally or normally.  To
     * avoid unbounded exception loops, this method aborts if an
     * exception is encountered in any <tt>onException</tt>
     * invocation.
     * @param ex the exception to throw when joining this task
     * @throws NullPointerException if ex is null
     * @throws Throwable if any invocation of
     * <tt>onException</tt> does so.
     */
    public final void completeExceptionally(Throwable ex) {
        LinkedAsyncAction a = this;
        while (!a.isCompletedAbnormally()) {
            a.completeThisExceptionally(ex);
            boolean up = a.onException(); // abort if this throws
            a = a.parent;
            if (a == null)
                break;
            if (!up) {
                a.complete();
                break;
            }
        }
    }

    /**
     * Returns this task's parent, or null if none.
     * @return this task's parent, or null if none.
     */
    public final LinkedAsyncAction getParent() {
        return parent;
    }

    /**
     * Returns the number of subtasks that have not yet completed.
     * @return the number of subtasks that have not yet completed.
     */
    public final int getPendingSubtaskCount() {
        return getControlState();
    }

    /**
     * Resets the internal bookkeeping state of this task, maintaining
     * the current parent but clearing pending joins.
     */
    public void reinitialize() {
        super.reinitialize();
    }

    /**
     * Resets the internal bookkeeping state of this task, maintaining
     * the current parent and setting pending joins to the given value.
     * @param pending the number of pending joins
     */
    public void reinitialize(int pending) {
        super.reinitialize();
        setControlState(pending);
    }

    /**
     * Reinitialize with the given parent, optionally registering.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     */
    public void reinitialize(LinkedAsyncAction parent, boolean register) {
        this.parent = parent;
        super.reinitialize();
        if (parent != null && register)
            parent.incrementControlState();
    }

    /**
     * Reinitialize with the given parent, optionally registering
     * and setting pending join count.
     * @param parent the parent task, or null if none
     * @param register true if parent must wait for this task
     * to complete before it completes
     * @param pending the pending join count
     */
    public void reinitialize(LinkedAsyncAction parent,
                             boolean register,
                             int pending) {
        this.parent = parent;
        super.reinitialize();
        setControlState(pending);
        if (parent != null && register)
            parent.incrementControlState();
    }

    /**
     * Gets the control state, which is initially zero, or negative if
     * this task has completed or cancelled. Once negative, the value
     * cannot be changed.
     * @return control state
     */
    protected final int getControlState() {
        return controlState;
    }

    /**
     * Atomically sets the control state to the given updated value if
     * the current value is and equal to the expected value.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful
     */
    protected final boolean compareAndSetControlState(int expect,
                                                      int update) {
        return controlStateUpdater.compareAndSet(this, expect, update);
    }

    /**
     * Sets the control state to the given value,
     * @param value the new value
     */
    protected final void setControlState(int value) {
        controlState = value;
    }

    /**
     * Increment the control state
     * @return true if successful
     */
    protected final void incrementControlState() {
        controlStateUpdater.incrementAndGet(this);
    }

    /**
     * Decrement the control state
     * @return true if successful
     */
    protected final void decrementControlState() {
        controlStateUpdater.decrementAndGet(this);
    }


}
