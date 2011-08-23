// Benchmarking framework that attempts to deal with JVM warmup,
// automatically detect an appropriate number of iterations,
// calculates essential statistical information, and logs/displays
// results in a readable and plottable way.

package chemistry.bench

import java.io.FileWriter
import java.io.PrintWriter
import java.util.Date
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.ops._
import scala.math._
import chemistry.Util._
import chemistry.bench.benchmarks._

private object SomeData // a reference to put in collections

private object config {
  private val fmt = new java.text.SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
  val startup = new Date
  val startupString = fmt.format(startup)

  var maxCores = min(Runtime.getRuntime.availableProcessors, 8)
  val warmupMillis = 2000
  val benchMillis = 1000
  val verbose = true
}

private object log {
  private val fmt = new java.text.SimpleDateFormat("yyyy.MM.dd.HH.mm.ss")
  private val logFile = new PrintWriter(new FileWriter(
    "reports/log." ++ config.startupString))
  
  def apply(s: String) {
    val outs = "[" ++ fmt.format(new Date) ++ "] " ++ s
    if (config.verbose) println(outs)
    logFile.println(outs)
    logFile.flush
  }
}

abstract class Entry {
  import config._

  def name: String
  protected type S
  protected def setup: S
  protected def run(s: S, work: Int, iters: Int)

  // launch a list of threads in parallel, and wait till they all
  // finish, propagating exceptions and  recording timing information.
  // times reported in nanos.
  case class TimedResult[A](startTime: Long, endTime: Long, res: A)
  def timedPar[A](threads: Int)(code: => A): Seq[TimedResult[A]] = {
    val svs = (1 to threads).map(_ => 
      new SyncVar[Either[TimedResult[A],Throwable]])
    svs.foreach(sv => fork {
      val t1  = System.nanoTime
      val res = code
      val t2  = System.nanoTime
      sv.set(
	try Left(TimedResult(t1, t2, res)) 
	catch { case e => Right(e) })
    })
    svs.map(_.get).map {
      case Left(tr) => tr
      case Right(e) => throw e
    }
  }
  
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

    // do longer trials for single-threaded, since uncontended
    // "communication" will be very fast relative to spin-work
    val trialIters: Int = (totalTP * (benchMillis + (100 * i)) * 
      (if (i == 0) 10 else 1) *     			   
      (if (work > 0) 0.1 * scala.math.log(work) * scala.math.log(work)	 
       else 1)
    ).toInt
    
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

      val fmt = " %3d  %6.2f  %7.3f  %5.2f  %6.0f  %6.0f  %5.2f  %3.0f %s"
      log(fmt.format(
	trials, 
	trialIters / (concOpTime * 1000), 
	trialIters / (t * 1000), 
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
    log("          tp   raw tp    cov     tot   t-avg  t-cov   sw")
    log(" - ex%6.1f %7.1f".format(
      (trialIters/estConcOpTime) / 1000,
      (trialIters/estTime) / 1000
    ))

    while (trials < 5 || (cov(times) > 10 && trials < 25)) {
//      if (trials > 30) throw new Exception("Variance too high, quitting.")
      trial
    }

    val meanConcOpTime = mean(times) - (timePerWork * trialIters / i)
    val throughput = trialIters / (meanConcOpTime * 1000)
    val rawThroughput = trialIters / (mean(times) * 1000)

    log(" - ob%6.1f %7.1f".format(throughput, rawThroughput))
    log("")

    Measurement(
      mean(times),
      cov(times),
      throughput,
      rawThroughput,
      trialIters.toInt,
      trials,
      i)
  }

  def measureAll(work: Int, cores: Int, timePerWork: Double) = 
    EntryResult(name, (1 to cores).map(measureOne(work, timePerWork)))
}

abstract class Benchmark {
  import config._

  protected def pureWork(work: Int, iters: Int)

  protected def name = getClass().getSimpleName().replace("$","")
  protected def entries: Seq[Entry]
  def go(work: Int, cores: Int)() = {
    log("=" * 60)

    val timePerWork = 
      if (work > 0) {
	// warmup
	for (_ <- 1 to 1000)
	  time(pureWork(work, 1000000/work)) 

	val workIters = 500000000 / work
	val tpw = time(pureWork(work, workIters)) / workIters
	log("Measured nanos per units work: %6.2f  tp: %5.2f".format(
	  tpw*1000000, (tpw)/1000
	))
	log("")

	tpw
      } else 0

    BenchResult(name, work, entries.map(_.measureAll(work, cores, timePerWork)))
  }
}

object Bench extends App {
  val t1 = System.nanoTime

  var seqOnly: Boolean = false

  for (a <- args) a match {
    case "--seq" => seqOnly = true
  }

  log("Beginning benchmark")
  log("  max cores: %d".format(config.maxCores))
  log("")

  private val seqBenches = for {
    b <- List(PushPop)
  } yield (b, 0, 1)
  private val concBenches = for {
//    b <- List(PushPop, EnqDeq, IncDec)
//    w <- List(100, 250)
    b <- List(PushPop)
//    w <- List(316, 562)
//    w <- List(0) ++ (for (i <- 0 to 15) yield pow(10, 1+i.toDouble * 0.25).toInt)
    w <- (0 to 1000 by 50)
  } yield (b, w, config.maxCores)

//  val benches = if (seqOnly) seqBenches else seqBenches ++ concBenches
  val benches = concBenches

  private val results = for {
    (b,w,c) <- benches
    r = b.go(w,c)
    _ = r.report(config.startupString)
    _ = r.report("latest")
    _ = r.display
  } yield r

  val t2 = System.nanoTime

  log("")
  log("Finished in %.1f minutes".format(
    (t2-t1).toDouble / 1000 / 1000 / 1000 / 60))

  log("")
  results.foreach(_.display)

}
