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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DelayQueueTest extends JSR166TestCase {

    public static class Generic extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new DelayQueue();
        }
        protected PDelay makeElement(int i) {
            return new PDelay(i);
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return newTestSuite(DelayQueueTest.class,
                            new Generic().testSuite());
    }

    private static final int NOCAP = Integer.MAX_VALUE;

    /**
     * A delayed implementation for testing.
     * Most tests use Pseudodelays, where delays are all elapsed
     * (so, no blocking solely for delays) but are still ordered
     */
    static class PDelay implements Delayed {
        int pseudodelay;
        PDelay(int i) { pseudodelay = i; }
        public int compareTo(PDelay other) {
            int a = this.pseudodelay;
            int b = other.pseudodelay;
            return (a < b) ? -1 : (a > b) ? 1 : 0;
        }
        public int compareTo(Delayed y) {
            return compareTo((PDelay)y);
        }
        public boolean equals(Object other) {
            return (other instanceof PDelay) &&
                this.pseudodelay == ((PDelay)other).pseudodelay;
        }
        public long getDelay(TimeUnit ignore) {
            return Integer.MIN_VALUE + pseudodelay;
        }
        public String toString() {
            return String.valueOf(pseudodelay);
        }
    }

    /**
     * Delayed implementation that actually delays
     */
    static class NanoDelay implements Delayed {
        long trigger;
        NanoDelay(long i) {
            trigger = System.nanoTime() + i;
        }
        public int compareTo(NanoDelay y) {
            long i = trigger;
            long j = y.trigger;
            if (i < j) return -1;
            if (i > j) return 1;
            return 0;
        }

        public int compareTo(Delayed y) {
            return compareTo((NanoDelay)y);
        }

        public boolean equals(Object other) {
            return equals((NanoDelay)other);
        }
        public boolean equals(NanoDelay other) {
            return other.trigger == trigger;
        }

        public long getDelay(TimeUnit unit) {
            long n = trigger - System.nanoTime();
            return unit.convert(n, TimeUnit.NANOSECONDS);
        }

        public long getTriggerTime() {
            return trigger;
        }

        public String toString() {
            return String.valueOf(trigger);
        }
    }

    /**
     * Create a queue of given size containing consecutive
     * PDelays 0 ... n.
     */
    private DelayQueue<PDelay> populatedQueue(int n) {
        DelayQueue<PDelay> q = new DelayQueue<PDelay>();
        assertTrue(q.isEmpty());
        for (int i = n-1; i >= 0; i-=2)
            assertTrue(q.offer(new PDelay(i)));
        for (int i = (n & 1); i < n; i+=2)
            assertTrue(q.offer(new PDelay(i)));
        assertFalse(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
        assertEquals(n, q.size());
        return q;
    }

    /**
     * A new queue has unbounded capacity
     */
    public void testConstructor1() {
        assertEquals(NOCAP, new DelayQueue().remainingCapacity());
    }

    /**
     * Initializing from null Collection throws NPE
     */
    public void testConstructor3() {
        try {
            DelayQueue q = new DelayQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection of null elements throws NPE
     */
    public void testConstructor4() {
        try {
            PDelay[] ints = new PDelay[SIZE];
            DelayQueue q = new DelayQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Initializing from Collection with some null elements throws NPE
     */
    public void testConstructor5() {
        try {
            PDelay[] ints = new PDelay[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new PDelay(i);
            DelayQueue q = new DelayQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements of collection used to initialize
     */
    public void testConstructor6() {
        PDelay[] ints = new PDelay[SIZE];
        for (int i = 0; i < SIZE; ++i)
            ints[i] = new PDelay(i);
        DelayQueue q = new DelayQueue(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * isEmpty is true before add, false after
     */
    public void testEmpty() {
        DelayQueue q = new DelayQueue();
        assertTrue(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new PDelay(1));
        assertFalse(q.isEmpty());
        q.add(new PDelay(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    /**
     * remainingCapacity does not change when elements added or removed,
     * but size does
     */
    public void testRemainingCapacity() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(SIZE-i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new PDelay(i));
        }
    }

    /**
     * offer non-null succeeds
     */
    public void testOffer() {
        DelayQueue q = new DelayQueue();
        assertTrue(q.offer(new PDelay(0)));
        assertTrue(q.offer(new PDelay(1)));
    }

    /**
     * add succeeds
     */
    public void testAdd() {
        DelayQueue q = new DelayQueue();
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new PDelay(i)));
        }
    }

    /**
     * addAll(this) throws IAE
     */
    public void testAddAllSelf() {
        try {
            DelayQueue q = populatedQueue(SIZE);
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    public void testAddAll3() {
        try {
            DelayQueue q = new DelayQueue();
            PDelay[] ints = new PDelay[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new PDelay(i);
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Queue contains all elements of successful addAll
     */
    public void testAddAll5() {
        PDelay[] empty = new PDelay[0];
        PDelay[] ints = new PDelay[SIZE];
        for (int i = SIZE-1; i >= 0; --i)
            ints[i] = new PDelay(i);
        DelayQueue q = new DelayQueue();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], q.poll());
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() {
        DelayQueue q = new DelayQueue();
        for (int i = 0; i < SIZE; ++i) {
            PDelay I = new PDelay(i);
            q.put(I);
            assertTrue(q.contains(I));
        }
        assertEquals(SIZE, q.size());
    }

    /**
     * put doesn't block waiting for take
     */
    public void testPutWithTake() throws InterruptedException {
        final DelayQueue q = new DelayQueue();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                q.put(new PDelay(0));
                q.put(new PDelay(0));
                q.put(new PDelay(0));
                q.put(new PDelay(0));
            }});

        awaitTermination(t);
        assertEquals(4, q.size());
    }

    /**
     * timed offer does not time out
     */
    public void testTimedOffer() throws InterruptedException {
        final DelayQueue q = new DelayQueue();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(new PDelay(0));
                q.put(new PDelay(0));
                assertTrue(q.offer(new PDelay(0), SHORT_DELAY_MS, MILLISECONDS));
                assertTrue(q.offer(new PDelay(0), LONG_DELAY_MS, MILLISECONDS));
            }});

        awaitTermination(t);
    }

    /**
     * take retrieves elements in priority order
     */
    public void testTake() throws InterruptedException {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.take()));
        }
    }

    /**
     * Take removes existing elements until empty, then blocks interruptibly
     */
    public void testBlockingTake() throws InterruptedException {
        final DelayQueue q = populatedQueue(SIZE);
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                for (int i = 0; i < SIZE; ++i) {
                    assertEquals(new PDelay(i), ((PDelay)q.take()));
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
    public void testPoll() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.poll()));
        }
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() throws InterruptedException {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.poll(0, MILLISECONDS)));
        }
        assertNull(q.poll(0, MILLISECONDS));
    }

    /**
     * timed poll with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() throws InterruptedException {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            long startTime = System.nanoTime();
            assertEquals(new PDelay(i), ((PDelay)q.poll(LONG_DELAY_MS, MILLISECONDS)));
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
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                DelayQueue q = populatedQueue(SIZE);
                for (int i = 0; i < SIZE; ++i) {
                    assertEquals(new PDelay(i), ((PDelay)q.poll(SHORT_DELAY_MS, MILLISECONDS)));
                }

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

        await(pleaseInterrupt);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.peek()));
            assertEquals(new PDelay(i), ((PDelay)q.poll()));
            if (q.isEmpty())
                assertNull(q.peek());
            else
                assertFalse(new PDelay(i).equals(q.peek()));
        }
        assertNull(q.peek());
    }

    /**
     * element returns next element, or throws NSEE if empty
     */
    public void testElement() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.element()));
            q.poll();
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove removes next element, or throws NSEE if empty
     */
    public void testRemove() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.remove()));
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 1; i < SIZE; i+=2) {
            assertTrue(q.remove(new PDelay(i)));
        }
        for (int i = 0; i < SIZE; i+=2) {
            assertTrue(q.remove(new PDelay(i)));
            assertFalse(q.remove(new PDelay(i+1)));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        DelayQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new PDelay(i)));
            q.poll();
            assertFalse(q.contains(new PDelay(i)));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() {
        DelayQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(NOCAP, q.remainingCapacity());
        PDelay x = new PDelay(1);
        q.add(x);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(x));
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        DelayQueue q = populatedQueue(SIZE);
        DelayQueue p = new DelayQueue();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new PDelay(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true if changed
     */
    public void testRetainAll() {
        DelayQueue q = populatedQueue(SIZE);
        DelayQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(SIZE-i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            DelayQueue q = populatedQueue(SIZE);
            DelayQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE-i, q.size());
            for (int j = 0; j < i; ++j) {
                PDelay I = (PDelay)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    /**
     * toArray contains all elements
     */
    public void testToArray() throws InterruptedException {
        DelayQueue q = populatedQueue(SIZE);
        Object[] o = q.toArray();
        Arrays.sort(o);
        for (int i = 0; i < o.length; i++)
            assertSame(o[i], q.take());
    }

    /**
     * toArray(a) contains all elements
     */
    public void testToArray2() {
        DelayQueue<PDelay> q = populatedQueue(SIZE);
        PDelay[] ints = new PDelay[SIZE];
        PDelay[] array = q.toArray(ints);
        assertSame(ints, array);
        Arrays.sort(ints);
        for (int i = 0; i < ints.length; i++)
            assertSame(ints[i], q.remove());
    }

    /**
     * toArray(incompatible array type) throws ArrayStoreException
     */
    public void testToArray1_BadArg() {
        DelayQueue q = populatedQueue(SIZE);
        try {
            q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {}
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() {
        DelayQueue q = populatedQueue(SIZE);
        int i = 0;
        Iterator it = q.iterator();
        while (it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
    }

    /**
     * iterator.remove removes current element
     */
    public void testIteratorRemove() {
        final DelayQueue q = new DelayQueue();
        q.add(new PDelay(2));
        q.add(new PDelay(1));
        q.add(new PDelay(3));
        Iterator it = q.iterator();
        it.next();
        it.remove();
        it = q.iterator();
        assertEquals(it.next(), new PDelay(2));
        assertEquals(it.next(), new PDelay(3));
        assertFalse(it.hasNext());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        DelayQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (Object e : q)
            assertTrue(s.contains(e.toString()));
    }

    /**
     * timed poll transfers elements across Executor tasks
     */
    public void testPollInExecutor() {
        final DelayQueue q = new DelayQueue();
        final CheckedBarrier threadsStarted = new CheckedBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertNull(q.poll());
                threadsStarted.await();
                assertTrue(null != q.poll(LONG_DELAY_MS, MILLISECONDS));
                checkEmpty(q);
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadsStarted.await();
                q.put(new PDelay(1));
            }});

        joinPool(executor);
    }

    /**
     * Delayed actions do not occur until their delay elapses
     */
    public void testDelay() throws InterruptedException {
        DelayQueue<NanoDelay> q = new DelayQueue<NanoDelay>();
        for (int i = 0; i < SIZE; ++i)
            q.add(new NanoDelay(1000000L * (SIZE - i)));

        long last = 0;
        for (int i = 0; i < SIZE; ++i) {
            NanoDelay e = q.take();
            long tt = e.getTriggerTime();
            assertTrue(System.nanoTime() - tt >= 0);
            if (i != 0)
                assertTrue(tt >= last);
            last = tt;
        }
        assertTrue(q.isEmpty());
    }

    /**
     * peek of a non-empty queue returns non-null even if not expired
     */
    public void testPeekDelayed() {
        DelayQueue q = new DelayQueue();
        q.add(new NanoDelay(Long.MAX_VALUE));
        assertNotNull(q.peek());
    }

    /**
     * poll of a non-empty queue returns null if no expired elements.
     */
    public void testPollDelayed() {
        DelayQueue q = new DelayQueue();
        q.add(new NanoDelay(Long.MAX_VALUE));
        assertNull(q.poll());
    }

    /**
     * timed poll of a non-empty queue returns null if no expired elements.
     */
    public void testTimedPollDelayed() throws InterruptedException {
        DelayQueue q = new DelayQueue();
        q.add(new NanoDelay(LONG_DELAY_MS * 1000000L));
        assertNull(q.poll(timeoutMillis(), MILLISECONDS));
    }

    /**
     * drainTo(c) empties queue into another collection c
     */
    public void testDrainTo() {
        DelayQueue q = new DelayQueue();
        PDelay[] elems = new PDelay[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            elems[i] = new PDelay(i);
            q.add(elems[i]);
        }
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(l.get(i), elems[i]);
        q.add(elems[0]);
        q.add(elems[1]);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(elems[0]));
        assertTrue(q.contains(elems[1]));
        l.clear();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), 2);
        for (int i = 0; i < 2; ++i)
            assertEquals(l.get(i), elems[i]);
    }

    /**
     * drainTo empties queue
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final DelayQueue q = populatedQueue(SIZE);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                q.put(new PDelay(SIZE+1));
            }});

        t.start();
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        t.join();
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(c, n) empties first min(n, size) elements of queue into c
     */
    public void testDrainToN() {
        for (int i = 0; i < SIZE + 2; ++i) {
            DelayQueue q = populatedQueue(SIZE);
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(q.size(), SIZE-k);
            assertEquals(l.size(), k);
        }
    }

}
