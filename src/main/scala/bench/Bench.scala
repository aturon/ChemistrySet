// dirt simple sequential benchmarks

package chemistry

import java.util.concurrent._
import scala.annotation.tailrec
import scala.math._
import Util._

private object SomeData // a reference to put in collections

private case class Measurement(
  meanMillis: Double,
  coeffOfVar: Double,
  throughput: Double,
  iters: Int,
  trials: Int,
  cores: Int
) {
  def print = 
    print(" %8.2f |".format(m.throughput))
  def printN(compTo: Double) = 
    print(" %8.2f |".format(m.throughput / compTo))
}

private case class EntryResult(name: string, ms: Seq[Measurement]) {
  def print {
    print("%10.10s |".format(s.name))    
    ms.foreach(_.print)
    println("")
  }
  def printN(compTo: Double) {
    print("%10.10s |".format(s.name))
    ms.foreach(_.printN(compTo))
    println("")
  }
}

private case class BenchResult(name: string, es: Seq[EntryResult]) {
  import config._

  def print {
    // raw throughput results
    println("-" * (12 + maxCores * 11))

    print("%10.10s |".format(name))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    println("-" * (12 + maxCores * 11))

    es.foreach(_.print)

    // normalized results
    println("-" * (12 + maxCores * 11))

    es.foreach(_.printN(es(0).ms(0).throughput))

    println("")
    println("")
  }
}

private object config {
  val maxCores = min(Runtime.getRuntime.availableProcessors, 1)
  val warmupMillis = 1000
  val benchMillis = 1000
  val verbose = true
}

private abstract class Entry {
  def name = getClass().getSimpleName().replace("$","")
  type S
  def setup: S
  def apply(s: S, iters: Int)
}

private abstract class Benchmark {
  import config._

  def name = getClass().getSimpleName().replace("$","")
  
  private def log(s: String) {
    if (verbose) println(s)
  }

  private def measure(e: BenchEntry)(i: Int): Measurement = {    
    def time(iters: Int, scale: Double = 1.0): (Seq[Double], Double) = {
      setup
      System.gc()
//      val (unscaledTimes, startup) = 
//	par(i) { Util.time(f((scale * iters/i).toInt)) }
//      (unscaledTimes.map(_ / scale), startup)
      (List(Util.time(f(iters))), 0)
    }

    log(" - cores = %d".format(i))

    // warmup and compute estimated throughput
    val tp = {
      // warm up
      log(" - warmup")
      var iters = 128
      var warmupIters = 0
      var warmupTime: Double = 0

      while (warmupTime < warmupMillis) {
	warmupTime  += time(iters)._1.sum
	warmupIters += iters
//	log(" i %d  wi %d  wt %f".format(iters, warmupIters, warmupTime))
	iters *= 2	
      }

      warmupIters / time(warmupIters)._1.sum;
    }

    val trialIters: Int = (tp * benchMillis).toInt
    var trials = 0
    var times: Seq[Double] = List()

    def mean = times.sum/trials
    def stddev = sqrt(times.map(x => (x - mean) * (x - mean)).sum/trials)
    def cov = 100 * (stddev / abs(mean))
    def throughput = trialIters / (mean * 1000)

    def trial(scale: Double) {
      trials += 1      
      val (ts, startup) = time(trialIters, scale) 
      times = ts.sum +: times

      log(" - trial %2d: %5.2f  st %4.1f  cov %5.2f  t %6.0f <= %s %s".format(
	trials, 
	trialIters / (ts.sum * 1000), 
	startup, 
	cov, 
	ts.sum,
	ts.map(t => "%5.0f".format(t)).mkString(" "),
        if (cov > 5 || startup > 50) "***\u0007" else ""))
    }

    log(" - expected throughput: %5.2f".format(tp / 1000))
    log(" - trial iters: %d".format(trialIters))

    trial(1.0)
    trial(0.5)
    trial(1.5)

    log(" - measured throughput: %5.2f".format(throughput))
    log("")
    Measurement(mean,cov,throughput,trialIters.toInt,trials,i)
  }

  def go = (name, entries.map(entry =>
    (entry.name, (1 to maxCores).map(measure(entry)))))
}

private object PushPop extends Benchmark {
  private var hs:  = null
  private var rs: TreiberStack[AnyRef] = null

  def setup {
    hs = 
    rs = new TreiberStack()
  }

  object hand extends Entry {
    type S = HandStack[AnyRef]
    def setup = new HandStack()
    def apply(s: S, iters: Int) {
      for (_ <- 1 to iters) {
	s.push(SomeData)
	s.push(SomeData)
	untilSome {s.tryPop}
	untilSome {s.tryPop}
      }
    }
  } 
  object reagent extends Entry(iters: Int) {
    val s = rs
    for (_ <- 1 to iters) {
      s.push ! SomeData;
      s.push ! SomeData;
      untilSome {s.tryPop ! ()}
      untilSome {s.tryPop ! ()}
    }
  }
}

private object EnqDeq extends Benchmark {
  private var hq: HandQueue[AnyRef] = null
  private var rq: MSQueue[AnyRef] = null
  private var jq: ConcurrentLinkedQueue[AnyRef] = null

  def setup {
    hq = new HandQueue[AnyRef]()
    rq = new MSQueue[AnyRef]()
    jq = new ConcurrentLinkedQueue[AnyRef]()
  }
  
  // override def hand(iters: Int) {
  //   val q = hq
  //   for (_ <- 1 to iters) {
  //     q.enq(SomeData)
  //     untilSome {q.tryDeq}
  //   }
  // } 
  def reagent(iters: Int) {
    val q = rq
    for (_ <- 1 to iters) {
      q.enq ! SomeData;
      untilSome {q.tryDeq ! ()}
    }
  }
  // override def juc(iters: Int) {
  //   val q = jq
  //   for (_ <- 1 to iters) {
  //     q.add(SomeData)
  //     whileNull {q.poll()}
  //   }
  // }
}

object Bench extends App {  
//  List(PushPop, EnqDeq).map(_.runAll).foreach(printRes(_))
  EnqDeq.go.print
}
