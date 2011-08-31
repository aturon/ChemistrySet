package chemistry.bench.benchmarks

import java.util.concurrent._
import java.util.concurrent.locks._

import chemistry._
import chemistry.bench._
import chemistry.bench.competition._
import chemistry.Util._

//import org.amino.ds.lockfree._

object PushPop extends Benchmark {
  private trait Generic {
    type S 
    def push(s: S, x: AnyRef)
    def tryPop(s: S): Option[AnyRef]

    def run(s: S, work: Int, iters: Int) {
//      var elims = 0
      var n: Int = 0
      if (work > 0) {
	val r = new Random
	while (n < iters) {
	  n += 1
	  push(s, SomeData)
	  Util.noop(r.fuzz(work))
	  tryPop(s)
	  Util.noop(r.fuzz(work))
	}
      } else {
	while (n < iters) {
	  n += 1
	  push(s,SomeData)
	  untilSome { tryPop(s) }
	}
      }

//      print(" %5.1f".format(100.toDouble * elims.toDouble/iters.toDouble) ++ "%")
    }
  }

  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    var n: Int = 0
    while (n < iters) {
      n += 1
      Util.noop(r.fuzz(work))
      Util.noop(r.fuzz(work))
    }
  }
  private object handElim extends Entry with Generic {
    def name = "handElim"
    type S = HandElimStack[AnyRef]
    def setup = new HandElimStack()
    def push(s: S, x: AnyRef) = s.push(x)
    def tryPop(s: S): Option[AnyRef]  = s.tryPop
  }
/*
  private object handPool extends Entry with Generic {
    def name = "handPool"
    type S = HandPoolStack[AnyRef]
    def setup = new HandPoolStack()
    def push(s: S, x: AnyRef) = s.push(x)
    def tryPop(s: S): Option[AnyRef]  = s.tryPop
  } 
  private object handPool3 extends Entry with Generic {
    def name = "handPool3"
    type S = hand3.HandPoolStack[AnyRef]
    def setup = new hand3.HandPoolStack()
    def push(s: S, x: AnyRef) = s.push(x)
    def tryPop(s: S): Option[AnyRef]  = s.tryPop
  } 
  private object handPool4 extends Entry with Generic {
    def name = "handPool4"
    type S = hand4.HandPoolStack[AnyRef]
    def setup = new hand4.HandPoolStack()
    def push(s: S, x: AnyRef) = s.push(x)
    def tryPop(s: S): Option[AnyRef]  = s.tryPop
  }
*/
  private object hand extends Entry with Generic{
    def name = "hand"
    type S = HandStack[AnyRef]
    def setup = new HandStack()
    def push(s: S, x: AnyRef) = s.push(x)
    def tryPop(s: S): Option[AnyRef]  = s.tryPop
  } 
  private object rTreiber extends Entry with Generic {
    def name = "rTreiber"
    type S = TreiberStack[AnyRef]
    def setup = new TreiberStack()
    def push(s: S, x: AnyRef) = s.push ! x
    def tryPop(s: S): Option[AnyRef]  = s.tryPop ! ()
  }
  private object rElim extends Entry with Generic {
    def name = "rElim"
    type S = EliminationStack[AnyRef]
    def setup = new EliminationStack()
    def push(s: S, x: AnyRef) = s.push ! x
    def tryPop(s: S): Option[AnyRef] =  s.tryPop ! ()    
  }
/*
  private object ebstack extends Entry {
    def name = "ebstack"
    type S = EBStack[AnyRef]
    def setup = new EBStack()
    def run(s: S, work: Int, iters: Int) {
      if (work > 0) {
	val r = new Random
	for (_ <- 1 to iters) {
	  s.push(SomeData)
	  Util.noop(r.fuzz(work))
	  s.pop
	  Util.noop(r.fuzz(work))
	}
      } else {
	for (_ <- 1 to iters) {
	  s.push(SomeData)
	  s.pop
	}
      }
    }
  } 
*/
//  def entries = List(rTreiber, rElim, hand)
//  def entries = List(rElim, rTreiber, handElim, hand, handPool)
//  def entries: List[Entry] = List(rElim, handPool, handElim)
  def entries: List[Entry] = List(rTreiber, rElim, hand, handElim)
//  def entries: List[Entry] = List(rElim, handPool, handElim, rTreiber, hand)
}
