package chemistry.bench.benchmarks

import chemistry._
import chemistry.bench._
import chemistry.bench.competition._
import chemistry.Util._

object StackTransfer extends Benchmark {
  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    for (_ <-1 to iters) {
      Util.noop(r.fuzz(work * 3))
    }
  }

  private object reagent extends Entry {
    def name = "reagent"
    type S = (TreiberStack[AnyRef], TreiberStack[AnyRef])
    def setup = (new TreiberStack, new TreiberStack)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p
      val moveRight = s1.pop >=> s2.push
      val moveLeft  = s2.pop >=> s1.push

      for (_ <- 1 to iters) {
	val (sFrom, sTo, move) = 
	  if (r.next(2) == 0)
	    (s1, s2, moveRight)
	  else
	    (s2, s1, moveLeft)

	sFrom.push ! SomeData
	Util.noop(r.fuzz(work))
	move ! ()
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryPop ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }

  private object rElim extends Entry {
    def name = "rElim"
    type S = (EliminationStack[AnyRef], EliminationStack[AnyRef])
    def setup = (new EliminationStack, new EliminationStack)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p
      val moveRight = s1.pop >=> s2.push
      val moveLeft  = s2.pop >=> s1.push

      for (_ <- 1 to iters) {
	val (sFrom, sTo, move) = 
	  if (r.next(2) == 0)
	    (s1, s2, moveRight)
	  else
	    (s2, s1, moveLeft)

	sFrom.push ! SomeData
	Util.noop(r.fuzz(work))
	move ! ()
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryPop ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }

  private object lock extends Entry {
    def name = "lock"
    type S = (LockStack[AnyRef], LockStack[AnyRef])
    def setup = (new LockStack, new LockStack)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p

      for (_ <- 1 to iters) {
	val (sFrom, sTo) = if (r.next(2) == 0) (s1, s2) else (s2, s1)

	sFrom.push(SomeData)

	Util.noop(r.fuzz(work))

	s1.lock.lock
	s2.lock.lock
	sTo.push(sFrom.pop)
	s2.lock.unlock
	s1.lock.unlock

	Util.noop(r.fuzz(work))

	untilSome {sTo.tryPop}

	Util.noop(r.fuzz(work))
      }
    }
  }

  private object stm extends Entry {
    import akka.stm._
    def name = "stm"
    type S = (STMStack[AnyRef], STMStack[AnyRef])
    def setup = (new STMStack, new STMStack)
    def run(p: S, work: Int, iters: Int) {
      val r = new Random
      val (s1, s2) = p

      for (_ <- 1 to iters) {
	val (sFrom, sTo) = if (r.next(2) == 0) (s1, s2) else (s2, s1)

	sFrom.push(SomeData)
	Util.noop(r.fuzz(work))
	atomic {
	  sTo.push(sFrom.pop)
	}
	Util.noop(r.fuzz(work))
	untilSome {sTo.tryPop}
	Util.noop(r.fuzz(work))
      }
    }
  }

//  def entries: List[Entry] = List(rElim, reagent) //List(stm,rElim,lock,reagent)
  def entries: List[Entry] = List(stm,lock,reagent) //rElim
}

