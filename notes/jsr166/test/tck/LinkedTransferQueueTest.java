/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include John Vint
 */

import junit.framework.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedTransferQueue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@SuppressWarnings({"unchecked", "rawtypes"})
public class LinkedTransferQueueTest extends JSR166TestCase {

    public static class Generic extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new LinkedTransferQueue();
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return newTestSuite(LinkedTransferQueueTest.class,
                            new Generic().testSuite());
    }

    /**
     * Constructor builds new queue with size being zero and empty
     * being true
     */
    public void testConstructor1() {
        assertEquals(0, new LinkedTransferQueue().size());
        assertTrue(new LinkedTransferQueue().isEmpty());
    }

    /**
     * Initializing constructor with null collection throws
     * NullPointerException
     */
    public void testConstructor2() {
        try {
            new LinkedTransferQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws
     * NullPointerException
     */
    public void testConstructor3() {
        Collection<Integer> elements = Arrays.asList(new Integer[SIZE]);
        try {
            new LinkedTransferQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing constructor with a collection containing some null elements
     * throws NullPointerException
     */
    public void testConstructor4() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE-1; ++i)
            ints[i] = i;
        Collection<Integer> elements = Arrays.asList(ints);
        try {
            new LinkedTransferQueue(elements);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements of the collection it is initialized by
     */
    public void testConstructor5() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            ints[i] = i;
        }
        List intList = Arrays.asList(ints);
        LinkedTransferQueue q
            = new LinkedTransferQueue(intList);
        assertEquals(q.size(), intList.size());
        assertEquals(q.toString(), intList.toString());
        assertTrue(Arrays.equals(q.toArray(),
                                     intList.toArray()));
        assertTrue(Arrays.equals(q.toArray(new Object[0]),
                                 intList.toArray(new Object[0])));
        assertTrue(Arrays.equals(q.toArray(new Object[SIZE]),
                                 intList.toArray(new Object[SIZE])));
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(ints[i], q.poll());
        }
    }

    /**
     * remainingCapacity() always returns Integer.MAX_VALUE
     */
    public void testRemainingCapacity() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(SIZE - i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(i);
        }
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    public void testAddAllSelf() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with any null elements throws
     * NullPointerException after possibly adding some elements
     */
    public void testAddAll3() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE - 1; ++i) {
                ints[i] = i;
            }
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements, in traversal order, of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            ints[i] = i;
        }
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(ints[i], q.poll());
        }
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(q.size(), i);
            q.put(i);
            assertTrue(q.contains(i));
        }
    }

    /**
     * take retrieves elements in FIFO order
     */
    public void testTake() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.take());
        }
    }

    /**
     * take removes existing elements until empty, then blocks interruptibly
     */
    public void testBlockingTake() throws InterruptedException {
        final BlockingQueue q = populatedQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i) {
                    assertEquals(i, q.take());
                }

                Thread.currentThread().interrupt();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll());
        }
        assertNull(q.poll());
        checkEmpty(q);
    }

    /**
     * timed poll with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll(0, MILLISECONDS));
        }
        assertNull(q.poll(0, MILLISECONDS));
        checkEmpty(q);
    }

    /**
     * timed poll with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            long startTime = System.nanoTime();
            assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
        long startTime = System.nanoTime();
        assertNull(q.poll(timeoutMillis(), MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        checkEmpty(q);
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll() throws InterruptedException {
        final BlockingQueue<Integer> q = populatedQueue(SIZE);
        final CountDownLatch aboutToWait = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i) {
                    long t0 = System.nanoTime();
                    assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
                    assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
                }
                long t0 = System.nanoTime();
                aboutToWait.countDown();
                try {
                    q.poll(MEDIUM_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {
                    assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);
                }
            }});

        aboutToWait.await();
        waitForThreadToEnterWaitState(t, SMALL_DELAY_MS);
        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
        checkEmpty(q);
    }

    /**
     * timed poll after thread interrupted throws InterruptedException
     * instead of returning timeout status
     */
    public void testTimedPollAfterInterrupt() throws InterruptedException {
        final BlockingQueue<Integer> q = populatedQueue(SIZE);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.currentThread().interrupt();
                for (int i = 0; i < SIZE; ++i) {
                    long t0 = System.nanoTime();
                    assertEquals(i, (int) q.poll(LONG_DELAY_MS, MILLISECONDS));
                    assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
                }
                try {
                    q.poll(MEDIUM_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
            }});

        awaitTermination(t, MEDIUM_DELAY_MS);
        checkEmpty(q);
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.peek());
            assertEquals(i, (int) q.poll());
            assertTrue(q.peek() == null ||
                       i != (int) q.peek());
        }
        assertNull(q.peek());
        checkEmpty(q);
    }

    /**
     * element returns next element, or throws NoSuchElementException if empty
     */
    public void testElement() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.element());
            assertEquals(i, (int) q.poll());
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        checkEmpty(q);
    }

    /**
     * remove removes next element, or throws NoSuchElementException if empty
     */
    public void testRemove() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
        checkEmpty(q);
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 1; i < SIZE; i+=2) {
            assertTrue(q.contains(i));
            assertTrue(q.remove(i));
            assertFalse(q.contains(i));
            assertTrue(q.contains(i-1));
        }
        for (int i = 0; i < SIZE; i+=2) {
            assertTrue(q.contains(i));
            assertTrue(q.remove(i));
            assertFalse(q.contains(i));
            assertFalse(q.remove(i+1));
            assertFalse(q.contains(i+1));
        }
        checkEmpty(q);
    }

    /**
     * An add following remove(x) succeeds
     */
    public void testRemoveElementAndAdd() throws InterruptedException {
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.add(one));
        assertTrue(q.add(two));
        assertTrue(q.remove(one));
        assertTrue(q.remove(two));
        assertTrue(q.add(three));
        assertSame(q.take(), three);
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(i));
            assertEquals(i, (int) q.poll());
            assertFalse(q.contains(i));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        q.clear();
        checkEmpty(q);
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertTrue(q.contains(one));
        q.clear();
        checkEmpty(q);
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        LinkedTransferQueue<Integer> p = new LinkedTransferQueue<Integer>();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(i);
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true
     * if changed
     */
    public void testRetainAll() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        LinkedTransferQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0) {
                assertFalse(changed);
            } else {
                assertTrue(changed);
            }
            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true
     * if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            LinkedTransferQueue q = populatedQueue(SIZE);
            LinkedTransferQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                assertFalse(q.contains(p.remove()));
            }
        }
    }

    /**
     * toArray() contains all elements in FIFO order
     */
    public void testToArray() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Object[] o = q.toArray();
        for (int i = 0; i < o.length; i++) {
            assertSame(o[i], q.poll());
        }
    }

    /**
     * toArray(a) contains all elements in FIFO order
     */
    public void testToArray2() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        Integer[] array = q.toArray(ints);
        assertSame(ints, array);
        for (int i = 0; i < ints.length; i++) {
            assertSame(ints[i], q.poll());
        }
    }

    /**
     * toArray(incompatible array type) throws ArrayStoreException
     */
    public void testToArray1_BadArg() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Iterator it = q.iterator();
        int i = 0;
        while (it.hasNext()) {
            assertEquals(it.next(), i++);
        }
        assertEquals(i, SIZE);
    }

    /**
     * iterator.remove() removes current element
     */
    public void testIteratorRemove() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.add(two);
        q.add(one);
        q.add(three);

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertSame(it.next(), one);
        assertSame(it.next(), three);
        assertFalse(it.hasNext());
    }

    /**
     * iterator ordering is FIFO
     */
    public void testIteratorOrdering() {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        q.add(two);
        q.add(three);
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        int k = 0;
        for (Integer n : q) {
            assertEquals(++k, (int) n);
        }
        assertEquals(3, k);
    }

    /**
     * Modifications do not cause iterators to fail
     */
    public void testWeaklyConsistentIteration() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.add(one);
        q.add(two);
        q.add(three);
        for (Iterator it = q.iterator(); it.hasNext();) {
            q.remove();
            it.next();
        }
        assertEquals(0, q.size());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.contains(String.valueOf(i)));
        }
    }

    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                assertTrue(q.offer(one, LONG_DELAY_MS, MILLISECONDS));
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                assertSame(one, q.take());
                checkEmpty(q);
            }});

        joinPool(executor);
    }

    /**
     * timed poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertNull(q.poll());
                threadsStarted.await();
                assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                checkEmpty(q);
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                q.put(one);
            }});

        joinPool(executor);
    }

    /**
     * A deserialized serialized queue has same elements in same order
     */
    public void testSerialization() throws Exception {
        Queue x = populatedQueue(SIZE);
        Queue y = serialClone(x);

        assertTrue(x != y);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.remove(), y.remove());
        }
        assertTrue(y.isEmpty());
    }

    /**
     * drainTo(c) empties queue into another collection c
     */
    public void testDrainTo() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(l.get(i), i);
        }
        q.add(zero);
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(zero));
        assertTrue(q.contains(one));
        l.clear();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), 2);
        for (int i = 0; i < 2; ++i) {
            assertEquals(l.get(i), i);
        }
    }

    /**
     * drainTo(c) empties full queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final LinkedTransferQueue q = populatedQueue(SIZE);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                q.put(SIZE + 1);
            }});
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(l.get(i), i);
        }
        awaitTermination(t, MEDIUM_DELAY_MS);
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(c, n) empties first min(n, size) elements of queue into c
     */
    public void testDrainToN() {
        LinkedTransferQueue q = new LinkedTransferQueue();
        for (int i = 0; i < SIZE + 2; ++i) {
            for (int j = 0; j < SIZE; j++) {
                assertTrue(q.offer(j));
            }
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(l.size(), k);
            assertEquals(q.size(), SIZE - k);
            for (int j = 0; j < k; ++j) {
                assertEquals(l.get(j), j);
            }
            while (q.poll() != null)
                ;
        }
    }

    /**
     * timed poll() or take() increments the waiting consumer count;
     * offer(e) decrements the waiting consumer count
     */
    public void testWaitingConsumer() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertEquals(q.getWaitingConsumerCount(), 0);
        assertFalse(q.hasWaitingConsumer());
        final CountDownLatch threadStarted = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                assertSame(one, q.poll(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(q.getWaitingConsumerCount(), 0);
                assertFalse(q.hasWaitingConsumer());
            }});

        threadStarted.await();
        waitForThreadToEnterWaitState(t, SMALL_DELAY_MS);
        assertEquals(q.getWaitingConsumerCount(), 1);
        assertTrue(q.hasWaitingConsumer());

        assertTrue(q.offer(one));
        assertEquals(q.getWaitingConsumerCount(), 0);
        assertFalse(q.hasWaitingConsumer());

        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * transfer(null) throws NullPointerException
     */
    public void testTransfer1() throws InterruptedException {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.transfer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * transfer waits until a poll occurs. The transfered element
     * is returned by this associated poll.
     */
    public void testTransfer2() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();
        final CountDownLatch threadStarted = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                q.transfer(five);
                checkEmpty(q);
            }});

        threadStarted.await();
        waitForThreadToEnterWaitState(t, SMALL_DELAY_MS);
        assertEquals(1, q.size());
        assertSame(five, q.poll());
        checkEmpty(q);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * transfer waits until a poll occurs, and then transfers in fifo order
     */
    public void testTransfer3() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();

        Thread first = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                assertTrue(!q.contains(four));
                assertEquals(1, q.size());
            }});

        Thread interruptedThread = newStartedThread(
            new CheckedInterruptedRunnable() {
                public void realRun() throws InterruptedException {
                    while (q.isEmpty())
                        Thread.yield();
                    q.transfer(five);
                }});

        while (q.size() < 2)
            Thread.yield();
        assertEquals(2, q.size());
        assertSame(four, q.poll());
        first.join();
        assertEquals(1, q.size());
        interruptedThread.interrupt();
        interruptedThread.join();
        checkEmpty(q);
    }

    /**
     * transfer waits until a poll occurs, at which point the polling
     * thread returns the element
     */
    public void testTransfer4() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                assertFalse(q.contains(four));
                assertSame(three, q.poll());
            }});

        while (q.isEmpty())
            Thread.yield();
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertTrue(q.offer(three));
        assertSame(four, q.poll());
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * transfer waits until a take occurs. The transfered element
     * is returned by this associated take.
     */
    public void testTransfer5() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.transfer(four);
                checkEmpty(q);
            }});

        while (q.isEmpty())
            Thread.yield();
        assertFalse(q.isEmpty());
        assertEquals(1, q.size());
        assertSame(four, q.take());
        checkEmpty(q);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * tryTransfer(null) throws NullPointerException
     */
    public void testTryTransfer1() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            q.tryTransfer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * tryTransfer returns false and does not enqueue if there are no
     * consumers waiting to poll or take.
     */
    public void testTryTransfer2() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.tryTransfer(new Object()));
        assertFalse(q.hasWaitingConsumer());
        checkEmpty(q);
    }

    /**
     * If there is a consumer waiting in timed poll, tryTransfer
     * returns true while successfully transfering object.
     */
    public void testTryTransfer3() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                assertTrue(q.hasWaitingConsumer());
                checkEmpty(q);
                assertTrue(q.tryTransfer(hotPotato));
            }});

        assertSame(hotPotato, q.poll(MEDIUM_DELAY_MS, MILLISECONDS));
        checkEmpty(q);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * If there is a consumer waiting in take, tryTransfer returns
     * true while successfully transfering object.
     */
    public void testTryTransfer4() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                assertTrue(q.hasWaitingConsumer());
                checkEmpty(q);
                assertTrue(q.tryTransfer(hotPotato));
            }});

        assertSame(q.take(), hotPotato);
        checkEmpty(q);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * tryTransfer blocks interruptibly if no takers
     */
    public void testTryTransfer5() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        assertTrue(q.isEmpty());

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.currentThread().interrupt();
                try {
                    q.tryTransfer(new Object(), LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                pleaseInterrupt.countDown();
                try {
                    q.tryTransfer(new Object(), LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * tryTransfer gives up after the timeout and returns false
     */
    public void testTryTransfer6() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long t0 = System.nanoTime();
                assertFalse(q.tryTransfer(new Object(),
                                          timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(t0) >= timeoutMillis());
                checkEmpty(q);
            }});

        awaitTermination(t);
        checkEmpty(q);
    }

    /**
     * tryTransfer waits for any elements previously in to be removed
     * before transfering to a poll or take
     */
    public void testTryTransfer7() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertTrue(q.tryTransfer(five, MEDIUM_DELAY_MS, MILLISECONDS));
                checkEmpty(q);
            }});

        while (q.size() != 2)
            Thread.yield();
        assertEquals(2, q.size());
        assertSame(four, q.poll());
        assertSame(five, q.poll());
        checkEmpty(q);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * tryTransfer attempts to enqueue into the queue and fails
     * returning false not enqueueing and the successive poll is null
     */
    public void testTryTransfer8() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));
        assertEquals(1, q.size());
        long t0 = System.nanoTime();
        assertFalse(q.tryTransfer(five, timeoutMillis(), MILLISECONDS));
        assertTrue(millisElapsedSince(t0) >= timeoutMillis());
        assertEquals(1, q.size());
        assertSame(four, q.poll());
        assertNull(q.poll());
        checkEmpty(q);
    }

    private LinkedTransferQueue<Integer> populatedQueue(int n) {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        checkEmpty(q);
        for (int i = 0; i < n; i++) {
            assertEquals(i, q.size());
            assertTrue(q.offer(i));
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        }
        assertFalse(q.isEmpty());
        return q;
    }
}
