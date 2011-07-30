// dirt simple sequential benchmarks

package chemistry

import java.util.Date
import java.util.concurrent._
import scala.annotation.tailrec
import scala.math._
import Util._

private object SomeData // a reference to put in collections

private object config {
  val maxCores = min(Runtime.getRuntime.availableProcessors, 8)
  val warmupMillis = 1000
  val benchMillis = 3000
  val verbose = true

  val startup = new Date
/*
 * val fmt = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
 * fmt.format(now) = "2011.07.30.16.38.44"
 */
}

private object log {
  def apply(s: String) {
    if (config.verbose) println(s)
  }
}

private case class Measurement(
  meanMillis: Double,
  coeffOfVar: Double,
  throughput: Double,
  iters: Int,
  trials: Int,
  cores: Int
) {
  def format = " %8.2f |".format(throughput)
  def formatN(compTo: Double) = 
    " %8.2f |".format(throughput / compTo)
}

private case class EntryResult(name: String, ms: Seq[Measurement]) {
  def format =
    "%10.10s |".format(name) ++
    ms.map(_.format).mkString ++
    "  max cov: %5.2f  avg cov: %5.2f\n".format(
      ms.map(_.coeffOfVar).max,
      ms.map(_.coeffOfVar).sum / ms.length
    )

  def formatN(compTo: Double) = 
    "%10.10s |".format(name) ++
    ms.map(_.formatN(compTo)).mkString ++
    "\n"
}

private case class BenchResult(name: String, es: Seq[EntryResult]) {
  import config._

  private def hrule {
    println("-" * (12 + maxCores * 11))
  }
  def display {
    // raw throughput results
    hrule

    print("%10.10s |".format(name))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    
    hrule
    es.map(_.format).foreach(print(_))

    // normalized results
    hrule
    es.map(_.formatN(es(0).ms(0).throughput)).foreach(print(_))
    hrule   

    println("")
    println("")
  }
}

private abstract class Entry {
  import config._
  import Util._

  def name: String
  protected type S
  protected def setup: S
  protected def run(s: S, iters: Int)

  private def measureOne(i: Int): Measurement = {
    log(getClass().getSimpleName())
    log(" - threads: %d".format(i))

    def time(iters: Int): (Double, Double, Seq[Double]) = {      
      System.gc()
      val s = setup
      val timings = timedPar(i) { run(s, iters/i) }
      val totalTime = 
	nanoToMilli(timings.map(_.endTime).max - timings.map(_.startTime).min)
      val startWindow = 
	nanoToMilli(timings.map(_.startTime).max - timings.map(_.startTime).min)
      val threadTimes = timings.map(t => nanoToMilli(t.endTime - t.startTime))
      (totalTime, startWindow, threadTimes)
    }

    // warmup and compute estimated throughput
    val tp = {
      log(" - warmup")
      var iters = 128
      var warmupIters = 0
      var warmupTime: Double = 0

      while (warmupTime < warmupMillis) {
	warmupTime  += time(iters)._1
	warmupIters += iters
//	log(" i %d  wi %d  wt %f".format(iters, warmupIters, warmupTime))
	iters *= 2	
      }

      warmupIters / time(warmupIters)._1;
    }

    val trialIters: Int = (tp * (benchMillis + 500 * i)).toInt
    var trials = 0
    var times: Seq[Double] = List()

    def trial {
      trials += 1      
      val (totalTime, startWindow, threadTimes) = time(trialIters) 
      times = totalTime +: times

      val fmt = "   %1d  %5.2f  %5.2f  %6.0f  %6.0f  %5.2f  %3.0f %s"
      log(fmt.format(
	trials, 
	trialIters / (totalTime * 1000), 
	cov(times), 
	totalTime,
	mean(threadTimes),
	cov(threadTimes),
	startWindow,
        if (cov(times) > 5 || startWindow > 5) "***\u0007" else ""))
    }

    log(" - iters: %d".format(trialIters))

    log("         tp    cov     tot   t-avg  t-cov   sw")
    log(" - ex %5.2f".format(tp / 1000))
    for (_ <- 1 to 5) trial
    val throughput = trialIters / (mean(times) * 1000)
    log(" - ob %5.2f".format(throughput))
    log("")

    Measurement(
      mean(times),
      cov(times),
      throughput,
      trialIters.toInt,
      trials,
      i)
  }

  def measureAll = EntryResult(name, (1 to maxCores).map(measureOne))
}

private abstract class Benchmark {
  import config._

  def name = getClass().getSimpleName().replace("$","")
  def entries: Seq[Entry]
  def go = {
    log("================================================================")
    BenchResult(name, entries.map(_.measureAll))
  }
}

private object PushPop extends Benchmark {
  private object hand extends Entry {
    def name = "hand"
    type S = HandStack[AnyRef]
    def setup = new HandStack()
    def run(s: S, iters: Int) {
      for (_ <- 1 to iters) {
	s.push(SomeData)
	s.push(SomeData)
	untilSome {s.tryPop}
	untilSome {s.tryPop}
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = TreiberStack[AnyRef]
    def setup = new TreiberStack()
    def run(s: S, iters: Int) {
      for (_ <- 1 to iters) {
	s.push ! SomeData;
	s.push ! SomeData;
	untilSome {s.tryPop ! ()}
	untilSome {s.tryPop ! ()}
      }
    }
  }
  def entries = List(reagent, hand)
}

private object EnqDeq extends Benchmark {  
  private object hand extends Entry {
    def name = "hand"
    type S = HandQueue[AnyRef]
    def setup = new HandQueue()
    def run(q: S, iters: Int) {
      for (_ <- 1 to iters) {
	q.enq(SomeData)
	untilSome {q.tryDeq}
      }
    }
  } 
  private object reagent extends Entry {
    def name = "reagent"
    type S = MSQueue[AnyRef]
    def setup = new MSQueue()
    def run(q: S, iters: Int) {
      for (_ <- 1 to iters) {
	q.enq ! SomeData;
	untilSome {q.tryDeq ! ()}
      }
    }
  }
  private object juc extends Entry {
    def name = "juc"
    type S = ConcurrentLinkedQueue[AnyRef]
    def setup = new ConcurrentLinkedQueue()
    def run(q: S, iters: Int) {
      for (_ <- 1 to iters) {
	q.add(SomeData)
	whileNull {q.poll()}
      }
    }
  }
  def entries = List(reagent, hand, juc)
}

object Bench extends App {  
  List(PushPop, EnqDeq).map(_.go).foreach(_.display)
//  List(EnqDeq).map(_.go).foreach(_.display)
}
