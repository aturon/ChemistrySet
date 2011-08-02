// benchmarking framework that attempts to deal with JVM warmup,
// automatically detect an appropriate number of iterations,
// calculates essential statistical information, and logs/displays
// results in a readable way

package chemistry

import java.util.Date
import scala.annotation.tailrec
import scala.math._
import Util._

private object SomeData // a reference to put in collections

private object config {
  val maxCores = min(Runtime.getRuntime.availableProcessors, 8)
  val warmupMillis = 2000
  val benchMillis = 1000
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

private abstract class Entry {
  import config._
  import Util._

  def name: String
  protected type S
  protected def setup: S
  protected def run(s: S, work: Int, iters: Int)
  
  private def measureOne(work: Int, timePerWork: Double)(i: Int): Measurement = {
    log(getClass().getSimpleName().replace("$"," "))
    log(" - threads: %d".format(i))
    log(" - work: %d".format(work))

    def time(iters: Int): (Double, Double, Seq[Double]) = {      
      System.gc()
      val s = setup
      val timings = timedPar(i) { run(s, work, iters/i) }
      val totalTime = 
	nanoToMilli(timings.map(_.endTime).max - timings.map(_.startTime).min)
      val startWindow = 
	nanoToMilli(timings.map(_.startTime).max - timings.map(_.startTime).min)
      val threadTimes = timings.map(t => nanoToMilli(t.endTime - t.startTime))
      (totalTime, startWindow, threadTimes)
    }

    // warmup and compute estimated throughput
    val totalTP = {
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

      warmupIters / time(warmupIters)._1
    }

    val trialIters: Int = (totalTP * (benchMillis + (250 * i))).toInt
//    val trialIters: Int = (tp * benchMillis).toInt
    
    val estTime = time(trialIters)._1
    val estConcOpTime = estTime - (timePerWork * trialIters / i)

    var trials = 0
    var times: Seq[Double] = List()
    var workTimes: Seq[Double] = List()

    def trial {
      trials += 1      

      val (t, startWindow, threadTimes) = time(trialIters) 
      times = t +: times

      val concOpTime = t - (timePerWork * trialIters / i)

      val fmt = " %3d  %6.2f  %5.2f  %6.0f  %6.0f  %5.2f  %3.0f %s"
      log(fmt.format(
	trials, 
	trialIters / (concOpTime * 1000), 
	cov(times), 
	t,
	mean(threadTimes),
	cov(threadTimes),
	startWindow,
        if (cov(times) > 10 || startWindow > 5) "***" else ""))
    }

    log(" - iters: %d".format(trialIters))
    log(" - est time: %6.0f  conc op: %6.0f  simulated work: %4.1f".format(
      estTime, estConcOpTime, 100 * (1-estConcOpTime/estTime)) ++ "%")
    log("          tp    cov     tot   t-avg  t-cov   sw")
    log(" - ex %6.2f".format((trialIters/estConcOpTime) / 1000))

//    for (_ <- 1 to 5) trial
    while (trials < 5 || cov(times) > 10) trial

//    if (cov(times) > 10) for (_ <- 1 to 10) print("\u0007")

    val meanConcOpTime = mean(times) - (timePerWork * trialIters / i)
    val throughput = trialIters / (meanConcOpTime * 1000)


    log(" - ob %6.2f".format(throughput))
    log("")

    Measurement(
      mean(times),
      cov(times),
      throughput,
      trialIters.toInt,
      trials,
      i)
  }

  def measureAll(work: Int, timePerWork: Double) = 
    EntryResult(name, (1 to maxCores).map(measureOne(work, timePerWork)))
}

private abstract class Benchmark {
  import config._

  def pureWork(work: Int, iters: Int)

  def name = getClass().getSimpleName().replace("$","")
  def entries: Seq[Entry]
  def go(work: Int) = {
    log("=" * 60)

    // warmup
    for (_ <- 1 to 100)
      Util.time(pureWork(work, 1000000/work)) 

    val workIters = 100000000 / work
    val timePerWork = Util.time(pureWork(work, workIters)) / workIters
    log("Measured nanos per units work: %6.2f  tp: %5.2f".format(
      timePerWork*1000000, (1/timePerWork)/1000
    ))
    log("")

    BenchResult(name, work, entries.map(_.measureAll(work, timePerWork)))
  }
}

object Bench extends App {  
  private val results = for {
    bench <- List(PushPop, EnqDeq)
    work  <- List(50,500,5000)
  } yield bench.go(work)
  results.foreach(_.display)
}
