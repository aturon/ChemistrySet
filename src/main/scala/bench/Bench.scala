// dirt simple sequential benchmarks

package chemistry

import java.util.Date
import java.util.concurrent._

private object NotApplicable extends Exception

private abstract class Benchmark {
  val iters = 1000000
  def name: String
  def hand: Unit
  def reagent: Unit
  def juc { throw NotApplicable }
}

private object Push extends Benchmark {
  private val d = new Date()
  def name = "Push"
  def hand {
    val s = new HandStack[java.util.Date]()
    for (i <- 1 to iters) s.push(d)
  }
  def reagent {		
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to iters) s.push ! d 
  }
}

private object PushPop extends Benchmark {
  private val d = new Date()
  def name = "Push/pop"
  def hand {
    val s = new HandStack[java.util.Date]()
    for (i <- 1 to iters) {
      s.push(d)
      s.push(d)
      s.tryPop
      s.tryPop
    }
  } 
  def reagent {
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to iters) {
      s.push ! d;
      s.push ! d;
      s.tryPop ! ()
      s.tryPop ! ()
    }
  }
}

private object Enq extends Benchmark {
  private val d = new Date()
  def name = "Enq"
  def hand {
    val s = new HandQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq(d)
    }
  } 
  def reagent {
    val s = new MSQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq ! d 
    }
  }
  override def juc {
    val s = new ConcurrentLinkedQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.add(d)
    }
  }
}

private object EnqDeq extends Benchmark {
  private val d = new Date()
  def name = "EnqDeq"
  def hand {
    val s = new HandQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq(d)
      s.tryDeq
    }
  } 
  def reagent {
    val s = new MSQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq ! d;
      s.tryDeq ! ()
    }
  }
  override def juc {
    val s = new ConcurrentLinkedQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.add(d)
      s.poll()
    }
  }
}

object Bench extends App {
  val trials = 5

  def bench(b: Benchmark) {
    def getTime = (new Date()).getTime
    def run(thunk: => Unit): Option[(Double,Double)] = try {
      for (i <- 1 to 3+(trials/10)) thunk  // warm up
      val times = (1 to trials).map { (_) =>
	System.gc()
	val t1 = getTime
	thunk
	val t2 = getTime	
	(t2 - t1).toDouble
      }
      val mean = times.sum/trials
      val std = scala.math.sqrt(
	times.map(x => (x - mean) * (x - mean)).sum/trials)
      val coeffOfVar = std / scala.math.abs(mean)
      Some(mean, coeffOfVar)
    } catch { 
      case _ => None
    }

    // run reagent benchmark separately, to pull out mean for normalization
    val r = run(b.reagent)
    val rm = r match { 
      case Some((m, _)) => m
      case _ => throw new Exception("Impossible")
    }

    // run the remaining benchmarks
    val metrics = List(run(b.hand), r, run(b.juc))

    // output
    print("%10.10s".format(b.name))
    metrics.map({
      case Some((m,c)) => " %7.2f %5.2f (%3.0f)".format(m, rm/m, 100 * c)
      case None => " %19s".format("N/A", "")
    }).foreach(print(_))
    println("")
  }

  println("%10.10s %19s %19s %19s".format(
    "Benchmark", "--By hand--", "--Reagent--", "--JUC--"
  ))

  bench(Push)
  bench(PushPop)
  bench(Enq)
  bench(EnqDeq)
}
