/*
 * Written by Martin Buchholz with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 6950540
 * @summary Attempt to add a null throws NullPointerException
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.PriorityQueue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class NoNulls {
    void test(String[] args) throws Throwable {
        final Comparator<String> nullTolerantComparator
            = new Comparator<String>() {
            public int compare(String x, String y) {
                return (x == null ? -1 :
                        y == null ? 1 :
                        x.compareTo(y));
            }};

        final SortedSet<String> nullSortedSet
            = new TreeSet<String>(nullTolerantComparator);
        nullSortedSet.add(null);

        final PriorityQueue<String> nullPriorityQueue
            = new PriorityQueue<String>() {
            public Object[] toArray() { return new Object[] { null };}};

        final Collection<String> nullCollection = new ArrayList<String>();
        nullCollection.add(null);

        THROWS(NullPointerException.class,
               new F() { void f() {
                   new PriorityQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, false, nullCollection);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, true, nullCollection);
               }},
               new F() { void f() {
                   new LinkedBlockingQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new LinkedBlockingDeque<String>(nullCollection);
               }},

               new F() { void f() {
                   new PriorityQueue<String>((Collection<String>) nullPriorityQueue);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>((Collection<String>) nullPriorityQueue);
               }},

               new F() { void f() {
                   new PriorityQueue<String>(nullSortedSet);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullSortedSet);
               }},

               new F() { void f() {
                   new PriorityQueue<String>((Collection<String>) nullSortedSet);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>((Collection<String>) nullSortedSet);
               }},

               new F() { void f() {
                   new PriorityQueue<String>(nullPriorityQueue);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullPriorityQueue);
               }},

               new F() { void f() {
                   new PriorityQueue<String>().add(null);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>().add(null);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, false).add(null);
               }},
               new F() { void f() {
                   new ArrayBlockingQueue<String>(10, true).add(null);
               }},
               new F() { void f() {
                   new LinkedBlockingQueue<String>().add(null);
               }},
               new F() { void f() {
                   new LinkedBlockingDeque<String>().add(null);
               }},

               new F() { void f() {
                   new PriorityQueue<String>().offer(null);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>().offer(null);
               }});

        nullSortedSet.add("foo");
        nullCollection.add("foo");
        THROWS(NullPointerException.class,
               new F() { void f() {
                   new PriorityQueue<String>(nullCollection);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullCollection);
               }},

               new F() { void f() {
                   new PriorityQueue<String>((Collection<String>) nullPriorityQueue);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>((Collection<String>) nullPriorityQueue);
               }},

               new F() { void f() {
                   new PriorityQueue<String>(nullSortedSet);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>(nullSortedSet);
               }},

               new F() { void f() {
                   new PriorityQueue<String>((Collection<String>) nullSortedSet);
               }},
               new F() { void f() {
                   new PriorityBlockingQueue<String>((Collection<String>) nullSortedSet);
               }});

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
        new NoNulls().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
    abstract class F {abstract void f() throws Throwable;}
    void THROWS(Class<? extends Throwable> k, F... fs) {
        for (F f : fs)
            try {f.f(); fail("Expected " + k.getName() + " not thrown");}
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
}
