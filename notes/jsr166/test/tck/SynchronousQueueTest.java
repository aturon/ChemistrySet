/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class SynchronousQueueTest extends JSR166TestCase {

    public static class Fair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new SynchronousQueue(true);
        }
    }

    public static class NonFair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new SynchronousQueue(false);
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return newTestSuite(SynchronousQueueTest.class,
                            new Fair().testSuite(),
                            new NonFair().testSuite());
    }

    /**
     * Any SynchronousQueue is both empty and full
     */
    public void testEmptyFull()      { testEmptyFull(false); }
    public void testEmptyFull_fair() { testEmptyFull(true); }
    public void testEmptyFull(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(zero));
    }

    /**
     * offer fails if no active taker
     */
    public void testOffer()      { testOffer(false); }
    public void testOffer_fair() { testOffer(true); }
    public void testOffer(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        assertFalse(q.offer(one));
    }

    /**
     * add throws IllegalStateException if no active taker
     */
    public void testAdd()      { testAdd(false); }
    public void testAdd_fair() { testAdd(true); }
    public void testAdd(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        assertEquals(0, q.remainingCapacity());
        try {
            q.add(one);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    public void testAddAll_self()      { testAddAll_self(false); }
    public void testAddAll_self_fair() { testAddAll_self(true); }
    public void testAddAll_self(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll throws ISE if no active taker
     */
    public void testAddAll_ISE()      { testAddAll_ISE(false); }
    public void testAddAll_ISE_fair() { testAddAll_ISE(true); }
    public void testAddAll_ISE(boolean fair) {
        SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] ints = new Integer[1];
        for (int i = 0; i < ints.length; i++)
            ints[i] = i;
        Collection<Integer> coll = Arrays.asList(ints);
        try {
            q.addAll(coll);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * put blocks interruptibly if no active taker
     */
    public void testBlockingPut()      { testBlockingPut(false); }
    public void testBlockingPut_fair() { testBlockingPut(true); }
    public void testBlockingPut(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.currentThread().interrupt();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        assertEquals(0, q.remainingCapacity());
    }

    /**
     * put blocks interruptibly waiting for take
     */
    public void testPutWithTake()      { testPutWithTake(false); }
    public void testPutWithTake_fair() { testPutWithTake(true); }
    public void testPutWithTake(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseTake = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                pleaseTake.countDown();
                q.put(one);

                pleaseInterrupt.countDown();
                try {
                    q.put(99);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseTake);
        assertEquals(q.remainingCapacity(), 0);
        try { assertSame(one, q.take()); }
        catch (InterruptedException e) { threadUnexpectedException(e); }

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        assertEquals(q.remainingCapacity(), 0);
    }

    /**
     * timed offer times out if elements not taken
     */
    public void testTimedOffer()      { testTimedOffer(false); }
    public void testTimedOffer_fair() { testTimedOffer(true); }
    public void testTimedOffer(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertFalse(q.offer(new Object(), timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
                pleaseInterrupt.countDown();
                try {
                    q.offer(new Object(), 2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * poll return null if no active putter
     */
    public void testPoll()      { testPoll(false); }
    public void testPoll_fair() { testPoll(true); }
    public void testPoll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout times out if no active putter
     */
    public void testTimedPoll0()      { testTimedPoll0(false); }
    public void testTimedPoll0_fair() { testTimedPoll0(true); }
    public void testTimedPoll0(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try { assertNull(q.poll(0, MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
    }

    /**
     * timed poll with nonzero timeout times out if no active putter
     */
    public void testTimedPoll()      { testTimedPoll(false); }
    public void testTimedPoll_fair() { testTimedPoll(true); }
    public void testTimedPoll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        long startTime = System.nanoTime();
        try { assertNull(q.poll(timeoutMillis(), MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
    }

    /**
     * timed poll before a delayed offer times out, returning null;
     * after offer succeeds; on interruption throws
     */
    public void testTimedPollWithOffer()      { testTimedPollWithOffer(false); }
    public void testTimedPollWithOffer_fair() { testTimedPollWithOffer(true); }
    public void testTimedPollWithOffer(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CountDownLatch pleaseOffer = new CountDownLatch(1);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertNull(q.poll(timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());

                pleaseOffer.countDown();
                startTime = System.nanoTime();
                assertSame(zero, q.poll(LONG_DELAY_MS, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) < MEDIUM_DELAY_MS);

                Thread.currentThread().interrupt();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseOffer);
        long startTime = System.nanoTime();
        try { assertTrue(q.offer(zero, LONG_DELAY_MS, MILLISECONDS)); }
        catch (InterruptedException e) { threadUnexpectedException(e); }
        assertTrue(millisElapsedSince(startTime) < MEDIUM_DELAY_MS);

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * peek() returns null if no active putter
     */
    public void testPeek()      { testPeek(false); }
    public void testPeek_fair() { testPeek(true); }
    public void testPeek(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertNull(q.peek());
    }

    /**
     * element() throws NoSuchElementException if no active putter
     */
    public void testElement()      { testElement(false); }
    public void testElement_fair() { testElement(true); }
    public void testElement(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove() throws NoSuchElementException if no active putter
     */
    public void testRemove()      { testRemove(false); }
    public void testRemove_fair() { testRemove(true); }
    public void testRemove(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove(x) returns false
     */
    public void testRemoveElement()      { testRemoveElement(false); }
    public void testRemoveElement_fair() { testRemoveElement(true); }
    public void testRemoveElement(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertFalse(q.remove(zero));
        assertTrue(q.isEmpty());
    }

    /**
     * contains returns false
     */
    public void testContains()      { testContains(false); }
    public void testContains_fair() { testContains(true); }
    public void testContains(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        assertFalse(q.contains(zero));
    }

    /**
     * clear ensures isEmpty
     */
    public void testClear()      { testClear(false); }
    public void testClear_fair() { testClear(true); }
    public void testClear(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll returns false unless empty
     */
    public void testContainsAll()      { testContainsAll(false); }
    public void testContainsAll_fair() { testContainsAll(true); }
    public void testContainsAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertTrue(q.containsAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    /**
     * retainAll returns false
     */
    public void testRetainAll()      { testRetainAll(false); }
    public void testRetainAll_fair() { testRetainAll(true); }
    public void testRetainAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertFalse(q.retainAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.retainAll(Arrays.asList(ints)));
    }

    /**
     * removeAll returns false
     */
    public void testRemoveAll()      { testRemoveAll(false); }
    public void testRemoveAll_fair() { testRemoveAll(true); }
    public void testRemoveAll(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] empty = new Integer[0];
        assertFalse(q.removeAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    /**
     * toArray is empty
     */
    public void testToArray()      { testToArray(false); }
    public void testToArray_fair() { testToArray(true); }
    public void testToArray(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Object[] o = q.toArray();
        assertEquals(o.length, 0);
    }

    /**
     * toArray(a) is nulled at position 0
     */
    public void testToArray2()      { testToArray2(false); }
    public void testToArray2_fair() { testToArray2(true); }
    public void testToArray2(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Integer[] ints = new Integer[1];
        assertNull(ints[0]);
    }

    /**
     * toArray(null) throws NPE
     */
    public void testToArray_null()      { testToArray_null(false); }
    public void testToArray_null_fair() { testToArray_null(true); }
    public void testToArray_null(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        try {
            Object o[] = q.toArray(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * iterator does not traverse any elements
     */
    public void testIterator()      { testIterator(false); }
    public void testIterator_fair() { testIterator(true); }
    public void testIterator(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Iterator it = q.iterator();
        assertFalse(it.hasNext());
        try {
            Object x = it.next();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * iterator remove throws ISE
     */
    public void testIteratorRemove()      { testIteratorRemove(false); }
    public void testIteratorRemove_fair() { testIteratorRemove(true); }
    public void testIteratorRemove(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Iterator it = q.iterator();
        try {
            it.remove();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * toString returns a non-null string
     */
    public void testToString()      { testToString(false); }
    public void testToString_fair() { testToString(true); }
    public void testToString(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        String s = q.toString();
        assertNotNull(s);
    }

    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor()      { testOfferInExecutor(false); }
    public void testOfferInExecutor_fair() { testOfferInExecutor(true); }
    public void testOfferInExecutor(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertFalse(q.offer(one));
                threadsStarted.await();
                assertTrue(q.offer(one, LONG_DELAY_MS, MILLISECONDS));
                assertEquals(0, q.remainingCapacity());
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                assertSame(one, q.take());
            }});

        joinPool(executor);
    }

    /**
     * timed poll retrieves elements across Executor threads
     */
    public void testPollInExecutor()      { testPollInExecutor(false); }
    public void testPollInExecutor_fair() { testPollInExecutor(true); }
    public void testPollInExecutor(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertNull(q.poll());
                threadsStarted.await();
                assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                assertTrue(q.isEmpty());
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                q.put(one);
            }});

        joinPool(executor);
    }

    /**
     * a deserialized serialized queue is usable
     */
    public void testSerialization()      { testSerialization(false); }
    public void testSerialization_fair() { testSerialization(true); }
    public void testSerialization(boolean fair) {
        final SynchronousQueue x = new SynchronousQueue(fair);
        final SynchronousQueue y = serialClone(x);
        assertTrue(x != y);
        assertTrue(x.isEmpty());
        assertTrue(y.isEmpty());
    }

    /**
     * drainTo(c) of empty queue doesn't transfer elements
     */
    public void testDrainTo()      { testDrainTo(false); }
    public void testDrainTo_fair() { testDrainTo(true); }
    public void testDrainTo(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), 0);
    }

    /**
     * drainTo empties queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut()      { testDrainToWithActivePut(false); }
    public void testDrainToWithActivePut_fair() { testDrainToWithActivePut(true); }
    public void testDrainToWithActivePut(boolean fair) {
        final SynchronousQueue q = new SynchronousQueue(fair);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(one);
            }});

        ArrayList l = new ArrayList();
        long startTime = System.nanoTime();
        while (l.isEmpty()) {
            q.drainTo(l);
            if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                fail("timed out");
            Thread.yield();
        }
        assertTrue(l.size() == 1);
        assertSame(one, l.get(0));
        awaitTermination(t);
    }

    /**
     * drainTo(c, n) empties up to n elements of queue into c
     */
    public void testDrainToN() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(one);
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(two);
            }});

        ArrayList l = new ArrayList();
        delay(SHORT_DELAY_MS);
        q.drainTo(l, 1);
        assertEquals(1, l.size());
        q.drainTo(l, 1);
        assertEquals(2, l.size());
        assertTrue(l.contains(one));
        assertTrue(l.contains(two));
        awaitTermination(t1);
        awaitTermination(t2);
    }

}
