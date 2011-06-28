/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class CyclicBarrierTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(CyclicBarrierTest.class);
    }

    private volatile int countAction;
    private class MyAction implements Runnable {
        public void run() { ++countAction; }
    }

    /**
     * Spin-waits till the number of waiters == numberOfWaiters.
     */
    void awaitNumberWaiting(CyclicBarrier barrier, int numberOfWaiters) {
        long startTime = System.nanoTime();
        while (barrier.getNumberWaiting() != numberOfWaiters) {
            if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                fail("timed out");
            Thread.yield();
        }
    }

    /**
     * Creating with negative parties throws IAE
     */
    public void testConstructor1() {
        try {
            new CyclicBarrier(-1, (Runnable)null);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Creating with negative parties and no action throws IAE
     */
    public void testConstructor2() {
        try {
            new CyclicBarrier(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getParties returns the number of parties given in constructor
     */
    public void testGetParties() {
        CyclicBarrier b = new CyclicBarrier(2);
        assertEquals(2, b.getParties());
        assertEquals(0, b.getNumberWaiting());
    }

    /**
     * A 1-party barrier triggers after single await
     */
    public void testSingleParty() throws Exception {
        CyclicBarrier b = new CyclicBarrier(1);
        assertEquals(1, b.getParties());
        assertEquals(0, b.getNumberWaiting());
        b.await();
        b.await();
        assertEquals(0, b.getNumberWaiting());
    }

    /**
     * The supplied barrier action is run at barrier
     */
    public void testBarrierAction() throws Exception {
        countAction = 0;
        CyclicBarrier b = new CyclicBarrier(1, new MyAction());
        assertEquals(1, b.getParties());
        assertEquals(0, b.getNumberWaiting());
        b.await();
        b.await();
        assertEquals(0, b.getNumberWaiting());
        assertEquals(countAction, 2);
    }

    /**
     * A 2-party/thread barrier triggers after both threads invoke await
     */
    public void testTwoParties() throws Exception {
        final CyclicBarrier b = new CyclicBarrier(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                b.await();
                b.await();
                b.await();
                b.await();
            }});

        b.await();
        b.await();
        b.await();
        b.await();
        awaitTermination(t);
    }

    /**
     * An interruption in one party causes others waiting in await to
     * throw BrokenBarrierException
     */
    public void testAwait1_Interrupted_BrokenBarrier() {
        final CyclicBarrier c = new CyclicBarrier(3);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(2);
        Thread t1 = new ThreadShouldThrow(InterruptedException.class) {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                c.await();
            }};
        Thread t2 = new ThreadShouldThrow(BrokenBarrierException.class) {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                c.await();
            }};

        t1.start();
        t2.start();
        await(pleaseInterrupt);
        t1.interrupt();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * An interruption in one party causes others waiting in timed await to
     * throw BrokenBarrierException
     */
    public void testAwait2_Interrupted_BrokenBarrier() throws Exception {
        final CyclicBarrier c = new CyclicBarrier(3);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(2);
        Thread t1 = new ThreadShouldThrow(InterruptedException.class) {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                c.await(LONG_DELAY_MS, MILLISECONDS);
            }};
        Thread t2 = new ThreadShouldThrow(BrokenBarrierException.class) {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                c.await(LONG_DELAY_MS, MILLISECONDS);
            }};

        t1.start();
        t2.start();
        await(pleaseInterrupt);
        t1.interrupt();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A timeout in timed await throws TimeoutException
     */
    public void testAwait3_TimeoutException() throws InterruptedException {
        final CyclicBarrier c = new CyclicBarrier(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                long startTime = System.nanoTime();
                try {
                    c.await(timeoutMillis(), MILLISECONDS);
                    shouldThrow();
                } catch (TimeoutException success) {}
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            }});

        awaitTermination(t);
    }

    /**
     * A timeout in one party causes others waiting in timed await to
     * throw BrokenBarrierException
     */
    public void testAwait4_Timeout_BrokenBarrier() throws InterruptedException {
        final CyclicBarrier c = new CyclicBarrier(3);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                try {
                    c.await(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (BrokenBarrierException success) {}
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                awaitNumberWaiting(c, 1);
                long startTime = System.nanoTime();
                try {
                    c.await(timeoutMillis(), MILLISECONDS);
                    shouldThrow();
                } catch (TimeoutException success) {}
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            }});

        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A timeout in one party causes others waiting in await to
     * throw BrokenBarrierException
     */
    public void testAwait5_Timeout_BrokenBarrier() throws InterruptedException {
        final CyclicBarrier c = new CyclicBarrier(3);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                try {
                    c.await();
                    shouldThrow();
                } catch (BrokenBarrierException success) {}
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                awaitNumberWaiting(c, 1);
                long startTime = System.nanoTime();
                try {
                    c.await(timeoutMillis(), MILLISECONDS);
                    shouldThrow();
                } catch (TimeoutException success) {}
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            }});

        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A reset of an active barrier causes waiting threads to throw
     * BrokenBarrierException
     */
    public void testReset_BrokenBarrier() throws InterruptedException {
        final CyclicBarrier c = new CyclicBarrier(3);
        final CountDownLatch pleaseReset = new CountDownLatch(2);
        Thread t1 = new ThreadShouldThrow(BrokenBarrierException.class) {
            public void realRun() throws Exception {
                pleaseReset.countDown();
                c.await();
            }};
        Thread t2 = new ThreadShouldThrow(BrokenBarrierException.class) {
            public void realRun() throws Exception {
                pleaseReset.countDown();
                c.await();
            }};

        t1.start();
        t2.start();
        await(pleaseReset);

        awaitNumberWaiting(c, 2);
        c.reset();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A reset before threads enter barrier does not throw
     * BrokenBarrierException
     */
    public void testReset_NoBrokenBarrier() throws Exception {
        final CyclicBarrier c = new CyclicBarrier(3);
        c.reset();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                c.await();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                c.await();
            }});

        c.await();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * All threads block while a barrier is broken.
     */
    public void testReset_Leakage() throws InterruptedException {
        final CyclicBarrier c = new CyclicBarrier(2);
        final AtomicBoolean done = new AtomicBoolean();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                while (!done.get()) {
                    try {
                        while (c.isBroken())
                            c.reset();

                        c.await();
                        shouldThrow();
                    }
                    catch (BrokenBarrierException ok) {}
                    catch (InterruptedException ok) {}
                }}});

        for (int i = 0; i < 4; i++) {
            delay(timeoutMillis());
            t.interrupt();
        }
        done.set(true);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * Reset of a non-broken barrier does not break barrier
     */
    public void testResetWithoutBreakage() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        for (int i = 0; i < 3; i++) {
            final CyclicBarrier start = new CyclicBarrier(3);
            Thread t1 = newStartedThread(new CheckedRunnable() {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }});

            Thread t2 = newStartedThread(new CheckedRunnable() {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }});

            start.await();
            barrier.await();
            awaitTermination(t1);
            awaitTermination(t2);
            assertFalse(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
            if (i == 1) barrier.reset();
            assertFalse(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
        }
    }

    /**
     * Reset of a barrier after interruption reinitializes it.
     */
    public void testResetAfterInterrupt() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        for (int i = 0; i < 2; i++) {
            final CyclicBarrier start = new CyclicBarrier(3);
            Thread t1 = new ThreadShouldThrow(InterruptedException.class) {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }};

            Thread t2 = new ThreadShouldThrow(BrokenBarrierException.class) {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }};

            t1.start();
            t2.start();
            start.await();
            t1.interrupt();
            awaitTermination(t1);
            awaitTermination(t2);
            assertTrue(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
            barrier.reset();
            assertFalse(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
        }
    }

    /**
     * Reset of a barrier after timeout reinitializes it.
     */
    public void testResetAfterTimeout() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(3);
        for (int i = 0; i < 2; i++) {
            assertEquals(0, barrier.getNumberWaiting());
            Thread t1 = newStartedThread(new CheckedRunnable() {
                public void realRun() throws Exception {
                    try {
                        barrier.await();
                        shouldThrow();
                    } catch (BrokenBarrierException success) {}
                }});
            Thread t2 = newStartedThread(new CheckedRunnable() {
                public void realRun() throws Exception {
                    awaitNumberWaiting(barrier, 1);
                    long startTime = System.nanoTime();
                    try {
                        barrier.await(timeoutMillis(), MILLISECONDS);
                        shouldThrow();
                    } catch (TimeoutException success) {}
                    assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                }});

            awaitTermination(t1);
            awaitTermination(t2);
            assertEquals(0, barrier.getNumberWaiting());
            assertTrue(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
            barrier.reset();
            assertFalse(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
        }
    }

    /**
     * Reset of a barrier after a failed command reinitializes it.
     */
    public void testResetAfterCommandException() throws Exception {
        final CyclicBarrier barrier =
            new CyclicBarrier(3, new Runnable() {
                    public void run() {
                        throw new NullPointerException(); }});
        for (int i = 0; i < 2; i++) {
            final CyclicBarrier start = new CyclicBarrier(3);
            Thread t1 = new ThreadShouldThrow(BrokenBarrierException.class) {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }};

            Thread t2 = new ThreadShouldThrow(BrokenBarrierException.class) {
                public void realRun() throws Exception {
                    start.await();
                    barrier.await();
                }};

            t1.start();
            t2.start();
            start.await();
            awaitNumberWaiting(barrier, 2);
            try {
                barrier.await();
                shouldThrow();
            } catch (NullPointerException success) {}
            awaitTermination(t1);
            awaitTermination(t2);
            assertTrue(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
            barrier.reset();
            assertFalse(barrier.isBroken());
            assertEquals(0, barrier.getNumberWaiting());
        }
    }
}
