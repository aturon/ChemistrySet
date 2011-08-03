package chemistry.bench

import java.util.concurrent._
import java.util.concurrent.locks._
import chemistry._
import chemistry.Util._

private object PushPop extends Benchmark {
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
      val r = new Random
      for (_ <- 1 to iters) {
	s.push(SomeData)
	Util.noop(r.fuzz(work))
	untilSome {s.tryPop}
	Util.noop(r.fuzz(work))
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = TreiberStack[AnyRef]
    def setup = new TreiberStack()
    def run(s: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	s.push ! SomeData;
	Util.noop(r.fuzz(work))
	untilSome {s.tryPop ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }
  def entries = List(reagent, hand)
}

private object EnqDeq extends Benchmark {  
  def pureWork(work: Int, iters: Int) = {
    val r = new Random
    for (_ <-1 to iters) {
      Util.noop(r.fuzz(work))
      Util.noop(r.fuzz(work))
    }
  }

  private object hand extends Entry {
    def name = "hand"
    type S = HandQueue[AnyRef]
    def setup = new HandQueue()
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	q.enq(SomeData)
	Util.noop(r.fuzz(work))
	untilSome {q.tryDeq}
	Util.noop(r.fuzz(work))
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = MSQueue[AnyRef]
    def setup = new MSQueue()
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	q.enq ! SomeData;
	Util.noop(r.fuzz(work))
	untilSome {q.tryDeq ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }
  private object lagging extends Entry {
    def name = "lagging-ra"
    type S = LaggingQueue[AnyRef]
    def setup = new LaggingQueue()
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	q.enq ! SomeData;
	Util.noop(r.fuzz(work))
	untilSome {q.tryDeq ! ()}
	Util.noop(r.fuzz(work))
      }
    }
  }
  private object juc extends Entry {
    def name = "juc"
    type S = ConcurrentLinkedQueue[AnyRef]
    def setup = new ConcurrentLinkedQueue()
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	q.add(SomeData)
	Util.noop(r.fuzz(work))
	whileNull {q.poll()}
	Util.noop(r.fuzz(work))
      }
    }
  }
  private object lock extends Entry {
    def name = "lock"
    type S = (java.util.LinkedList[AnyRef], ReentrantLock)
    def setup = (new java.util.LinkedList(), new ReentrantLock)
    def run(s: S, work: Int, iters: Int) {
      val r = new Random
      val q = s._1
      val l = s._2
      for (_ <- 1 to iters) {
	l.lock
	q.add(SomeData)
	l.unlock

	Util.noop(r.fuzz(work))

	l.lock
	whileNull {q.poll()}
	l.unlock

	Util.noop(r.fuzz(work))
      }
    }
  }
  private object fc extends Entry {
    def name = "fc"
    type S = FCQueue
    def setup = new FCQueue
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      for (_ <- 1 to iters) {
	q.enqueue(SomeData)
	Util.noop(r.fuzz(work))
	val res = q.dequeue
	if (res == null) println("**** got null ****")
//	whileNull {q.dequeue}
	Util.noop(r.fuzz(work))
      }
    }
  }
  private object optimistic extends Entry {
    def name = "optimistic"
    type S = OptimisticLinkedQueue[AnyRef]
    def setup = new OptimisticLinkedQueue
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      val id = Thread.currentThread.getId.toInt % config.maxCores
      for (_ <- 1 to iters) {
	q.offer(SomeData)
	Util.noop(r.fuzz(work))
	whileNull {q.poll()}
	Util.noop(r.fuzz(work))
      }
    }
  }
/*
  private object basket extends Entry {
    def name = "basket"
    type S = BasketsQueue[AnyRef]
    def setup = new BasketsQueue(config.maxCores)
    def run(q: S, work: Int, iters: Int) {
      val r = new Random
      val id = Thread.currentThread.getId.toInt % config.maxCores
      for (_ <- 1 to iters) {
	q.enq(SomeData, id)
	Util.noop(r.fuzz(work))
	whileNull {q.deq(id)}
	Util.noop(r.fuzz(work))
      }
    }
  }
*/

  def entries = List(reagent, hand, juc, lock)
  // optimistic throws NullPointerException
  // fc livelocks
  // basket is not licensed for distribution

}


object Bench extends App {
  val t1 = System.nanoTime

  log("Beginning benchmark")
  log("")

  // val benchmarks = List(
  //   (PushPop, 100)
  //   (PushPop, 250)
  //   (EnqDeq,  100)

  private val results = for {
    bench <- List(PushPop, EnqDeq)
    work  <- List(100, 250)
    r = bench.go(work)
    _ = r.reportTP(config.startupString)
    _ = r.reportTP("latest")
    _ = r.reportRTP(config.startupString)
    _ = r.reportRTP("latest")
    _ = r.display
  } yield r

  val t2 = System.nanoTime

  log("")
  log("Finished in %.1f minutes".format(
    (t2-t1).toDouble / 1000 / 1000 / 1000 / 60))

  log("")
  results.foreach(_.display)

}
