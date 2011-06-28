/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;

public class StringMapLoops {
    static int nkeys       = 75000;
    static int pinsert     = 60;
    static int premove     =  2;
    static int maxThreads  = 100;
    static int nops        = 8000000;
    static int removesPerMaxRandom;
    static int insertsPerMaxRandom;

    static final ExecutorService pool = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {

        Class mapClass = null;
        if (args.length > 0) {
            try {
                mapClass = Class.forName(args[0]);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class " + args[0] + " not found.");
            }
        }
        else
            mapClass = java.util.concurrent.ConcurrentHashMap.class;

        if (args.length > 1)
            maxThreads = Integer.parseInt(args[1]);

        if (args.length > 2)
            nkeys = Integer.parseInt(args[2]);

        if (args.length > 3)
            pinsert = Integer.parseInt(args[3]);

        if (args.length > 4)
            premove = Integer.parseInt(args[4]);

        if (args.length > 5)
            nops = Integer.parseInt(args[5]);

        // normalize probabilities wrt random number generator
        removesPerMaxRandom = (int)(((double)premove/100.0 * 0x7FFFFFFFL));
        insertsPerMaxRandom = (int)(((double)pinsert/100.0 * 0x7FFFFFFFL));

        System.out.print("Class: " + mapClass.getName());
        System.out.print(" threads: " + maxThreads);
        System.out.print(" size: " + nkeys);
        System.out.print(" ins: " + pinsert);
        System.out.print(" rem: " + premove);
        System.out.print(" ops: " + nops);
        System.out.println();

        String[] key = makeKeys(nkeys);

        int k = 1;
        int warmups = 2;
        for (int i = 1; i <= maxThreads;) {
            Thread.sleep(100);
            test(i, nkeys, key, mapClass);
            shuffleKeys(key);
            if (warmups > 0)
                --warmups;
            else if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            }
            else if (i == 1 && k == 2) {
                i = k;
                warmups = 1;
            }
            else
                i = k;
        }
        for (int j = 0; j < 10; ++j) {
            Thread.sleep(100);
            test(1, nkeys, key, mapClass);
            //            shuffleKeys(key);
        }
        pool.shutdown();
    }

    static String[] makeKeys(int n) {
        LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
        String[] key = new String[n];
        for (int i = 0; i < key.length; ++i) {
            int k = 0;
            int len = 1 + (rng.next() & 0xf);
            char[] c = new char[len * 4];
            for (int j = 0; j < len; ++j) {
                int r = rng.next();
                c[k++] = (char) (' ' + (r & 0x7f));
                r >>>= 8;
                c[k++] = (char) (' ' + (r & 0x7f));
                r >>>= 8;
                c[k++] = (char) (' ' + (r & 0x7f));
                r >>>= 8;
                c[k++] = (char) (' ' + (r & 0x7f));
            }
            key[i] = new String(c);
        }
        return key;
    }

    static void shuffleKeys(String[] key) {
        Random rng = new Random();
        for (int i = key.length; i > 1; --i) {
            int j = rng.nextInt(i);
            String tmp = key[j];
            key[j] = key[i-1];
            key[i-1] = tmp;
        }
    }

    static void test(int i, int nkeys, String[] key, Class mapClass) throws Exception {
        System.out.print("Threads: " + i + "\t:");
        Map<String, String> map = (Map<String,String>)mapClass.newInstance();
        // Uncomment to start with a non-empty table
        //        for (int j = 0; j < nkeys; j += 4) // start 1/4 occupied
        //            map.put(key[j], key[j]);

        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(i+1, timer);
        for (int t = 0; t < i; ++t)
            pool.execute(new Runner(t, map, key, barrier));
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        long tpo = time / (i * (long) nops);
        System.out.print(LoopHelpers.rightJustify(tpo) + " ns per op");
        double secs = (double) time / 1000000000.0;
        System.out.println("\t " + secs + "s run time");
        map.clear();
    }

    static class Runner implements Runnable {
        final Map<String,String> map;
        final String[] key;
        final LoopHelpers.SimpleRandom rng;
        final CyclicBarrier barrier;
        int position;
        int total;

        Runner(int id, Map<String,String> map, String[] key,  CyclicBarrier barrier) {
            this.map = map;
            this.key = key;
            this.barrier = barrier;
            position = key.length / 2;
            rng = new LoopHelpers.SimpleRandom((id + 1) * 8862213513L);
            rng.next();
        }

        int step() {
            // random-walk around key positions,  bunching accesses
            int r = rng.next();
            position += (r & 7) - 3;
            while (position >= key.length) position -= key.length;
            while (position < 0) position += key.length;

            String k = key[position];
            String x = map.get(k);

            if (x != null) {
                if (r < removesPerMaxRandom) {
                    if (map.remove(k) != null) {
                        position = total % key.length; // move from position
                        return 2;
                    }
                }
            }
            else if (r < insertsPerMaxRandom) {
                ++position;
                map.put(k, k);
                return 2;
            }

            total += r;
            return 1;
        }

        public void run() {
            try {
                barrier.await();
                int ops = nops;
                while (ops > 0)
                    ops -= step();
                barrier.await();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
