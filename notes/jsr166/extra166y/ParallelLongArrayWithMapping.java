/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package extra166y;
import jsr166y.*;
import static extra166y.Ops.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.Array;

/**
 * A prefix view of ParallelLongArray that causes operations to apply
 * to mappings of elements, not to the elements themselves.
 * Instances of this class may be constructed only via prefix
 * methods of ParallelLongArray or its other prefix classes.
 */
public abstract class ParallelLongArrayWithMapping<U> extends AbstractParallelAnyArray.LPap {
    ParallelLongArrayWithMapping
        (ForkJoinPool ex, int origin, int fence, long[] array) {
        super(ex, origin, fence, array);
    }

    /**
     * Applies the given procedure to mapped elements
     * @param procedure the procedure
     */
    public void apply(Procedure<? super U> procedure) {
        ex.invoke(new PAS.FJOApply(this, origin, fence, null, procedure));
    }

    /**
     * Returns reduction of mapped elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public U reduce(Reducer<U> reducer, U base) {
        PAS.FJOReduce f = new PAS.FJOReduce
            (this, origin, fence, null, reducer, base);
        ex.invoke(f);
        return (U)(f.result);
    }

    /**
     * Returns mapping of some element matching bound and filter
     * constraints, or null if none.
     * @return mapping of matching element, or null if none.
     */
    public U any() {
        int i = anyIndex();
        return (i < 0) ? null : (U)oget(i);
    }

    /**
     * Returns the minimum mapped element, or null if empty
     * @param comparator the comparator
     * @return minimum mapped element, or null if empty
     */
    public U min(Comparator<? super U> comparator) {
        return reduce(CommonOps.<U>minReducer(comparator), null);
    }

    /**
     * Returns the minimum mapped element, or null if empty,
     * assuming that all elements are Comparables
     * @return minimum mapped element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public U min() {
        return reduce((Reducer<U>)(CommonOps.castedMinReducer()), null);
    }

    /**
     * Returns the maximum mapped element, or null if empty
     * @param comparator the comparator
     * @return maximum mapped element, or null if empty
     */
    public U max(Comparator<? super U> comparator) {
        return reduce(CommonOps.<U>maxReducer(comparator), null);
    }

    /**
     * Returns the maximum mapped element, or null if empty
     * assuming that all elements are Comparables
     * @return maximum mapped element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public U max() {
        return reduce((Reducer<U>)(CommonOps.castedMaxReducer()), null);
    }

    /**
     * Returns summary statistics, using the given comparator
     * to locate minimum and maximum elements.
     * @param comparator the comparator to use for
     * locating minimum and maximum elements
     * @return the summary.
     */
    public ParallelArray.SummaryStatistics<U> summary
        (Comparator<? super U> comparator) {
        PAS.FJOStats f = new PAS.FJOStats
            (this, origin, fence, null, comparator);
        ex.invoke(f);
        return (ParallelArray.SummaryStatistics<U>)f;
    }

    /**
     * Returns summary statistics, assuming that all elements are
     * Comparables
     * @return the summary.
     */
    public ParallelArray.SummaryStatistics<U> summary() {
        return summary((Comparator<? super U>)(CommonOps.castedComparator()));
    }

    /**
     * Returns a new ParallelArray holding mapped elements
     * @return a new ParallelArray holding mapped elements
     */
    public ParallelArray<U> all() {
        return new ParallelArray<U>(ex, (U[])allObjects(null));
    }

    /**
     * Returns a new ParallelArray with the given element type holding
     * all elements
     * @param elementType the type of the elements
     * @return a new ParallelArray holding all elements
     */
    public ParallelArray<U> all(Class<? super U> elementType) {
        return new ParallelArray<U>(ex, (U[])allObjects(elementType));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op
     * applied to current op's results
     * @param op the op
     * @return operation prefix
     */
    public abstract <V> ParallelLongArrayWithMapping<V> withMapping
        (Op<? super U, ? extends V> op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op
     * applied to current op's results
     * @param op the op
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithLongMapping withMapping
        (ObjectToLong<? super U> op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op
     * applied to current op's results
     * @param op the op
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithDoubleMapping withMapping
        (ObjectToDouble<? super U> op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public <V,W,X> ParallelLongArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArrayWithMapping<X,V> other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArrayWithDoubleMapping other) {
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArrayWithLongMapping other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public <V,W> ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArrayWithMapping<W,V> other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArrayWithDoubleMapping other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArrayWithLongMapping other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public <V,W> ParallelLongArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArrayWithMapping<W,V> other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArrayWithDoubleMapping other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     * @throws IllegalArgumentException if other array is a
     * filtered view (all filters must precede all mappings).
     */
    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArrayWithLongMapping other) {
        if (other.hasFilter()) throw new IllegalArgumentException();
        return withIndexedMapping
            (AbstractParallelAnyArray.indexedMapper(combiner, other, origin));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mappings of this array using the given mapper that
     * accepts as arguments an element's current index and value
     * (as mapped by preceding mappings, if any), and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public abstract <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mappings of this array using the given mapper that
     * accepts as arguments an element's current index and value
     * (as mapped by preceding mappings, if any), and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mappings of this array using the given mapper that
     * accepts as arguments an element's current index and value
     * (as mapped by preceding mappings, if any), and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper);

    /**
     * Returns an Iterable view to sequentially step through mapped
     * elements also obeying bound and filter constraints, without
     * performing computations to evaluate them in parallel
     * @return the Iterable view
     */
    public Iterable<U> sequentially() {
        return new Sequentially<U>();
    }

}

