/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 6941130
 * @summary Numeric overflow/underflow of permits causes Error throw
 */

import java.util.concurrent.Semaphore;

public class PermitOverflow {

    public static void main(String[] args) throws Throwable {
        for (boolean fair : new boolean[] { true, false }) {
            Semaphore sem = new Semaphore(Integer.MAX_VALUE - 1, fair);
            if (sem.availablePermits() != Integer.MAX_VALUE - 1)
                throw new RuntimeException();
            try {
                sem.release(2);
                throw new RuntimeException();
            } catch (Error expected) {
            }
            sem.release(1);
            if (sem.availablePermits() != Integer.MAX_VALUE)
                throw new RuntimeException();
            try {
                sem.release(1);
                throw new RuntimeException();
            } catch (Error expected) {
            }
            try {
                sem.release(Integer.MAX_VALUE);
                throw new RuntimeException();
            } catch (Error expected) {
            }
        }

        class Sem extends Semaphore {
            public Sem(int permits, boolean fair) {
                super(permits, fair);
            }
            public void reducePermits(int reduction) {
                super.reducePermits(reduction);
            }
        }

        for (boolean fair : new boolean[] { true, false }) {
            Sem sem = new Sem(Integer.MIN_VALUE + 1, fair);
            if (sem.availablePermits() != Integer.MIN_VALUE + 1)
                throw new RuntimeException();
            try {
                sem.reducePermits(2);
                throw new RuntimeException();
            } catch (Error expected) {
            }
            sem.reducePermits(1);
            if (sem.availablePermits() != Integer.MIN_VALUE)
                throw new RuntimeException();
            try {
                sem.reducePermits(1);
                throw new RuntimeException();
            } catch (Error expected) {
            }
            try {
                sem.reducePermits(Integer.MAX_VALUE);
                throw new RuntimeException();
            } catch (Error expected) {
            }
        }
    }
}
