/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.*;

public class ReentrantLockTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ReentrantLockTest.class);
    }

    /**
     * A runnable calling lockInterruptibly
     */
    class InterruptibleLockRunnable extends CheckedRunnable {
        final ReentrantLock lock;
        InterruptibleLockRunnable(ReentrantLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.lockInterruptibly();
        }
    }

    /**
     * A runnable calling lockInterruptibly that expects to be
     * interrupted
     */
    class InterruptedLockRunnable extends CheckedInterruptedRunnable {
        final ReentrantLock lock;
        InterruptedLockRunnable(ReentrantLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.lockInterruptibly();
        }
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicReentrantLock extends ReentrantLock {
        PublicReentrantLock() { super(); }
        PublicReentrantLock(boolean fair) { super(fair); }
        public Thread getOwner() {
            return super.getOwner();
        }
        public Collection<Thread> getQueuedThreads() {
            return super.getQueuedThreads();
        }
        public Collection<Thread> getWaitingThreads(Condition c) {
            return super.getWaitingThreads(c);
        }
    }

    /**
     * Releases write lock, checking that it had a hold count of 1.
     */
    void releaseLock(PublicReentrantLock lock) {
        assertLockedByMoi(lock);
        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread());
        assertNotLocked(lock);
    }

    /**
     * Spin-waits until lock.hasQueuedThread(t) becomes true.
     */
    void waitForQueuedThread(PublicReentrantLock lock, Thread t) {
        long startTime = System.nanoTime();
        while (!lock.hasQueuedThread(t)) {
            if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                throw new AssertionFailedError("timed out");
            Thread.yield();
        }
        assertTrue(t.isAlive());
        assertTrue(lock.getOwner() != t);
    }

    /**
     * Checks that lock is not locked.
     */
    void assertNotLocked(PublicReentrantLock lock) {
        assertFalse(lock.isLocked());
        assertFalse(lock.isHeldByCurrentThread());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getHoldCount());
    }

    /**
     * Checks that lock is locked by the given thread.
     */
    void assertLockedBy(PublicReentrantLock lock, Thread t) {
        assertTrue(lock.isLocked());
        assertSame(t, lock.getOwner());
        assertEquals(t == Thread.currentThread(),
                     lock.isHeldByCurrentThread());
        assertEquals(t == Thread.currentThread(),
                     lock.getHoldCount() > 0);
    }

    /**
     * Checks that lock is locked by the current thread.
     */
    void assertLockedByMoi(PublicReentrantLock lock) {
        assertLockedBy(lock, Thread.currentThread());
    }

    /**
     * Checks that condition c has no waiters.
     */
    void assertHasNoWaiters(PublicReentrantLock lock, Condition c) {
        assertHasWaiters(lock, c, new Thread[] {});
    }

    /**
     * Checks that condition c has exactly the given waiter threads.
     */
    void assertHasWaiters(PublicReentrantLock lock, Condition c,
                          Thread... threads) {
        lock.lock();
        assertEquals(threads.length > 0, lock.hasWaiters(c));
        assertEquals(threads.length, lock.getWaitQueueLength(c));
        assertEquals(threads.length == 0, lock.getWaitingThreads(c).isEmpty());
        assertEquals(threads.length, lock.getWaitingThreads(c).size());
        assertEquals(new HashSet<Thread>(lock.getWaitingThreads(c)),
                     new HashSet<Thread>(Arrays.asList(threads)));
        lock.unlock();
    }

    enum AwaitMethod { await, awaitTimed, awaitNanos, awaitUntil };

    /**
     * Awaits condition using the specified AwaitMethod.
     */
    void await(Condition c, AwaitMethod awaitMethod)
            throws InterruptedException {
        long timeoutMillis = 2 * LONG_DELAY_MS;
        switch (awaitMethod) {
        case await:
            c.await();
            break;
        case awaitTimed:
            assertTrue(c.await(timeoutMillis, MILLISECONDS));
            break;
        case awaitNanos:
            long nanosTimeout = MILLISECONDS.toNanos(timeoutMillis);
            long nanosRemaining = c.awaitNanos(nanosTimeout);
            assertTrue(nanosRemaining > 0);
            break;
        case awaitUntil:
            assertTrue(c.awaitUntil(delayedDate(timeoutMillis)));
            break;
        }
    }

    /**
     * Constructor sets given fairness, and is in unlocked state
     */
    public void testConstructor() {
        PublicReentrantLock lock;

        lock = new PublicReentrantLock();
        assertFalse(lock.isFair());
        assertNotLocked(lock);

        lock = new PublicReentrantLock(true);
        assertTrue(lock.isFair());
        assertNotLocked(lock);

        lock = new PublicReentrantLock(false);
        assertFalse(lock.isFair());
        assertNotLocked(lock);
    }

    /**
     * locking an unlocked lock succeeds
     */
    public void testLock()      { testLock(false); }
    public void testLock_fair() { testLock(true); }
    public void testLock(boolean fair) {
        PublicReentrantLock lock = new PublicReentrantLock(fair);
        lock.lock();
        assertLockedByMoi(lock);
        releaseLock(lock);
    }

    /**
     * Unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testUnlock_IMSE()      { testUnlock_IMSE(false); }
    public void testUnlock_IMSE_fair() { testUnlock_IMSE(true); }
    public void testUnlock_IMSE(boolean fair) {
        ReentrantLock lock = new ReentrantLock(fair);
        try {
            lock.unlock();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * tryLock on an unlocked lock succeeds
     */
    public void testTryLock()      { testTryLock(false); }
    public void testTryLock_fair() { testTryLock(true); }
    public void testTryLock(boolean fair) {
        PublicReentrantLock lock = new PublicReentrantLock(fair);
        assertTrue(lock.tryLock());
        assertLockedByMoi(lock);
        assertTrue(lock.tryLock());
        assertLockedByMoi(lock);
        lock.unlock();
        releaseLock(lock);
    }

    /**
     * hasQueuedThreads reports whether there are waiting threads
     */
    public void testHasQueuedThreads()      { testHasQueuedThreads(false); }
    public void testHasQueuedThreads_fair() { testHasQueuedThreads(true); }
    public void testHasQueuedThreads(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThreads());
        lock.lock();
        assertFalse(lock.hasQueuedThreads());
        t1.start();
        waitForQueuedThread(lock, t1);
        assertTrue(lock.hasQueuedThreads());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertTrue(lock.hasQueuedThreads());
        t1.interrupt();
        awaitTermination(t1);
        assertTrue(lock.hasQueuedThreads());
        lock.unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThreads());
    }

    /**
     * getQueueLength reports number of waiting threads
     */
    public void testGetQueueLength()      { testGetQueueLength(false); }
    public void testGetQueueLength_fair() { testGetQueueLength(true); }
    public void testGetQueueLength(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertEquals(0, lock.getQueueLength());
        lock.lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueueLength());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueueLength());
        t1.interrupt();
        awaitTermination(t1);
        assertEquals(1, lock.getQueueLength());
        lock.unlock();
        awaitTermination(t2);
        assertEquals(0, lock.getQueueLength());
    }

    /**
     * hasQueuedThread(null) throws NPE
     */
    public void testHasQueuedThreadNPE()      { testHasQueuedThreadNPE(false); }
    public void testHasQueuedThreadNPE_fair() { testHasQueuedThreadNPE(true); }
    public void testHasQueuedThreadNPE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        try {
            lock.hasQueuedThread(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasQueuedThread reports whether a thread is queued
     */
    public void testHasQueuedThread()      { testHasQueuedThread(false); }
    public void testHasQueuedThread_fair() { testHasQueuedThread(true); }
    public void testHasQueuedThread(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        lock.lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertTrue(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        t2.start();
        waitForQueuedThread(lock, t2);
        assertTrue(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        t1.interrupt();
        awaitTermination(t1);
        assertFalse(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        lock.unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
    }

    /**
     * getQueuedThreads includes waiting threads
     */
    public void testGetQueuedThreads()      { testGetQueuedThreads(false); }
    public void testGetQueuedThreads_fair() { testGetQueuedThreads(true); }
    public void testGetQueuedThreads(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertTrue(lock.getQueuedThreads().isEmpty());
        lock.lock();
        assertTrue(lock.getQueuedThreads().isEmpty());
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueuedThreads().size());
        assertTrue(lock.getQueuedThreads().contains(t1));
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueuedThreads().size());
        assertTrue(lock.getQueuedThreads().contains(t1));
        assertTrue(lock.getQueuedThreads().contains(t2));
        t1.interrupt();
        awaitTermination(t1);
        assertFalse(lock.getQueuedThreads().contains(t1));
        assertTrue(lock.getQueuedThreads().contains(t2));
        assertEquals(1, lock.getQueuedThreads().size());
        lock.unlock();
        awaitTermination(t2);
        assertTrue(lock.getQueuedThreads().isEmpty());
    }

    /**
     * timed tryLock is interruptible
     */
    public void testTryLock_Interruptible()      { testTryLock_Interruptible(false); }
    public void testTryLock_Interruptible_fair() { testTryLock_Interruptible(true); }
    public void testTryLock_Interruptible(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        lock.lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.tryLock(2 * LONG_DELAY_MS, MILLISECONDS);
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * tryLock on a locked lock fails
     */
    public void testTryLockWhenLocked()      { testTryLockWhenLocked(false); }
    public void testTryLockWhenLocked_fair() { testTryLockWhenLocked(true); }
    public void testTryLockWhenLocked(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        lock.lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.tryLock());
            }});

        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * Timed tryLock on a locked lock times out
     */
    public void testTryLock_Timeout()      { testTryLock_Timeout(false); }
    public void testTryLock_Timeout_fair() { testTryLock_Timeout(true); }
    public void testTryLock_Timeout(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        lock.lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                long timeoutMillis = 10;
                assertFalse(lock.tryLock(timeoutMillis, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            }});

        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * getHoldCount returns number of recursive holds
     */
    public void testGetHoldCount()      { testGetHoldCount(false); }
    public void testGetHoldCount_fair() { testGetHoldCount(true); }
    public void testGetHoldCount(boolean fair) {
        ReentrantLock lock = new ReentrantLock(fair);
        for (int i = 1; i <= SIZE; i++) {
            lock.lock();
            assertEquals(i, lock.getHoldCount());
        }
        for (int i = SIZE; i > 0; i--) {
            lock.unlock();
            assertEquals(i-1, lock.getHoldCount());
        }
    }

    /**
     * isLocked is true when locked and false when not
     */
    public void testIsLocked()      { testIsLocked(false); }
    public void testIsLocked_fair() { testIsLocked(true); }
    public void testIsLocked(boolean fair) {
        try {
            final ReentrantLock lock = new ReentrantLock(fair);
            assertFalse(lock.isLocked());
            lock.lock();
            assertTrue(lock.isLocked());
            lock.lock();
            assertTrue(lock.isLocked());
            lock.unlock();
            assertTrue(lock.isLocked());
            lock.unlock();
            assertFalse(lock.isLocked());
            final CyclicBarrier barrier = new CyclicBarrier(2);
            Thread t = newStartedThread(new CheckedRunnable() {
                    public void realRun() throws Exception {
                        lock.lock();
                        assertTrue(lock.isLocked());
                        barrier.await();
                        barrier.await();
                        lock.unlock();
                    }});

            barrier.await();
            assertTrue(lock.isLocked());
            barrier.await();
            awaitTermination(t);
            assertFalse(lock.isLocked());
        } catch (Exception e) {
            threadUnexpectedException(e);
        }
    }

    /**
     * lockInterruptibly succeeds when unlocked, else is interruptible
     */
    public void testLockInterruptibly()      { testLockInterruptibly(false); }
    public void testLockInterruptibly_fair() { testLockInterruptibly(true); }
    public void testLockInterruptibly(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException ie) {
            threadUnexpectedException(ie);
        }
        assertLockedByMoi(lock);
        Thread t = newStartedThread(new InterruptedLockRunnable(lock));
        waitForQueuedThread(lock, t);
        t.interrupt();
        assertTrue(lock.isLocked());
        assertTrue(lock.isHeldByCurrentThread());
        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IMSE()      { testAwait_IMSE(false); }
    public void testAwait_IMSE_fair() { testAwait_IMSE(true); }
    public void testAwait_IMSE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        for (AwaitMethod awaitMethod : AwaitMethod.values()) {
            long startTime = System.nanoTime();
            try {
                await(c, awaitMethod);
                shouldThrow();
            } catch (IllegalMonitorStateException success) {
            } catch (InterruptedException e) { threadUnexpectedException(e); }
            assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);
        }
    }

    /**
     * Calling signal without holding lock throws IllegalMonitorStateException
     */
    public void testSignal_IMSE()      { testSignal_IMSE(false); }
    public void testSignal_IMSE_fair() { testSignal_IMSE(true); }
    public void testSignal_IMSE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        try {
            c.signal();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * awaitNanos without a signal times out
     */
    public void testAwaitNanos_Timeout()      { testAwaitNanos_Timeout(false); }
    public void testAwaitNanos_Timeout_fair() { testAwaitNanos_Timeout(true); }
    public void testAwaitNanos_Timeout(boolean fair) {
        try {
            final ReentrantLock lock = new ReentrantLock(fair);
            final Condition c = lock.newCondition();
            lock.lock();
            long startTime = System.nanoTime();
            long timeoutMillis = 10;
            long timeoutNanos = MILLISECONDS.toNanos(timeoutMillis);
            long nanosRemaining = c.awaitNanos(timeoutNanos);
            assertTrue(nanosRemaining <= 0);
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            lock.unlock();
        } catch (InterruptedException e) {
            threadUnexpectedException(e);
        }
    }

    /**
     * timed await without a signal times out
     */
    public void testAwait_Timeout()      { testAwait_Timeout(false); }
    public void testAwait_Timeout_fair() { testAwait_Timeout(true); }
    public void testAwait_Timeout(boolean fair) {
        try {
            final ReentrantLock lock = new ReentrantLock(fair);
            final Condition c = lock.newCondition();
            lock.lock();
            long startTime = System.nanoTime();
            long timeoutMillis = 10;
            assertFalse(c.await(timeoutMillis, MILLISECONDS));
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            lock.unlock();
        } catch (InterruptedException e) {
            threadUnexpectedException(e);
        }
    }

    /**
     * awaitUntil without a signal times out
     */
    public void testAwaitUntil_Timeout()      { testAwaitUntil_Timeout(false); }
    public void testAwaitUntil_Timeout_fair() { testAwaitUntil_Timeout(true); }
    public void testAwaitUntil_Timeout(boolean fair) {
        try {
            final ReentrantLock lock = new ReentrantLock(fair);
            final Condition c = lock.newCondition();
            lock.lock();
            long startTime = System.nanoTime();
            long timeoutMillis = 10;
            java.util.Date d = new java.util.Date();
            assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + timeoutMillis)));
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            lock.unlock();
        } catch (InterruptedException e) {
            threadUnexpectedException(e);
        }
    }

    /**
     * await returns when signalled
     */
    public void testAwait()      { testAwait(false); }
    public void testAwait_fair() { testAwait(true); }
    public void testAwait(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked.countDown();
                c.await();
                lock.unlock();
            }});

        await(locked);
        lock.lock();
        assertHasWaiters(lock, c, t);
        c.signal();
        assertHasNoWaiters(lock, c);
        assertTrue(t.isAlive());
        lock.unlock();
        awaitTermination(t);
    }

    /**
     * hasWaiters throws NPE if null
     */
    public void testHasWaitersNPE()      { testHasWaitersNPE(false); }
    public void testHasWaitersNPE_fair() { testHasWaitersNPE(true); }
    public void testHasWaitersNPE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        try {
            lock.hasWaiters(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitQueueLength throws NPE if null
     */
    public void testGetWaitQueueLengthNPE()      { testGetWaitQueueLengthNPE(false); }
    public void testGetWaitQueueLengthNPE_fair() { testGetWaitQueueLengthNPE(true); }
    public void testGetWaitQueueLengthNPE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        try {
            lock.getWaitQueueLength(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitingThreads throws NPE if null
     */
    public void testGetWaitingThreadsNPE()      { testGetWaitingThreadsNPE(false); }
    public void testGetWaitingThreadsNPE_fair() { testGetWaitingThreadsNPE(true); }
    public void testGetWaitingThreadsNPE(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        try {
            lock.getWaitingThreads(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasWaiters throws IllegalArgumentException if not owned
     */
    public void testHasWaitersIAE()      { testHasWaitersIAE(false); }
    public void testHasWaitersIAE_fair() { testHasWaitersIAE(true); }
    public void testHasWaitersIAE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        final ReentrantLock lock2 = new ReentrantLock(fair);
        try {
            lock2.hasWaiters(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * hasWaiters throws IllegalMonitorStateException if not locked
     */
    public void testHasWaitersIMSE()      { testHasWaitersIMSE(false); }
    public void testHasWaitersIMSE_fair() { testHasWaitersIMSE(true); }
    public void testHasWaitersIMSE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        try {
            lock.hasWaiters(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalArgumentException if not owned
     */
    public void testGetWaitQueueLengthIAE()      { testGetWaitQueueLengthIAE(false); }
    public void testGetWaitQueueLengthIAE_fair() { testGetWaitQueueLengthIAE(true); }
    public void testGetWaitQueueLengthIAE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        final ReentrantLock lock2 = new ReentrantLock(fair);
        try {
            lock2.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitQueueLengthIMSE()      { testGetWaitQueueLengthIMSE(false); }
    public void testGetWaitQueueLengthIMSE_fair() { testGetWaitQueueLengthIMSE(true); }
    public void testGetWaitQueueLengthIMSE(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        try {
            lock.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitingThreads throws IllegalArgumentException if not owned
     */
    public void testGetWaitingThreadsIAE()      { testGetWaitingThreadsIAE(false); }
    public void testGetWaitingThreadsIAE_fair() { testGetWaitingThreadsIAE(true); }
    public void testGetWaitingThreadsIAE(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final PublicReentrantLock lock2 = new PublicReentrantLock(fair);
        try {
            lock2.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitingThreads throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitingThreadsIMSE()      { testGetWaitingThreadsIMSE(false); }
    public void testGetWaitingThreadsIMSE_fair() { testGetWaitingThreadsIMSE(true); }
    public void testGetWaitingThreadsIMSE(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        try {
            lock.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * hasWaiters returns true when a thread is waiting, else false
     */
    public void testHasWaiters()      { testHasWaiters(false); }
    public void testHasWaiters_fair() { testHasWaiters(true); }
    public void testHasWaiters(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch pleaseSignal = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                pleaseSignal.countDown();
                c.await();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                lock.unlock();
            }});

        await(pleaseSignal);
        lock.lock();
        assertHasWaiters(lock, c, t);
        assertTrue(lock.hasWaiters(c));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertFalse(lock.hasWaiters(c));
        lock.unlock();
        awaitTermination(t);
        assertHasNoWaiters(lock, c);
    }

    /**
     * getWaitQueueLength returns number of waiting threads
     */
    public void testGetWaitQueueLength()      { testGetWaitQueueLength(false); }
    public void testGetWaitQueueLength_fair() { testGetWaitQueueLength(true); }
    public void testGetWaitQueueLength(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertFalse(lock.hasWaiters(c));
                assertEquals(0, lock.getWaitQueueLength(c));
                locked1.countDown();
                c.await();
                lock.unlock();
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.hasWaiters(c));
                assertEquals(1, lock.getWaitQueueLength(c));
                locked2.countDown();
                c.await();
                lock.unlock();
            }});

        lock.lock();
        assertEquals(0, lock.getWaitQueueLength(c));
        lock.unlock();

        t1.start();
        await(locked1);

        lock.lock();
        assertHasWaiters(lock, c, t1);
        assertEquals(1, lock.getWaitQueueLength(c));
        lock.unlock();

        t2.start();
        await(locked2);

        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertEquals(2, lock.getWaitQueueLength(c));
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();

        awaitTermination(t1);
        awaitTermination(t2);

        assertHasNoWaiters(lock, c);
    }

    /**
     * getWaitingThreads returns only and all waiting threads
     */
    public void testGetWaitingThreads()      { testGetWaitingThreads(false); }
    public void testGetWaitingThreads_fair() { testGetWaitingThreads(true); }
    public void testGetWaitingThreads(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.getWaitingThreads(c).isEmpty());
                locked1.countDown();
                c.await();
                lock.unlock();
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertFalse(lock.getWaitingThreads(c).isEmpty());
                locked2.countDown();
                c.await();
                lock.unlock();
            }});

        lock.lock();
        assertTrue(lock.getWaitingThreads(c).isEmpty());
        lock.unlock();

        t1.start();
        await(locked1);

        lock.lock();
        assertHasWaiters(lock, c, t1);
        assertTrue(lock.getWaitingThreads(c).contains(t1));
        assertFalse(lock.getWaitingThreads(c).contains(t2));
        assertEquals(1, lock.getWaitingThreads(c).size());
        lock.unlock();

        t2.start();
        await(locked2);

        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertTrue(lock.getWaitingThreads(c).contains(t1));
        assertTrue(lock.getWaitingThreads(c).contains(t2));
        assertEquals(2, lock.getWaitingThreads(c).size());
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();

        awaitTermination(t1);
        awaitTermination(t2);

        assertHasNoWaiters(lock, c);
    }

    /**
     * awaitUninterruptibly is uninterruptible
     */
    public void testAwaitUninterruptibly()      { testAwaitUninterruptibly(false); }
    public void testAwaitUninterruptibly_fair() { testAwaitUninterruptibly(true); }
    public void testAwaitUninterruptibly(boolean fair) {
        final ReentrantLock lock = new ReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch pleaseInterrupt = new CountDownLatch(2);

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                // Interrupt before awaitUninterruptibly
                lock.lock();
                pleaseInterrupt.countDown();
                Thread.currentThread().interrupt();
                c.awaitUninterruptibly();
                assertTrue(Thread.interrupted());
                lock.unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                // Interrupt during awaitUninterruptibly
                lock.lock();
                pleaseInterrupt.countDown();
                c.awaitUninterruptibly();
                assertTrue(Thread.interrupted());
                lock.unlock();
            }});

        await(pleaseInterrupt);
        lock.lock();
        lock.unlock();
        t2.interrupt();

        assertThreadStaysAlive(t1);
        assertTrue(t2.isAlive());

        lock.lock();
        c.signalAll();
        lock.unlock();

        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * await/awaitNanos/awaitUntil is interruptible
     */
    public void testInterruptible_await()           { testInterruptible(false, AwaitMethod.await); }
    public void testInterruptible_await_fair()      { testInterruptible(true,  AwaitMethod.await); }
    public void testInterruptible_awaitTimed()      { testInterruptible(false, AwaitMethod.awaitTimed); }
    public void testInterruptible_awaitTimed_fair() { testInterruptible(true,  AwaitMethod.awaitTimed); }
    public void testInterruptible_awaitNanos()      { testInterruptible(false, AwaitMethod.awaitNanos); }
    public void testInterruptible_awaitNanos_fair() { testInterruptible(true,  AwaitMethod.awaitNanos); }
    public void testInterruptible_awaitUntil()      { testInterruptible(false, AwaitMethod.awaitUntil); }
    public void testInterruptible_awaitUntil_fair() { testInterruptible(true,  AwaitMethod.awaitUntil); }
    public void testInterruptible(boolean fair, final AwaitMethod awaitMethod) {
        final PublicReentrantLock lock =
            new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertLockedByMoi(lock);
                assertHasNoWaiters(lock, c);
                pleaseInterrupt.countDown();
                try {
                    await(c, awaitMethod);
                } finally {
                    assertLockedByMoi(lock);
                    assertHasNoWaiters(lock, c);
                    lock.unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        await(pleaseInterrupt);
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertNotLocked(lock);
    }

    /**
     * signalAll wakes up all threads
     */
    public void testSignalAll_await()           { testSignalAll(false, AwaitMethod.await); }
    public void testSignalAll_await_fair()      { testSignalAll(true,  AwaitMethod.await); }
    public void testSignalAll_awaitTimed()      { testSignalAll(false, AwaitMethod.awaitTimed); }
    public void testSignalAll_awaitTimed_fair() { testSignalAll(true,  AwaitMethod.awaitTimed); }
    public void testSignalAll_awaitNanos()      { testSignalAll(false, AwaitMethod.awaitNanos); }
    public void testSignalAll_awaitNanos_fair() { testSignalAll(true,  AwaitMethod.awaitNanos); }
    public void testSignalAll_awaitUntil()      { testSignalAll(false, AwaitMethod.awaitUntil); }
    public void testSignalAll_awaitUntil_fair() { testSignalAll(true,  AwaitMethod.awaitUntil); }
    public void testSignalAll(boolean fair, final AwaitMethod awaitMethod) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch pleaseSignal = new CountDownLatch(2);
        class Awaiter extends CheckedRunnable {
            public void realRun() throws InterruptedException {
                lock.lock();
                pleaseSignal.countDown();
                await(c, awaitMethod);
                lock.unlock();
            }
        }

        Thread t1 = newStartedThread(new Awaiter());
        Thread t2 = newStartedThread(new Awaiter());

        await(pleaseSignal);
        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * signal wakes up waiting threads in FIFO order
     */
    public void testSignalWakesFifo()      { testSignalWakesFifo(false); }
    public void testSignalWakesFifo_fair() { testSignalWakesFifo(true); }
    public void testSignalWakesFifo(boolean fair) {
        final PublicReentrantLock lock =
            new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked1.countDown();
                c.await();
                lock.unlock();
            }});

        await(locked1);

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked2.countDown();
                c.await();
                lock.unlock();
            }});

        await(locked2);

        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertFalse(lock.hasQueuedThreads());
        c.signal();
        assertHasWaiters(lock, c, t2);
        assertTrue(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertTrue(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        lock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * await after multiple reentrant locking preserves lock count
     */
    public void testAwaitLockCount()      { testAwaitLockCount(false); }
    public void testAwaitLockCount_fair() { testAwaitLockCount(true); }
    public void testAwaitLockCount(boolean fair) {
        final PublicReentrantLock lock = new PublicReentrantLock(fair);
        final Condition c = lock.newCondition();
        final CountDownLatch pleaseSignal = new CountDownLatch(2);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertLockedByMoi(lock);
                assertEquals(1, lock.getHoldCount());
                pleaseSignal.countDown();
                c.await();
                assertLockedByMoi(lock);
                assertEquals(1, lock.getHoldCount());
                lock.unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                lock.lock();
                assertLockedByMoi(lock);
                assertEquals(2, lock.getHoldCount());
                pleaseSignal.countDown();
                c.await();
                assertLockedByMoi(lock);
                assertEquals(2, lock.getHoldCount());
                lock.unlock();
                lock.unlock();
            }});

        await(pleaseSignal);
        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertEquals(1, lock.getHoldCount());
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A serialized lock deserializes as unlocked
     */
    public void testSerialization()      { testSerialization(false); }
    public void testSerialization_fair() { testSerialization(true); }
    public void testSerialization(boolean fair) {
        ReentrantLock lock = new ReentrantLock(fair);
        lock.lock();

        ReentrantLock clone = serialClone(lock);
        assertEquals(lock.isFair(), clone.isFair());
        assertTrue(lock.isLocked());
        assertFalse(clone.isLocked());
        assertEquals(1, lock.getHoldCount());
        assertEquals(0, clone.getHoldCount());
        clone.lock();
        clone.lock();
        assertTrue(clone.isLocked());
        assertEquals(2, clone.getHoldCount());
        assertEquals(1, lock.getHoldCount());
        clone.unlock();
        clone.unlock();
        assertTrue(lock.isLocked());
        assertFalse(clone.isLocked());
    }

    /**
     * toString indicates current lock state
     */
    public void testToString()      { testToString(false); }
    public void testToString_fair() { testToString(true); }
    public void testToString(boolean fair) {
        ReentrantLock lock = new ReentrantLock(fair);
        assertTrue(lock.toString().contains("Unlocked"));
        lock.lock();
        assertTrue(lock.toString().contains("Locked"));
        lock.unlock();
        assertTrue(lock.toString().contains("Unlocked"));
    }
}
