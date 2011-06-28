/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtomicBooleanTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicBooleanTest.class);
    }

    /**
     * constructor initializes to given value
     */
    public void testConstructor() {
        assertTrue(new AtomicBoolean(true).get());
        assertFalse(new AtomicBoolean(false).get());
    }

    /**
     * default constructed initializes to false
     */
    public void testConstructor2() {
        AtomicBoolean ai = new AtomicBoolean();
        assertFalse(ai.get());
    }

    /**
     * get returns the last value set
     */
    public void testGetSet() {
        AtomicBoolean ai = new AtomicBoolean(true);
        assertTrue(ai.get());
        ai.set(false);
        assertFalse(ai.get());
        ai.set(true);
        assertTrue(ai.get());
    }

    /**
     * get returns the last value lazySet in same thread
     */
    public void testGetLazySet() {
        AtomicBoolean ai = new AtomicBoolean(true);
        assertTrue(ai.get());
        ai.lazySet(false);
        assertFalse(ai.get());
        ai.lazySet(true);
        assertTrue(ai.get());
    }

    /**
     * compareAndSet succeeds in changing value if equal to expected else fails
     */
    public void testCompareAndSet() {
        AtomicBoolean ai = new AtomicBoolean(true);
        assertTrue(ai.compareAndSet(true, false));
        assertFalse(ai.get());
        assertTrue(ai.compareAndSet(false, false));
        assertFalse(ai.get());
        assertFalse(ai.compareAndSet(true, false));
        assertFalse(ai.get());
        assertTrue(ai.compareAndSet(false, true));
        assertTrue(ai.get());
    }

    /**
     * compareAndSet in one thread enables another waiting for value
     * to succeed
     */
    public void testCompareAndSetInMultipleThreads() throws Exception {
        final AtomicBoolean ai = new AtomicBoolean(true);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                while (!ai.compareAndSet(false, true)) Thread.yield();
            }});

        t.start();
        assertTrue(ai.compareAndSet(true, false));
        t.join(LONG_DELAY_MS);
        assertFalse(t.isAlive());
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSet() {
        AtomicBoolean ai = new AtomicBoolean(true);
        while (!ai.weakCompareAndSet(true, false));
        assertFalse(ai.get());
        while (!ai.weakCompareAndSet(false, false));
        assertFalse(ai.get());
        while (!ai.weakCompareAndSet(false, true));
        assertTrue(ai.get());
    }

    /**
     * getAndSet returns previous value and sets to given value
     */
    public void testGetAndSet() {
        AtomicBoolean ai = new AtomicBoolean(true);
        assertEquals(true, ai.getAndSet(false));
        assertEquals(false, ai.getAndSet(false));
        assertEquals(false, ai.getAndSet(true));
        assertTrue(ai.get());
    }

    /**
     * a deserialized serialized atomic holds same value
     */
    public void testSerialization() throws Exception {
        AtomicBoolean x = new AtomicBoolean();
        AtomicBoolean y = serialClone(x);
        x.set(true);
        AtomicBoolean z = serialClone(x);
        assertTrue(x.get());
        assertFalse(y.get());
        assertTrue(z.get());
    }

    /**
     * toString returns current value.
     */
    public void testToString() {
        AtomicBoolean ai = new AtomicBoolean();
        assertEquals(Boolean.toString(false), ai.toString());
        ai.set(true);
        assertEquals(Boolean.toString(true), ai.toString());
    }

}
