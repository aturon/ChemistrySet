/*
 * Written by Martin Buchholz and Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Test Phaser phase integer overflow behavior
 */

import java.util.concurrent.Phaser;
import java.lang.reflect.Field;

public class PhaseOverflow {
    Field stateField;

    void checkState(Phaser phaser,
                    int phase, int parties, int unarrived) {
        equal(phase, phaser.getPhase());
        equal(parties, phaser.getRegisteredParties());
        equal(unarrived, phaser.getUnarrivedParties());
    }

    void test(String[] args) throws Throwable {
        stateField = Phaser.class.getDeclaredField("state");
        stateField.setAccessible(true);
        testLeaf();
        testTiered();
    }

    void testLeaf() throws Throwable {
        Phaser phaser = new Phaser();
        // this is extremely dependent on internal representation
        stateField.setLong(phaser, ((Integer.MAX_VALUE - 1L) << 32) | 1L);
        checkState(phaser, Integer.MAX_VALUE - 1, 0, 0);
        phaser.register();
        checkState(phaser, Integer.MAX_VALUE - 1, 1, 1);
        phaser.arrive();
        checkState(phaser, Integer.MAX_VALUE, 1, 1);
        phaser.arrive();
        checkState(phaser, 0, 1, 1);
        phaser.arrive();
        checkState(phaser, 1, 1, 1);
    }

    int phaseInc(int phase) { return (phase + 1) & Integer.MAX_VALUE; }

    void testTiered() throws Throwable {
        Phaser root = new Phaser();
        // this is extremely dependent on internal representation
        stateField.setLong(root, ((Integer.MAX_VALUE - 1L) << 32) | 1L);
        checkState(root, Integer.MAX_VALUE - 1, 0, 0);
        Phaser p1 = new Phaser(root, 1);
        checkState(root, Integer.MAX_VALUE - 1, 1, 1);
        checkState(p1, Integer.MAX_VALUE - 1, 1, 1);
        Phaser p2 = new Phaser(root, 2);
        checkState(root, Integer.MAX_VALUE - 1, 2, 2);
        checkState(p2, Integer.MAX_VALUE - 1, 2, 2);
        int ph = Integer.MAX_VALUE - 1;
        for (int k = 0; k < 5; k++) {
            checkState(root, ph, 2, 2);
            checkState(p1, ph, 1, 1);
            checkState(p2, ph, 2, 2);
            p1.arrive();
            checkState(root, ph, 2, 1);
            checkState(p1, ph, 1, 0);
            checkState(p2, ph, 2, 2);
            p2.arrive();
            checkState(root, ph, 2, 1);
            checkState(p1, ph, 1, 0);
            checkState(p2, ph, 2, 1);
            p2.arrive();
            ph = phaseInc(ph);
            checkState(root, ph, 2, 2);
            checkState(p1, ph, 1, 1);
            checkState(p2, ph, 2, 2);
        }
        equal(3, ph);
    }

    void xtestTiered() throws Throwable {
        Phaser root = new Phaser();
        stateField.setLong(root, ((Integer.MAX_VALUE - 1L) << 32) | 1L);
        checkState(root, Integer.MAX_VALUE - 1, 0, 0);
        Phaser p1 = new Phaser(root, 1);
        checkState(root, Integer.MAX_VALUE - 1, 1, 1);
        checkState(p1, Integer.MAX_VALUE - 1, 1, 1);
        Phaser p2 = new Phaser(root, 2);
        checkState(root, Integer.MAX_VALUE - 1, 2, 2);
        checkState(p2, Integer.MAX_VALUE - 1, 2, 2);
        int ph = Integer.MAX_VALUE - 1;
        for (int k = 0; k < 5; k++) {
            checkState(root, ph, 2, 2);
            checkState(p1, ph, 1, 1);
            checkState(p2, ph, 2, 2);
            p1.arrive();
            checkState(root, ph, 2, 1);
            checkState(p1, ph, 1, 0);
            checkState(p2, ph, 2, 2);
            p2.arrive();
            checkState(root, ph, 2, 1);
            checkState(p1, ph, 1, 0);
            checkState(p2, ph, 2, 1);
            p2.arrive();
            ph = phaseInc(ph);
            checkState(root, ph, 2, 2);
            checkState(p1, ph, 1, 1);
            checkState(p2, ph, 2, 2);
        }
        equal(3, ph);
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
        new PhaseOverflow().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
