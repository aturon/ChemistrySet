/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AtomicIntegerFieldUpdaterTest extends JSR166TestCase {
    volatile int x = 0;
    int w;
    long z;
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicIntegerFieldUpdaterTest.class);
    }

    /**
     * Construction with non-existent field throws RuntimeException
     */
    public void testConstructor() {
        try {
            AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest>
                a = AtomicIntegerFieldUpdater.newUpdater
                (AtomicIntegerFieldUpdaterTest.class, "y");
            shouldThrow();
        } catch (RuntimeException success) {}
    }

    /**
     * construction with field not of given type throws RuntimeException
     */
    public void testConstructor2() {
        try {
            AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest>
                a = AtomicIntegerFieldUpdater.newUpdater
                (AtomicIntegerFieldUpdaterTest.class, "z");
            shouldThrow();
        } catch (RuntimeException success) {}
    }

    /**
     * construction with non-volatile field throws RuntimeException
     */
    public void testConstructor3() {
        try {
            AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest>
                a = AtomicIntegerFieldUpdater.newUpdater
                (AtomicIntegerFieldUpdaterTest.class, "w");
            shouldThrow();
        } catch (RuntimeException success) {}
    }

    /**
     * get returns the last value set or assigned
     */
    public void testGetSet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.get(this));
        a.set(this, 2);
        assertEquals(2, a.get(this));
        a.set(this, -3);
        assertEquals(-3, a.get(this));
    }

    /**
     * get returns the last value lazySet by same thread
     */
    public void testGetLazySet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.get(this));
        a.lazySet(this, 2);
        assertEquals(2, a.get(this));
        a.lazySet(this, -3);
        assertEquals(-3, a.get(this));
    }

    /**
     * compareAndSet succeeds in changing value if equal to expected else fails
     */
    public void testCompareAndSet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertTrue(a.compareAndSet(this, 1, 2));
        assertTrue(a.compareAndSet(this, 2, -4));
        assertEquals(-4, a.get(this));
        assertFalse(a.compareAndSet(this, -5, 7));
        assertEquals(-4, a.get(this));
        assertTrue(a.compareAndSet(this, -4, 7));
        assertEquals(7, a.get(this));
    }

    /**
     * compareAndSet in one thread enables another waiting for value
     * to succeed
     */
    public void testCompareAndSetInMultipleThreads() throws Exception {
        x = 1;
        final AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest>a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }

        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                while (!a.compareAndSet(AtomicIntegerFieldUpdaterTest.this, 2, 3))
                    Thread.yield();
            }});

        t.start();
        assertTrue(a.compareAndSet(this, 1, 2));
        t.join(LONG_DELAY_MS);
        assertFalse(t.isAlive());
        assertEquals(3, a.get(this));
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        while (!a.weakCompareAndSet(this, 1, 2));
        while (!a.weakCompareAndSet(this, 2, -4));
        assertEquals(-4, a.get(this));
        while (!a.weakCompareAndSet(this, -4, 7));
        assertEquals(7, a.get(this));
    }

    /**
     * getAndSet returns previous value and sets to given value
     */
    public void testGetAndSet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.getAndSet(this, 0));
        assertEquals(0, a.getAndSet(this, -10));
        assertEquals(-10, a.getAndSet(this, 1));
    }

    /**
     * getAndAdd returns previous value and adds given value
     */
    public void testGetAndAdd() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.getAndAdd(this, 2));
        assertEquals(3, a.get(this));
        assertEquals(3, a.getAndAdd(this, -4));
        assertEquals(-1, a.get(this));
    }

    /**
     * getAndDecrement returns previous value and decrements
     */
    public void testGetAndDecrement() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.getAndDecrement(this));
        assertEquals(0, a.getAndDecrement(this));
        assertEquals(-1, a.getAndDecrement(this));
    }

    /**
     * getAndIncrement returns previous value and increments
     */
    public void testGetAndIncrement() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(1, a.getAndIncrement(this));
        assertEquals(2, a.get(this));
        a.set(this, -2);
        assertEquals(-2, a.getAndIncrement(this));
        assertEquals(-1, a.getAndIncrement(this));
        assertEquals(0, a.getAndIncrement(this));
        assertEquals(1, a.get(this));
    }

    /**
     * addAndGet adds given value to current, and returns current value
     */
    public void testAddAndGet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(3, a.addAndGet(this, 2));
        assertEquals(3, a.get(this));
        assertEquals(-1, a.addAndGet(this, -4));
        assertEquals(-1, a.get(this));
    }

    /**
     * decrementAndGet decrements and returns current value
     */
    public void testDecrementAndGet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(0, a.decrementAndGet(this));
        assertEquals(-1, a.decrementAndGet(this));
        assertEquals(-2, a.decrementAndGet(this));
        assertEquals(-2, a.get(this));
    }

    /**
     * incrementAndGet increments and returns current value
     */
    public void testIncrementAndGet() {
        AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdaterTest> a;
        try {
            a = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdaterTest.class, "x");
        } catch (RuntimeException ok) {
            return;
        }
        x = 1;
        assertEquals(2, a.incrementAndGet(this));
        assertEquals(2, a.get(this));
        a.set(this, -2);
        assertEquals(-1, a.incrementAndGet(this));
        assertEquals(0, a.incrementAndGet(this));
        assertEquals(1, a.incrementAndGet(this));
        assertEquals(1, a.get(this));
    }

}
