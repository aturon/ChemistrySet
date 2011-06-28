/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeoutException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ExchangerTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ExchangerTest.class);
    }

    /**
     * exchange exchanges objects across two threads
     */
    public void testExchange() {
        final Exchanger e = new Exchanger();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertSame(one, e.exchange(two));
                assertSame(two, e.exchange(one));
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertSame(two, e.exchange(one));
                assertSame(one, e.exchange(two));
            }});

        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * timed exchange exchanges objects across two threads
     */
    public void testTimedExchange() {
        final Exchanger e = new Exchanger();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                assertSame(one, e.exchange(two, LONG_DELAY_MS, MILLISECONDS));
                assertSame(two, e.exchange(one, LONG_DELAY_MS, MILLISECONDS));
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                assertSame(two, e.exchange(one, LONG_DELAY_MS, MILLISECONDS));
                assertSame(one, e.exchange(two, LONG_DELAY_MS, MILLISECONDS));
            }});

        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * interrupt during wait for exchange throws IE
     */
    public void testExchange_InterruptedException() {
        final Exchanger e = new Exchanger();
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                e.exchange(one);
            }});

        await(threadStarted);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * interrupt during wait for timed exchange throws IE
     */
    public void testTimedExchange_InterruptedException() {
        final Exchanger e = new Exchanger();
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                e.exchange(null, LONG_DELAY_MS, MILLISECONDS);
            }});

        await(threadStarted);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * timeout during wait for timed exchange throws TimeoutException
     */
    public void testExchange_TimeoutException() {
        final Exchanger e = new Exchanger();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                long startTime = System.nanoTime();
                try {
                    e.exchange(null, timeoutMillis(), MILLISECONDS);
                    shouldThrow();
                } catch (TimeoutException success) {}
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
            }});

        awaitTermination(t);
    }

    /**
     * If one exchanging thread is interrupted, another succeeds.
     */
    public void testReplacementAfterExchange() {
        final Exchanger e = new Exchanger();
        final CountDownLatch exchanged = new CountDownLatch(2);
        final CountDownLatch interrupted = new CountDownLatch(1);
        Thread t1 = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                assertSame(two, e.exchange(one));
                exchanged.countDown();
                e.exchange(two);
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertSame(one, e.exchange(two));
                exchanged.countDown();
                interrupted.await();
                assertSame(three, e.exchange(one));
            }});
        Thread t3 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                interrupted.await();
                assertSame(one, e.exchange(three));
            }});

        await(exchanged);
        t1.interrupt();
        awaitTermination(t1);
        interrupted.countDown();
        awaitTermination(t2);
        awaitTermination(t3);
    }

}
