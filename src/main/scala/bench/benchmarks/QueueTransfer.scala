package chemistry.bench.benchmarks

import chemistry._
import chemistry.bench._
import chemistry.bench.competition._
import chemistry.Util._

object QueueTransfer extends Benchmark {
  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    for (_ <-1 to iters) {
      Util.noop(r.fuzz(work * 3))
    }
  }

  private object reagent extends Entry {
    def name = "reagent"
    type S = (MSQueue[AnyRef], MSQueue[AnyRef])
    def setup = (new MSQueue, new MSQueue)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p
      val moveRight = s1.deq >=> s2.enq
      val moveLeft  = s2.deq >=> s1.enq

      for (_ <- 1 to iters) {
	val (sFrom, sTo, move) = 
	  if (r.next(2) == 0)
	    (s1, s2, moveRight)
	  else
	    (s2, s1, moveLeft)

	sFrom.enq ! SomeData
	Util.noop(r.fuzz(work))
	move ! ()
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryDeq ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }

  private object simple extends Entry {
    def name = "simple"
    type S = (SimpleQueue[AnyRef], SimpleQueue[AnyRef])
    def setup = (new SimpleQueue, new SimpleQueue)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p
      val moveRight = s1.deq >=> s2.enq
      val moveLeft  = s2.deq >=> s1.enq

      for (_ <- 1 to iters) {
	val (sFrom, sTo, move) = 
	  if (r.next(2) == 0)
	    (s1, s2, moveRight)
	  else
	    (s2, s1, moveLeft)

	sFrom.enq ! SomeData
	Util.noop(r.fuzz(work))
	move ! ()
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryDeq ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }

  private object lock extends Entry {
    def name = "lock"
    type S = (LockQueue[AnyRef], LockQueue[AnyRef])
    def setup = (new LockQueue, new LockQueue)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p

      for (_ <- 1 to iters) {
	val (sFrom, sTo) = if (r.next(2) == 0) (s1, s2) else (s2, s1)

	sFrom.enq(SomeData)

	Util.noop(r.fuzz(work))

	s1.lock.lock
	s2.lock.lock
	sTo.enq(sFrom.deq)
	s2.lock.unlock
	s1.lock.unlock

	Util.noop(r.fuzz(work))

	untilSome {sTo.tryDeq}

	Util.noop(r.fuzz(work))
      }
    }
  }

  private object stm extends Entry {
    import akka.stm._
    def name = "stm"
    type S = (STMQueue[AnyRef], STMQueue[AnyRef])
    def setup = (new STMQueue, new STMQueue)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p

      for (_ <- 1 to iters) {
	val (sFrom, sTo) = if (r.next(2) == 0) (s1, s2) else (s2, s1)

	sFrom.enq(SomeData)
	Util.noop(r.fuzz(work))
	atomic {
	  sTo.enq(sFrom.deq)
	}
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryDeq}
	Util.noop(r.fuzz(work))
      }
    }
  }

//  def entries: List[Entry] = List(stm,reagent,lock,simple)

  // for PLDI:
  def entries: List[Entry] = List(stm,reagent,lock)
}

