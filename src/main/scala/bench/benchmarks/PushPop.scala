package chemistry.bench.benchmarks

import java.util.concurrent._
import java.util.concurrent.locks._

import chemistry._
import chemistry.bench._
import chemistry.bench.competition._
import chemistry.Util._

object PushPop extends Benchmark {
  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    for (_ <-1 to iters) {
      Util.noop(r.fuzz(work))
      Util.noop(r.fuzz(work))
    }
  }

  private object hand extends Entry {
    def name = "hand"
    type S = HandStack[AnyRef]
    def setup = new HandStack()
    def run(s: S, work: Int, iters: Int) {
      if (work > 0) {
	val r = new Random
	for (_ <- 1 to iters) {
	  s.push(SomeData)
	  Util.noop(r.fuzz(work))
	  untilSome {s.tryPop}
	  Util.noop(r.fuzz(work))
	}
      } else {
	for (_ <- 1 to iters) {
	  s.push(SomeData)
	  untilSome {s.tryPop}
	}
      }
    }
  } 
  private object rTreiber extends Entry {
    def name = "r-treiber"
    type S = TreiberStack[AnyRef]
    def setup = new TreiberStack()
    def run(s: S, work: Int, iters: Int) {
      if (work > 0) {
	val r = new Random
	for (_ <- 1 to iters) {
	  s.push ! SomeData;
	  Util.noop(r.fuzz(work))
	  untilSome {s.tryPop ! ()}
	  Util.noop(r.fuzz(work))
	}
      } else {
	for (_ <- 1 to iters) {
	  s.push ! SomeData;
	  untilSome {s.tryPop ! ()}
	}
      }
    }
  }
  private object rElim extends Entry {
    def name = "r-elim"
    type S = EliminationStack[AnyRef]
    def setup = new EliminationStack()
    def run(s: S, work: Int, iters: Int) {
      if (work > 0) {
	val r = new Random
	for (_ <- 1 to iters) {
	  s.push ! SomeData;
	  Util.noop(r.fuzz(work))
	  untilSome {s.tryPop ! ()}
	  Util.noop(r.fuzz(work))
	}
      } else {
	for (_ <- 1 to iters) {
	  s.push ! SomeData;
	  untilSome {s.tryPop ! ()}
	}
      }
    }
  }
//  def entries = List(rTreiber, rElim, hand)
  def entries = List(rTreiber, hand)
}
