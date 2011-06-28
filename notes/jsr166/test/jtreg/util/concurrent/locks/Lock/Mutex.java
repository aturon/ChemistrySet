/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.io.*;

/**
 * A sample user extension of AbstractQueuedSynchronizer.
 */
public class Mutex implements Lock, java.io.Serializable {
    private static class Sync extends AbstractQueuedSynchronizer {
        public boolean isHeldExclusively() { return getState() == 1; }

        public boolean tryAcquire(int acquires) {
            assert acquires == 1; // Does not use multiple acquires
            return compareAndSetState(0, 1);
        }

        public boolean tryRelease(int releases) {
            setState(0);
            return true;
        }

        Condition newCondition() { return new ConditionObject(); }

        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    private final Sync sync = new Sync();
    public void lock() {
        sync.acquire(1);
    }
    public boolean tryLock() {
        return sync.tryAcquire(1);
    }
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }
    public void unlock() { sync.release(1); }
    public Condition newCondition() { return sync.newCondition(); }
    public boolean isLocked() { return sync.isHeldExclusively(); }
    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
}
