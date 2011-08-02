package chemistry

import java.util.concurrent._
import Util._

private object PushPop extends Benchmark {
  def pureWork(work: Int, iters: Int) =
    for (_ <-1 to iters) Util.noop(work * 2)

  private object hand extends Entry {
    def name = "hand"
    type S = HandStack[AnyRef]
    def setup = new HandStack()
    def run(s: S, work: Int, iters: Int) {
      for (_ <- 1 to iters) {
	s.push(SomeData)
	Util.noop(work)
	untilSome {s.tryPop}
	Util.noop(work)
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = TreiberStack[AnyRef]
    def setup = new TreiberStack()
    def run(s: S, work: Int, iters: Int) {
      for (_ <- 1 to iters) {
	s.push ! SomeData;
	Util.noop(work)
	untilSome {s.tryPop ! ()}
	Util.noop(work)
      }
    }
  }
  def entries = List(reagent, hand)
}

private object EnqDeq extends Benchmark {  
  def pureWork(work: Int, iters: Int) =
    for (_ <-1 to iters) Util.noop(work * 2)

  private object hand extends Entry {
    def name = "hand"
    type S = HandQueue[AnyRef]
    def setup = new HandQueue()
    def run(q: S, work: Int, iters: Int) {
      for (_ <- 1 to iters) {
	q.enq(SomeData)
	Util.noop(work)
	untilSome {q.tryDeq}
	Util.noop(work)
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = MSQueue[AnyRef]
    def setup = new MSQueue()
    def run(q: S, work: Int, iters: Int) {
      for (_ <- 1 to iters) {
	q.enq ! SomeData;
	Util.noop(work)
	untilSome {q.tryDeq ! ()}
	Util.noop(work)
      }
    }
  }
  private object juc extends Entry {
    def name = "juc"
    type S = ConcurrentLinkedQueue[AnyRef]
    def setup = new ConcurrentLinkedQueue()
    def run(q: S, work: Int, iters: Int) {
      for (_ <- 1 to iters) {
	q.add(SomeData)
	Util.noop(work)
	whileNull {q.poll()}
	Util.noop(work)
      }
    }
  }
  def entries = List(reagent, hand, juc)
}
