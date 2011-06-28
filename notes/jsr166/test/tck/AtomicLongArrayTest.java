/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

public class AtomicLongArrayTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicLongArrayTest.class);
    }

    /**
     * constructor creates array of given size with all elements zero
     */
    public void testConstructor() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(0, ai.get(i));
    }

    /**
     * constructor with null array throws NPE
     */
    public void testConstructor2NPE() {
        try {
            long[] a = null;
            AtomicLongArray ai = new AtomicLongArray(a);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * constructor with array is of same size and has all elements
     */
    public void testConstructor2() {
        long[] a = { 17L, 3L, -42L, 99L, -7L };
        AtomicLongArray ai = new AtomicLongArray(a);
        assertEquals(a.length, ai.length());
        for (int i = 0; i < a.length; ++i)
            assertEquals(a[i], ai.get(i));
    }

    /**
     * get and set for out of bound indices throw IndexOutOfBoundsException
     */
    public void testIndexing() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        try {
            ai.get(SIZE);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            ai.get(-1);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            ai.set(SIZE, 0);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            ai.set(-1, 0);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
    }

    /**
     * get returns the last value set at index
     */
    public void testGetSet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1, ai.get(i));
            ai.set(i, 2);
            assertEquals(2, ai.get(i));
            ai.set(i, -3);
            assertEquals(-3, ai.get(i));
        }
    }

    /**
     * get returns the last value lazySet at index by same thread
     */
    public void testGetLazySet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.lazySet(i, 1);
            assertEquals(1, ai.get(i));
            ai.lazySet(i, 2);
            assertEquals(2, ai.get(i));
            ai.lazySet(i, -3);
            assertEquals(-3, ai.get(i));
        }
    }

    /**
     * compareAndSet succeeds in changing value if equal to expected else fails
     */
    public void testCompareAndSet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertTrue(ai.compareAndSet(i, 1, 2));
            assertTrue(ai.compareAndSet(i, 2, -4));
            assertEquals(-4, ai.get(i));
            assertFalse(ai.compareAndSet(i, -5, 7));
            assertEquals(-4, ai.get(i));
            assertTrue(ai.compareAndSet(i, -4, 7));
            assertEquals(7, ai.get(i));
        }
    }

    /**
     * compareAndSet in one thread enables another waiting for value
     * to succeed
     */
    public void testCompareAndSetInMultipleThreads() throws InterruptedException {
        final AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                while (!a.compareAndSet(0, 2, 3))
                    Thread.yield();
            }});

        t.start();
        assertTrue(a.compareAndSet(0, 1, 2));
        t.join(LONG_DELAY_MS);
        assertFalse(t.isAlive());
        assertEquals(3, a.get(0));
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            while (!ai.weakCompareAndSet(i, 1, 2));
            while (!ai.weakCompareAndSet(i, 2, -4));
            assertEquals(-4, ai.get(i));
            while (!ai.weakCompareAndSet(i, -4, 7));
            assertEquals(7, ai.get(i));
        }
    }

    /**
     * getAndSet returns previous value and sets to given value at given index
     */
    public void testGetAndSet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1, ai.getAndSet(i, 0));
            assertEquals(0, ai.getAndSet(i, -10));
            assertEquals(-10, ai.getAndSet(i, 1));
        }
    }

    /**
     * getAndAdd returns previous value and adds given value
     */
    public void testGetAndAdd() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1, ai.getAndAdd(i, 2));
            assertEquals(3, ai.get(i));
            assertEquals(3, ai.getAndAdd(i, -4));
            assertEquals(-1, ai.get(i));
        }
    }

    /**
     * getAndDecrement returns previous value and decrements
     */
    public void testGetAndDecrement() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1, ai.getAndDecrement(i));
            assertEquals(0, ai.getAndDecrement(i));
            assertEquals(-1, ai.getAndDecrement(i));
        }
    }

    /**
     * getAndIncrement returns previous value and increments
     */
    public void testGetAndIncrement() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1, ai.getAndIncrement(i));
            assertEquals(2, ai.get(i));
            ai.set(i, -2);
            assertEquals(-2, ai.getAndIncrement(i));
            assertEquals(-1, ai.getAndIncrement(i));
            assertEquals(0, ai.getAndIncrement(i));
            assertEquals(1, ai.get(i));
        }
    }

    /**
     * addAndGet adds given value to current, and returns current value
     */
    public void testAddAndGet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(3, ai.addAndGet(i, 2));
            assertEquals(3, ai.get(i));
            assertEquals(-1, ai.addAndGet(i, -4));
            assertEquals(-1, ai.get(i));
        }
    }

    /**
     * decrementAndGet decrements and returns current value
     */
    public void testDecrementAndGet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(0, ai.decrementAndGet(i));
            assertEquals(-1, ai.decrementAndGet(i));
            assertEquals(-2, ai.decrementAndGet(i));
            assertEquals(-2, ai.get(i));
        }
    }

    /**
     * incrementAndGet increments and returns current value
     */
    public void testIncrementAndGet() {
        AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(2, ai.incrementAndGet(i));
            assertEquals(2, ai.get(i));
            ai.set(i, -2);
            assertEquals(-1, ai.incrementAndGet(i));
            assertEquals(0, ai.incrementAndGet(i));
            assertEquals(1, ai.incrementAndGet(i));
            assertEquals(1, ai.get(i));
        }
    }

    static final long COUNTDOWN = 100000;

    class Counter extends CheckedRunnable {
        final AtomicLongArray ai;
        volatile long counts;
        Counter(AtomicLongArray a) { ai = a; }
        public void realRun() {
            for (;;) {
                boolean done = true;
                for (int i = 0; i < ai.length(); ++i) {
                    long v = ai.get(i);
                    assertTrue(v >= 0);
                    if (v != 0) {
                        done = false;
                        if (ai.compareAndSet(i, v, v-1))
                            ++counts;
                    }
                }
                if (done)
                    break;
            }
        }
    }

    /**
     * Multiple threads using same array of counters successfully
     * update a number of times equal to total count
     */
    public void testCountingInMultipleThreads() throws InterruptedException {
        final AtomicLongArray ai = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            ai.set(i, COUNTDOWN);
        Counter c1 = new Counter(ai);
        Counter c2 = new Counter(ai);
        Thread t1 = new Thread(c1);
        Thread t2 = new Thread(c2);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(c1.counts+c2.counts, SIZE * COUNTDOWN);
    }

    /**
     * a deserialized serialized array holds same values
     */
    public void testSerialization() throws Exception {
        AtomicLongArray x = new AtomicLongArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            x.set(i, -i);
        AtomicLongArray y = serialClone(x);
        assertTrue(x != y);
        assertEquals(x.length(), y.length());
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(x.get(i), y.get(i));
        }
    }

    /**
     * toString returns current value.
     */
    public void testToString() {
        long[] a = { 17, 3, -42, 99, -7 };
        AtomicLongArray ai = new AtomicLongArray(a);
        assertEquals(Arrays.toString(a), ai.toString());
    }

}
