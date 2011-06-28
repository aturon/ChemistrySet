/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.security.*;

public class ExecutorsTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ExecutorsTest.class);
    }

    /**
     * A newCachedThreadPool can execute runnables
     */
    public void testNewCachedThreadPool1() {
        ExecutorService e = Executors.newCachedThreadPool();
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A newCachedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewCachedThreadPool2() {
        ExecutorService e = Executors.newCachedThreadPool(new SimpleThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A newCachedThreadPool with null ThreadFactory throws NPE
     */
    public void testNewCachedThreadPool3() {
        try {
            ExecutorService e = Executors.newCachedThreadPool(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor can execute runnables
     */
    public void testNewSingleThreadExecutor1() {
        ExecutorService e = Executors.newSingleThreadExecutor();
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A new SingleThreadExecutor with given ThreadFactory can execute runnables
     */
    public void testNewSingleThreadExecutor2() {
        ExecutorService e = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A new SingleThreadExecutor with null ThreadFactory throws NPE
     */
    public void testNewSingleThreadExecutor3() {
        try {
            ExecutorService e = Executors.newSingleThreadExecutor(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new SingleThreadExecutor cannot be casted to concrete implementation
     */
    public void testCastNewSingleThreadExecutor() {
        ExecutorService e = Executors.newSingleThreadExecutor();
        try {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor)e;
            shouldThrow();
        } catch (ClassCastException success) {
        } finally {
            joinPool(e);
        }
    }

    /**
     * A new newFixedThreadPool can execute runnables
     */
    public void testNewFixedThreadPool1() {
        ExecutorService e = Executors.newFixedThreadPool(2);
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A new newFixedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewFixedThreadPool2() {
        ExecutorService e = Executors.newFixedThreadPool(2, new SimpleThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * A new newFixedThreadPool with null ThreadFactory throws NPE
     */
    public void testNewFixedThreadPool3() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(2, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * A new newFixedThreadPool with 0 threads throws IAE
     */
    public void testNewFixedThreadPool4() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * An unconfigurable newFixedThreadPool can execute runnables
     */
    public void testUnconfigurableExecutorService() {
        ExecutorService e = Executors.unconfigurableExecutorService(Executors.newFixedThreadPool(2));
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        joinPool(e);
    }

    /**
     * unconfigurableExecutorService(null) throws NPE
     */
    public void testUnconfigurableExecutorServiceNPE() {
        try {
            ExecutorService e = Executors.unconfigurableExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * unconfigurableScheduledExecutorService(null) throws NPE
     */
    public void testUnconfigurableScheduledExecutorServiceNPE() {
        try {
            ExecutorService e = Executors.unconfigurableScheduledExecutorService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * a newSingleThreadScheduledExecutor successfully runs delayed task
     */
    public void testNewSingleThreadScheduledExecutor() throws Exception {
        ScheduledExecutorService p = Executors.newSingleThreadScheduledExecutor();
        try {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        } finally {
            joinPool(p);
        }
    }

    /**
     * a newScheduledThreadPool successfully runs delayed task
     */
    public void testNewScheduledThreadPool() throws Exception {
        ScheduledExecutorService p = Executors.newScheduledThreadPool(2);
        try {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        } finally {
            joinPool(p);
        }
    }

    /**
     * an unconfigurable newScheduledThreadPool successfully runs delayed task
     */
    public void testUnconfigurableScheduledExecutorService() throws Exception {
        ScheduledExecutorService p =
            Executors.unconfigurableScheduledExecutorService
            (Executors.newScheduledThreadPool(2));
        try {
            final CountDownLatch proceed = new CountDownLatch(1);
            final Runnable task = new CheckedRunnable() {
                public void realRun() {
                    await(proceed);
                }};
            long startTime = System.nanoTime();
            Future f = p.schedule(Executors.callable(task, Boolean.TRUE),
                                  timeoutMillis(), MILLISECONDS);
            assertFalse(f.isDone());
            proceed.countDown();
            assertSame(Boolean.TRUE, f.get(LONG_DELAY_MS, MILLISECONDS));
            assertSame(Boolean.TRUE, f.get());
            assertTrue(f.isDone());
            assertFalse(f.isCancelled());
            assertTrue(millisElapsedSince(startTime) >= timeoutMillis());
        } finally {
            joinPool(p);
        }
    }

    /**
     * Future.get on submitted tasks will time out if they compute too long.
     */
    public void testTimedCallable() throws Exception {
        final ExecutorService[] executors = {
            Executors.newSingleThreadExecutor(),
            Executors.newCachedThreadPool(),
            Executors.newFixedThreadPool(2),
            Executors.newScheduledThreadPool(2),
        };

        final Runnable sleeper = new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                delay(LONG_DELAY_MS);
            }};

        List<Thread> threads = new ArrayList<Thread>();
        for (final ExecutorService executor : executors) {
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    long startTime = System.nanoTime();
                    Future future = executor.submit(sleeper);
                    assertFutureTimesOut(future);
                }}));
        }
        for (Thread thread : threads)
            awaitTermination(thread);
        for (ExecutorService executor : executors)
            joinPool(executor);
    }

    /**
     * ThreadPoolExecutor using defaultThreadFactory has
     * specified group, priority, daemon status, and name
     */
    public void testDefaultThreadFactory() throws Exception {
        final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
        final CountDownLatch done = new CountDownLatch(1);
        Runnable r = new CheckedRunnable() {
            public void realRun() {
                try {
                    Thread current = Thread.currentThread();
                    assertTrue(!current.isDaemon());
                    assertTrue(current.getPriority() <= Thread.NORM_PRIORITY);
                    ThreadGroup g = current.getThreadGroup();
                    SecurityManager s = System.getSecurityManager();
                    if (s != null)
                        assertTrue(g == s.getThreadGroup());
                    else
                        assertTrue(g == egroup);
                    String name = current.getName();
                    assertTrue(name.endsWith("thread-1"));
                } catch (SecurityException ok) {
                    // Also pass if not allowed to change setting
                }
                done.countDown();
            }};
        ExecutorService e = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());

        e.execute(r);
        await(done);

        try {
            e.shutdown();
        } catch (SecurityException ok) {
        }

        joinPool(e);
    }

    /**
     * ThreadPoolExecutor using privilegedThreadFactory has
     * specified group, priority, daemon status, name,
     * access control context and context class loader
     */
    public void testPrivilegedThreadFactory() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
                final ClassLoader thisccl = Thread.currentThread().getContextClassLoader();
                final AccessControlContext thisacc = AccessController.getContext();
                Runnable r = new CheckedRunnable() {
                    public void realRun() {
                        Thread current = Thread.currentThread();
                        assertTrue(!current.isDaemon());
                        assertTrue(current.getPriority() <= Thread.NORM_PRIORITY);
                        ThreadGroup g = current.getThreadGroup();
                        SecurityManager s = System.getSecurityManager();
                        if (s != null)
                            assertTrue(g == s.getThreadGroup());
                        else
                            assertTrue(g == egroup);
                        String name = current.getName();
                        assertTrue(name.endsWith("thread-1"));
                        assertSame(thisccl, current.getContextClassLoader());
                        assertEquals(thisacc, AccessController.getContext());
                        done.countDown();
                    }};
                ExecutorService e = Executors.newSingleThreadExecutor(Executors.privilegedThreadFactory());
                e.execute(r);
                await(done);
                e.shutdown();
                joinPool(e);
            }};

        runWithPermissions(r,
                           new RuntimePermission("getClassLoader"),
                           new RuntimePermission("setContextClassLoader"),
                           new RuntimePermission("modifyThread"));
    }

    boolean haveCCLPermissions() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
                sm.checkPermission(new RuntimePermission("getClassLoader"));
            } catch (AccessControlException e) {
                return false;
            }
        }
        return true;
    }

    void checkCCL() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            sm.checkPermission(new RuntimePermission("getClassLoader"));
        }
    }

    class CheckCCL implements Callable<Object> {
        public Object call() {
            checkCCL();
            return null;
        }
    }

    /**
     * Without class loader permissions, creating
     * privilegedCallableUsingCurrentClassLoader throws ACE
     */
    public void testCreatePrivilegedCallableUsingCCLWithNoPrivs() {
        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                if (System.getSecurityManager() == null)
                    return;
                try {
                    Executors.privilegedCallableUsingCurrentClassLoader(new NoOpCallable());
                    shouldThrow();
                } catch (AccessControlException success) {}
            }};

        runWithoutPermissions(r);
    }

    /**
     * With class loader permissions, calling
     * privilegedCallableUsingCurrentClassLoader does not throw ACE
     */
    public void testPrivilegedCallableUsingCCLWithPrivs() throws Exception {
        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                Executors.privilegedCallableUsingCurrentClassLoader
                    (new NoOpCallable())
                    .call();
            }};

        runWithPermissions(r,
                           new RuntimePermission("getClassLoader"),
                           new RuntimePermission("setContextClassLoader"));
    }

    /**
     * Without permissions, calling privilegedCallable throws ACE
     */
    public void testPrivilegedCallableWithNoPrivs() throws Exception {
        // Avoid classloader-related SecurityExceptions in swingui.TestRunner
        Executors.privilegedCallable(new CheckCCL());

        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                if (System.getSecurityManager() == null)
                    return;
                Callable task = Executors.privilegedCallable(new CheckCCL());
                try {
                    task.call();
                    shouldThrow();
                } catch (AccessControlException success) {}
            }};

        runWithoutPermissions(r);

        // It seems rather difficult to test that the
        // AccessControlContext of the privilegedCallable is used
        // instead of its caller.  Below is a failed attempt to do
        // that, which does not work because the AccessController
        // cannot capture the internal state of the current Policy.
        // It would be much more work to differentiate based on,
        // e.g. CodeSource.

//         final AccessControlContext[] noprivAcc = new AccessControlContext[1];
//         final Callable[] task = new Callable[1];

//         runWithPermissions
//             (new CheckedRunnable() {
//                 public void realRun() {
//                     if (System.getSecurityManager() == null)
//                         return;
//                     noprivAcc[0] = AccessController.getContext();
//                     task[0] = Executors.privilegedCallable(new CheckCCL());
//                     try {
//                         AccessController.doPrivileged(new PrivilegedAction<Void>() {
//                                                           public Void run() {
//                                                               checkCCL();
//                                                               return null;
//                                                           }}, noprivAcc[0]);
//                         shouldThrow();
//                     } catch (AccessControlException success) {}
//                 }});

//         runWithPermissions
//             (new CheckedRunnable() {
//                 public void realRun() throws Exception {
//                     if (System.getSecurityManager() == null)
//                         return;
//                     // Verify that we have an underprivileged ACC
//                     try {
//                         AccessController.doPrivileged(new PrivilegedAction<Void>() {
//                                                           public Void run() {
//                                                               checkCCL();
//                                                               return null;
//                                                           }}, noprivAcc[0]);
//                         shouldThrow();
//                     } catch (AccessControlException success) {}

//                     try {
//                         task[0].call();
//                         shouldThrow();
//                     } catch (AccessControlException success) {}
//                 }},
//              new RuntimePermission("getClassLoader"),
//              new RuntimePermission("setContextClassLoader"));
    }

    /**
     * With permissions, calling privilegedCallable succeeds
     */
    public void testPrivilegedCallableWithPrivs() throws Exception {
        Runnable r = new CheckedRunnable() {
            public void realRun() throws Exception {
                Executors.privilegedCallable(new CheckCCL()).call();
            }};

        runWithPermissions(r,
                           new RuntimePermission("getClassLoader"),
                           new RuntimePermission("setContextClassLoader"));
    }

    /**
     * callable(Runnable) returns null when called
     */
    public void testCallable1() throws Exception {
        Callable c = Executors.callable(new NoOpRunnable());
        assertNull(c.call());
    }

    /**
     * callable(Runnable, result) returns result when called
     */
    public void testCallable2() throws Exception {
        Callable c = Executors.callable(new NoOpRunnable(), one);
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedAction) returns its result when called
     */
    public void testCallable3() throws Exception {
        Callable c = Executors.callable(new PrivilegedAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(PrivilegedExceptionAction) returns its result when called
     */
    public void testCallable4() throws Exception {
        Callable c = Executors.callable(new PrivilegedExceptionAction() {
                public Object run() { return one; }});
        assertSame(one, c.call());
    }

    /**
     * callable(null Runnable) throws NPE
     */
    public void testCallableNPE1() {
        try {
            Callable c = Executors.callable((Runnable) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null, result) throws NPE
     */
    public void testCallableNPE2() {
        try {
            Callable c = Executors.callable((Runnable) null, one);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedAction) throws NPE
     */
    public void testCallableNPE3() {
        try {
            Callable c = Executors.callable((PrivilegedAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * callable(null PrivilegedExceptionAction) throws NPE
     */
    public void testCallableNPE4() {
        try {
            Callable c = Executors.callable((PrivilegedExceptionAction) null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

}
