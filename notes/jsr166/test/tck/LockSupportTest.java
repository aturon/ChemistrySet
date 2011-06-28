/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class LockSupportTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(LockSupportTest.class);
    }

    /**
     * Returns the blocker object used by tests in this file.
     * Any old object will do; we'll return a convenient one.
     */
    static Object theBlocker() {
        return LockSupportTest.class;
    }

    enum ParkMethod {
        park() {
            void park() {
                LockSupport.park();
            }
            void park(long millis) {
                throw new UnsupportedOperationException();
            }
        },
        parkUntil() {
            void park(long millis) {
                LockSupport.parkUntil(deadline(millis));
            }
        },
        parkNanos() {
            void park(long millis) {
                LockSupport.parkNanos(MILLISECONDS.toNanos(millis));
            }
        },
        parkBlocker() {
            void park() {
                LockSupport.park(theBlocker());
            }
            void park(long millis) {
                throw new UnsupportedOperationException();
            }
        },
        parkUntilBlocker() {
            void park(long millis) {
                LockSupport.parkUntil(theBlocker(), deadline(millis));
            }
        },
        parkNanosBlocker() {
            void park(long millis) {
                LockSupport.parkNanos(theBlocker(),
                                      MILLISECONDS.toNanos(millis));
            }
        };

        void park() { park(2 * LONG_DELAY_MS); }
        abstract void park(long millis);

        /** Returns a deadline to use with parkUntil. */
        long deadline(long millis) {
            // beware of rounding
            return System.currentTimeMillis() + millis + 1;
        }
    }

    /**
     * park is released by subsequent unpark
     */
    public void testParkBeforeUnpark_park() {
        testParkBeforeUnpark(ParkMethod.park);
    }
    public void testParkBeforeUnpark_parkNanos() {
        testParkBeforeUnpark(ParkMethod.parkNanos);
    }
    public void testParkBeforeUnpark_parkUntil() {
        testParkBeforeUnpark(ParkMethod.parkUntil);
    }
    public void testParkBeforeUnpark_parkBlocker() {
        testParkBeforeUnpark(ParkMethod.parkBlocker);
    }
    public void testParkBeforeUnpark_parkNanosBlocker() {
        testParkBeforeUnpark(ParkMethod.parkNanosBlocker);
    }
    public void testParkBeforeUnpark_parkUntilBlocker() {
        testParkBeforeUnpark(ParkMethod.parkUntilBlocker);
    }
    public void testParkBeforeUnpark(final ParkMethod parkMethod) {
        final CountDownLatch pleaseUnpark = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseUnpark.countDown();
                parkMethod.park();
            }});

        await(pleaseUnpark);
        LockSupport.unpark(t);
        awaitTermination(t);
    }

    /**
     * park is released by preceding unpark
     */
    public void testParkAfterUnpark_park() {
        testParkAfterUnpark(ParkMethod.park);
    }
    public void testParkAfterUnpark_parkNanos() {
        testParkAfterUnpark(ParkMethod.parkNanos);
    }
    public void testParkAfterUnpark_parkUntil() {
        testParkAfterUnpark(ParkMethod.parkUntil);
    }
    public void testParkAfterUnpark_parkBlocker() {
        testParkAfterUnpark(ParkMethod.parkBlocker);
    }
    public void testParkAfterUnpark_parkNanosBlocker() {
        testParkAfterUnpark(ParkMethod.parkNanosBlocker);
    }
    public void testParkAfterUnpark_parkUntilBlocker() {
        testParkAfterUnpark(ParkMethod.parkUntilBlocker);
    }
    public void testParkAfterUnpark(final ParkMethod parkMethod) {
        final CountDownLatch pleaseUnpark = new CountDownLatch(1);
        final AtomicBoolean pleasePark = new AtomicBoolean(false);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseUnpark.countDown();
                while (!pleasePark.get())
                    Thread.yield();
                parkMethod.park();
            }});

        await(pleaseUnpark);
        LockSupport.unpark(t);
        pleasePark.set(true);
        awaitTermination(t);
    }

    /**
     * park is released by subsequent interrupt
     */
    public void testParkBeforeInterrupt_park() {
        testParkBeforeInterrupt(ParkMethod.park);
    }
    public void testParkBeforeInterrupt_parkNanos() {
        testParkBeforeInterrupt(ParkMethod.parkNanos);
    }
    public void testParkBeforeInterrupt_parkUntil() {
        testParkBeforeInterrupt(ParkMethod.parkUntil);
    }
    public void testParkBeforeInterrupt_parkBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkBlocker);
    }
    public void testParkBeforeInterrupt_parkNanosBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkNanosBlocker);
    }
    public void testParkBeforeInterrupt_parkUntilBlocker() {
        testParkBeforeInterrupt(ParkMethod.parkUntilBlocker);
    }
    public void testParkBeforeInterrupt(final ParkMethod parkMethod) {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                pleaseInterrupt.countDown();
                do {
                    parkMethod.park();
                    // park may return spuriously
                } while (! Thread.currentThread().isInterrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * park is released by preceding interrupt
     */
    public void testParkAfterInterrupt_park() {
        testParkAfterInterrupt(ParkMethod.park);
    }
    public void testParkAfterInterrupt_parkNanos() {
        testParkAfterInterrupt(ParkMethod.parkNanos);
    }
    public void testParkAfterInterrupt_parkUntil() {
        testParkAfterInterrupt(ParkMethod.parkUntil);
    }
    public void testParkAfterInterrupt_parkBlocker() {
        testParkAfterInterrupt(ParkMethod.parkBlocker);
    }
    public void testParkAfterInterrupt_parkNanosBlocker() {
        testParkAfterInterrupt(ParkMethod.parkNanosBlocker);
    }
    public void testParkAfterInterrupt_parkUntilBlocker() {
        testParkAfterInterrupt(ParkMethod.parkUntilBlocker);
    }
    public void testParkAfterInterrupt(final ParkMethod parkMethod) {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        final AtomicBoolean pleasePark = new AtomicBoolean(false);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                pleaseInterrupt.countDown();
                while (!pleasePark.get())
                    Thread.yield();
                assertTrue(Thread.currentThread().isInterrupted());
                parkMethod.park();
                assertTrue(Thread.currentThread().isInterrupted());
            }});

        await(pleaseInterrupt);
        t.interrupt();
        pleasePark.set(true);
        awaitTermination(t);
    }

    /**
     * timed park times out if not unparked
     */
    public void testParkTimesOut_parkNanos() {
        testParkTimesOut(ParkMethod.parkNanos);
    }
    public void testParkTimesOut_parkUntil() {
        testParkTimesOut(ParkMethod.parkUntil);
    }
    public void testParkTimesOut_parkNanosBlocker() {
        testParkTimesOut(ParkMethod.parkNanosBlocker);
    }
    public void testParkTimesOut_parkUntilBlocker() {
        testParkTimesOut(ParkMethod.parkUntilBlocker);
    }
    public void testParkTimesOut(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                for (;;) {
                    long startTime = System.nanoTime();
                    parkMethod.park(timeoutMillis());
                    // park may return spuriously
                    if (millisElapsedSince(startTime) >= timeoutMillis())
                        return;
                }
            }});

        awaitTermination(t);
    }

    /**
     * getBlocker(null) throws NullPointerException
     */
    public void testGetBlockerNull() {
        try {
            LockSupport.getBlocker(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getBlocker returns the blocker object passed to park
     */
    public void testGetBlocker_parkBlocker() {
        testGetBlocker(ParkMethod.parkBlocker);
    }
    public void testGetBlocker_parkNanosBlocker() {
        testGetBlocker(ParkMethod.parkNanosBlocker);
    }
    public void testGetBlocker_parkUntilBlocker() {
        testGetBlocker(ParkMethod.parkUntilBlocker);
    }
    public void testGetBlocker(final ParkMethod parkMethod) {
        final CountDownLatch started = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                Thread t = Thread.currentThread();
                started.countDown();
                do {
                    assertNull(LockSupport.getBlocker(t));
                    parkMethod.park();
                    assertNull(LockSupport.getBlocker(t));
                    // park may return spuriously
                } while (! Thread.currentThread().isInterrupted());
            }});

        long startTime = System.nanoTime();
        await(started);
        for (;;) {
            Object x = LockSupport.getBlocker(t);
            if (x == theBlocker()) { // success
                t.interrupt();
                awaitTermination(t);
                assertNull(LockSupport.getBlocker(t));
                return;
            } else {
                assertNull(x);  // ok
                if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                    fail("timed out");
                Thread.yield();
            }
        }
    }

    /**
     * timed park(0) returns immediately.
     *
     * Requires hotspot fix for:
     * 6763959 java.util.concurrent.locks.LockSupport.parkUntil(0) blocks forever
     * which is in jdk7-b118 and 6u25.
     */
    public void testPark0_parkNanos() {
        testPark0(ParkMethod.parkNanos);
    }
    public void testPark0_parkUntil() {
        testPark0(ParkMethod.parkUntil);
    }
    public void testPark0_parkNanosBlocker() {
        testPark0(ParkMethod.parkNanosBlocker);
    }
    public void testPark0_parkUntilBlocker() {
        testPark0(ParkMethod.parkUntilBlocker);
    }
    public void testPark0(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                parkMethod.park(0L);
            }});

        awaitTermination(t);
    }

    /**
     * timed park(Long.MIN_VALUE) returns immediately.
     */
    public void testParkNeg_parkNanos() {
        testParkNeg(ParkMethod.parkNanos);
    }
    public void testParkNeg_parkUntil() {
        testParkNeg(ParkMethod.parkUntil);
    }
    public void testParkNeg_parkNanosBlocker() {
        testParkNeg(ParkMethod.parkNanosBlocker);
    }
    public void testParkNeg_parkUntilBlocker() {
        testParkNeg(ParkMethod.parkUntilBlocker);
    }
    public void testParkNeg(final ParkMethod parkMethod) {
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                parkMethod.park(Long.MIN_VALUE);
            }});

        awaitTermination(t);
    }
}
