/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Test drainTo failing due to c.add throwing
 */

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class DrainToFails {
    final int CAPACITY = 10;
    final int SMALL = 2;

    void test(String[] args) throws Throwable {
        testDelayQueue(new DelayQueue());
        testDelayQueue(new ScheduledThreadPoolExecutor(1).getQueue());

        testUnbounded(new LinkedBlockingQueue());
        testUnbounded(new LinkedBlockingDeque());
        testUnbounded(new PriorityBlockingQueue());

        testBounded(new LinkedBlockingQueue(CAPACITY));
        testBounded(new LinkedBlockingDeque(CAPACITY));
        testBounded(new ArrayBlockingQueue(CAPACITY));
    }

    static class PDelay
        extends FutureTask<Void>
        implements Delayed, RunnableScheduledFuture<Void> {
        int pseudodelay;
        PDelay(int i) {
            super(new Runnable() { public void run() {}}, null);
            pseudodelay = i;
        }
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
        public boolean isPeriodic() { return false; }
    }

    void testDelayQueue(final BlockingQueue q) throws Throwable {
        System.err.println(q.getClass().getSimpleName());
        for (int i = 0; i < CAPACITY; i++)
            q.add(new PDelay(i));
        ArrayBlockingQueue q2 = new ArrayBlockingQueue(SMALL);
        try {
            q.drainTo(q2, SMALL + 3);
            fail("should throw");
        } catch (IllegalStateException success) {
            equal(SMALL, q2.size());
            equal(new PDelay(0), q2.poll());
            equal(new PDelay(1), q2.poll());
            check(q2.isEmpty());
            for (int i = SMALL; i < CAPACITY; i++)
                equal(new PDelay(i), q.poll());
            equal(0, q.size());
        }
    }

    void testUnbounded(final BlockingQueue q) throws Throwable {
        System.err.println(q.getClass().getSimpleName());
        for (int i = 0; i < CAPACITY; i++)
            q.add(i);
        ArrayBlockingQueue q2 = new ArrayBlockingQueue(SMALL);
        try {
            q.drainTo(q2, 7);
            fail("should throw");
        } catch (IllegalStateException success) {
            assertContentsInOrder(q2, 0, 1);
            q2.clear();
            equal(q.size(), CAPACITY - SMALL);
            equal(SMALL, q.peek());
        }

        try {
            q.drainTo(q2);
            fail("should throw");
        } catch (IllegalStateException success) {
            assertContentsInOrder(q2, 2, 3);
            equal(q.size(), CAPACITY - 2 * SMALL);
            for (int i = 2 * SMALL; i < CAPACITY; i++)
                equal(i, q.poll());
            equal(0, q.size());
        }
    }

    void testBounded(final BlockingQueue q) throws Throwable {
        System.err.println(q.getClass().getSimpleName());
        for (int i = 0; i < CAPACITY; i++)
            q.add(i);
        List<Thread> putters = new ArrayList<Thread>();
        for (int i = 0; i < 4; i++) {
            Thread putter = new Thread(putter(q, 42 + i));
            putters.add(putter);
            putter.setDaemon(true);
            putter.start();
        }
        ArrayBlockingQueue q2 = new ArrayBlockingQueue(SMALL);
        try {
            q.drainTo(q2, 7);
            fail("should throw");
        } catch (IllegalStateException success) {
            while (q.size() < CAPACITY)
                Thread.yield();
            assertContentsInOrder(q2, 0, 1);
            q2.clear();
        }

        try {
            q.drainTo(q2);
            fail("should throw");
        } catch (IllegalStateException success) {
            for (Thread putter : putters) {
                putter.join(2000L);
                check(! putter.isAlive());
            }
            assertContentsInOrder(q2, 2, 3);
            for (int i = 2 * SMALL; i < CAPACITY; i++)
                equal(i, q.poll());
            equal(4, q.size());
            check(q.contains(42));
            check(q.contains(43));
            check(q.contains(44));
            check(q.contains(45));
        }
    }

    Runnable putter(final BlockingQueue q, final int elt) {
        return new Runnable() {
            public void run() {
                try { q.put(elt); }
                catch (Throwable t) { unexpected(t); }}};
    }

    void assertContentsInOrder(Iterable it, Object... contents) {
        int i = 0;
        for (Object e : it)
            equal(contents[i++], e);
        equal(contents.length, i);
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new DrainToFails().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
