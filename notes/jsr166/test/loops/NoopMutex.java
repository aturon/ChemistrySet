/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.atomic.*;

class NoopSpin100M {
    public static void main(String[] args) throws Exception {
        AtomicInteger lock = new AtomicInteger();
        for (int i = 100000000; i > 0; --i) {
            lock.compareAndSet(0,1);
            lock.set(0);
        }
    }
}
