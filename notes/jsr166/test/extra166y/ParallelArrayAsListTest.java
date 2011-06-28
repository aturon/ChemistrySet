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
import jsr166y.*;
import extra166y.*;
import java.io.*;

public class ParallelArrayAsListTest extends JSR166TestCase{

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(ParallelArrayAsListTest.class);
    }

    static List populatedArray(int n) {
        List a = ParallelArray.createEmpty(n, Object.class, ParallelArray.defaultExecutor()).asList();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; ++i)
            a.add(new Integer(i));
        assertFalse(a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }


    static List emptyArray() {
        List a = ParallelArray.createEmpty(1, Object.class, ParallelArray.defaultExecutor()).asList();
        return a;
    }


    /**
     * a new list is empty
     */
    public void testConstructor() {
        List a = ParallelArray.createEmpty(1, Object.class, ParallelArray.defaultExecutor()).asList();
        assertTrue(a.isEmpty());
    }

    /**
     * new list contains all elements of initializing array
     */
    public void testConstructor2() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE-1; ++i)
            ints[i] = new Integer(i);
        List a = ParallelArray.createUsingHandoff(ints, ParallelArray.defaultExecutor()).asList();
        for (int i = 0; i < SIZE; ++i)
            assertEquals(ints[i], a.get(i));
    }


    /**
     * addAll adds each element from the given collection
     */
    public void testAddAll() {
        List full = populatedArray(3);
        Vector v = new Vector();
        v.add(three);
        v.add(four);
        v.add(five);
        full.addAll(v);
        assertEquals(6, full.size());
    }

    /**
     * clear removes all elements from the list
     */
    public void testClear() {
        List full = populatedArray(SIZE);
        full.clear();
        assertEquals(0, full.size());
    }



    /**
     * contains is true for added elements
     */
    public void testContains() {
        List full = populatedArray(3);
        assertTrue(full.contains(one));
        assertFalse(full.contains(five));
    }

    /**
     * adding at an index places it in the indicated index
     */
    public void testAddIndex() {
        List full = populatedArray(3);
        full.add(0, m1);
        assertEquals(4, full.size());
        assertEquals(m1, full.get(0));
        assertEquals(zero, full.get(1));

        full.add(2, m2);
        assertEquals(5, full.size());
        assertEquals(m2, full.get(2));
        assertEquals(two, full.get(4));
    }

    /**
     * lists with same elements are equal and have same hashCode
     */
    public void testEquals() {
        List a = populatedArray(3);
        List b = populatedArray(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
        a.add(m1);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        b.add(m1);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }


    /**
     * containsAll returns true for collection with subset of elements
     */
    public void testContainsAll() {
        List full = populatedArray(3);
        Vector v = new Vector();
        v.add(one);
        v.add(two);
        assertTrue(full.containsAll(v));
        v.add(six);
        assertFalse(full.containsAll(v));
    }

    /**
     * get returns the  value at the given index
     */
    public void testGet() {
        List full = populatedArray(3);
        assertEquals(0, ((Integer)full.get(0)).intValue());
    }

    /**
     * indexOf gives the index for the given object
     */
    public void testIndexOf() {
        List full = populatedArray(3);
        assertEquals(1, full.indexOf(one));
        assertEquals(-1, full.indexOf("puppies"));
    }

    /**
     * isEmpty returns true when empty, else false
     */
    public void testIsEmpty() {
        List empty = emptyArray();
        List full = populatedArray(SIZE);
        assertTrue(empty.isEmpty());
        assertFalse(full.isEmpty());
    }

    /**
     * iterator() returns an iterator containing the elements of the list
     */
    public void testIterator() {
        List full = populatedArray(SIZE);
        Iterator i = full.iterator();
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j, ((Integer)i.next()).intValue());
        assertEquals(SIZE, j);
    }

    /**
     * iterator.remove removes element
     */
    public void testIteratorRemove() {
        List full = populatedArray(SIZE);
        Iterator it = full.iterator();
        Object first = full.get(0);
        it.next();
        it.remove();
        assertFalse(full.contains(first));
    }

    /**
     * toString contains toString of elements
     */
    public void testToString() {
        List full = populatedArray(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }

    /**
     * lastIndexOf returns the index for the given object
     */
    public void testLastIndexOf1() {
        List full = populatedArray(3);
        full.add(one);
        full.add(three);
        assertEquals(3, full.lastIndexOf(one));
        assertEquals(-1, full.lastIndexOf(six));
    }

    /**
     * listIterator traverses all elements
     */
    public void testListIterator1() {
        List full = populatedArray(SIZE);
        ListIterator i = full.listIterator();
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j, ((Integer)i.next()).intValue());
        assertEquals(SIZE, j);
    }

    /**
     * listIterator only returns those elements after the given index
     */
    public void testListIterator2() {
        List full = populatedArray(3);
        ListIterator i = full.listIterator(1);
        int j;
        for (j = 0; i.hasNext(); j++)
            assertEquals(j+1, ((Integer)i.next()).intValue());
        assertEquals(2, j);
    }

    /**
     * remove removes and returns the object at the given index
     */
    public void testRemove() {
        List full = populatedArray(3);
        assertEquals(two, full.remove(2));
        assertEquals(2, full.size());
    }

    /**
     * removeAll removes all elements from the given collection
     */
    public void testRemoveAll() {
        List full = populatedArray(3);
        Vector v = new Vector();
        v.add(one);
        v.add(two);
        full.removeAll(v);
        assertEquals(1, full.size());
    }

    /**
     * set changes the element at the given index
     */
    public void testSet() {
        List full = populatedArray(3);
        assertEquals(two, full.set(2, four));
        assertEquals(4, ((Integer)full.get(2)).intValue());
    }

    /**
     * size returns the number of elements
     */
    public void testSize() {
        List empty = emptyArray();
        List full = populatedArray(SIZE);
        assertEquals(SIZE, full.size());
        assertEquals(0, empty.size());
    }

    /**
     * toArray returns an Object array containing all elements from the list
     */
    public void testToArray() {
        List full = populatedArray(3);
        Object[] o = full.toArray();
        assertEquals(3, o.length);
        assertEquals(0, ((Integer)o[0]).intValue());
        assertEquals(1, ((Integer)o[1]).intValue());
        assertEquals(2, ((Integer)o[2]).intValue());
    }

    /**
     * toArray returns an Integer array containing all elements from
     * the list
     */
    public void testToArray2() {
        List full = populatedArray(3);
        Integer[] i = new Integer[3];
        i = (Integer[])full.toArray(i);
        assertEquals(3, i.length);
        assertEquals(0, i[0].intValue());
        assertEquals(1, i[1].intValue());
        assertEquals(2, i[2].intValue());
    }


    /**
     * sublists contains elements at indexes offset from their base
     */
    public void testSubList() {
        List a = populatedArray(10);
        assertTrue(a.subList(1,1).isEmpty());
        for (int j = 0; j < 9; ++j) {
            for (int i = j ; i < 10; ++i) {
                List b = a.subList(j,i);
                for (int k = j; k < i; ++k) {
                    assertEquals(new Integer(k), b.get(k-j));
                }
            }
        }

        List s = a.subList(2, 5);
        assertEquals(s.size(), 3);
        s.set(2, m1);
        assertEquals(a.get(4), m1);
        s.clear();
        assertEquals(a.size(), 7);
    }

    // Exception tests

    /**
     * toArray throws an ArrayStoreException when the given array
     * can not store the objects inside the list
     */
    public void testToArray_ArrayStoreException() {
        try {
            List c = emptyArray();
            c.add("zfasdfsdf");
            c.add("asdadasd");
            c.toArray(new Long[5]);
            shouldThrow();
        } catch (ArrayStoreException e) {}
    }

    /**
     * get throws an IndexOutOfBoundsException on a negative index
     */
    public void testGet1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.get(-1);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * get throws an IndexOutOfBoundsException on a too high index
     */
    public void testGet2_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.add("asdad");
            c.get(100);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * set throws an IndexOutOfBoundsException on a negative index
     */
    public void testSet1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.set(-1,"qwerty");
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * set throws an IndexOutOfBoundsException on a too high index
     */
    public void testSet2() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.add("asdad");
            c.set(100, "qwerty");
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * add throws an IndexOutOfBoundsException on a negative index
     */
    public void testAdd1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add(-1,"qwerty");
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * add throws an IndexOutOfBoundsException on a too high index
     */
    public void testAdd2_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.add("asdasdasd");
            c.add(100, "qwerty");
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * remove throws an IndexOutOfBoundsException on a negative index
     */
    public void testRemove1_IndexOutOfBounds() {
        try {
            List c = emptyArray();
            c.remove(-1);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * remove throws an IndexOutOfBoundsException on a too high index
     */
    public void testRemove2_IndexOutOfBounds() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.add("adasdasd");
            c.remove(100);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * addAll throws an IndexOutOfBoundsException on a negative index
     */
    public void testAddAll1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.addAll(-1,new LinkedList());
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * addAll throws an IndexOutOfBoundsException on a too high index
     */
    public void testAddAll2_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.add("asdasdasd");
            c.addAll(100, new LinkedList());
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * listIterator throws an IndexOutOfBoundsException on a negative index
     */
    public void testListIterator1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.listIterator(-1);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * listIterator throws an IndexOutOfBoundsException on a too high index
     */
    public void testListIterator2_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add("adasd");
            c.add("asdasdas");
            c.listIterator(100);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * subList throws an IndexOutOfBoundsException on a negative index
     */
    public void testSubList1_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.subList(-1,100);

            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * subList throws an IndexOutOfBoundsException on a too high index
     */
    public void testSubList2_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.add("asdasd");
            c.subList(1,100);
            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }

    /**
     * subList throws IndexOutOfBoundsException when the second index
     * is lower then the first
     */
    public void testSubList3_IndexOutOfBoundsException() {
        try {
            List c = emptyArray();
            c.subList(3,1);

            shouldThrow();
        } catch (IndexOutOfBoundsException e) {}
    }


}
