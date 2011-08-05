package chemistry.bench.benchmarks

import java.util.concurrent._
import java.util.concurrent.locks._

import chemistry._
import chemistry.bench._
import chemistry.bench.competition._
import chemistry.Util._

object IncDec extends Benchmark {
  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    for (_ <-1 to iters) {
      Util.noop(r.fuzz(work))
      Util.noop(r.fuzz(work))
    }
  }

  private object reagent extends Entry {
    def name = "reagent"
    type S = Counter
    def setup = new Counter
    def run(s: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	s.inc ! ()
	Util.noop(r.fuzz(work))
	untilSome {s.tryDec ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }
  private object lock extends Entry {
    class IntRef {
      var count: Int = 0
    }

    def name = "lock"

    type S = (IntRef, ReentrantLock)
    def setup = (new IntRef, new ReentrantLock)
    def backoffSpin(l: ReentrantLock) {
      var r = l.tryLock
      if (r) return

      val backoff = new Backoff
      while (true) {
	r = l.tryLock
	if (r) return
	backoff.once
      }
    }
    def spinLock(l: ReentrantLock) {
      var r = l.tryLock
      if (r) return
      backoffSpin(l)
    }

//    type S = (IntRef, CLHLock)
//    def setup = (new IntRef, new CLHLock)
    def run(s: S, work: Int, iters: Int) {
      val r = new Random
      val c = s._1
      val l = s._2
      for (_ <- 1 to iters) {
//	l.lock
	spinLock(l)
	c.count += 1
	l.unlock

	Util.noop(r.fuzz(work))

//	l.lock
	spinLock(l)
	c.count -= 1
	l.unlock

	Util.noop(r.fuzz(work))
      }
    }
  }
  def entries: Seq[Entry] = List(reagent, lock)
}
