// dirt simple sequential benchmarks

package chemistry

import java.util.concurrent._
import scala.annotation.tailrec
import scala.math._

private object SomeData // a reference to put in collections
private object NotApplicable extends Exception

private case class Measurement(
  meanMillis: Double,
  coeffOfVar: Double,
  throughput: Double,
  iters: Int,
  trials: Int,
  cores: Int
)
private case class Series(name: String, ms: Seq[Measurement])
private case class BenchResult(name: String, ss: Seq[Series])

private object config {
  val maxCores = min(Runtime.getRuntime.availableProcessors, 8)
  val warmupMillis = 1000
  val benchMillis = 1000
}

private abstract class Benchmark {
  import config._
  
  private val verbose = true
  private def log(s: String) {
    if (verbose) println(s)
  }

  @inline final protected def spawnN(threads: Int)(code: => Unit) {
    Threads.spawnAndJoin((1 to threads).map(_ => (() => code)))
  }

  @inline final protected def parRepeat(threads: Int, iters: Int)(code: => Unit) {
    spawnN(threads) {
      for (i <- 1 to iters/threads) code
    }
  }

  protected def hand(cores: Int, iters: Int) { throw NotApplicable } 
  protected def juc(cores: Int, iters: Int) { throw NotApplicable } 
  def reagent(cores: Int, iters: Int): Unit
  
  private def getTime = System.nanoTime()

  private def time(thunk: => Unit): Double = {   
    System.gc()
    val t1 = getTime
    thunk
    val t2 = getTime	
    (t2 - t1).toDouble / 1000000
  }

  @tailrec protected final def untilSome[A](thunk: => Option[A]): Unit = 
    thunk match {
      case None    => untilSome(thunk)
      case Some(_) => ()
    }

  @tailrec protected final def whileNull[A <: AnyRef](thunk: => A): Unit = 
    thunk match {
      case null => whileNull(thunk)
      case _    => ()
    }

  private def runOne(f: (Int, Int) => Unit)(i: Int): Measurement = {    
    log(" - cores = %d".format(i))

    // warmup and compute estimated throughput
    val tp = {
      // warm up
      log(" - warmup")
      var iters = 128
      var warmupIters = 0
      var warmupTime: Double = 0

      while (warmupTime < warmupMillis) {
	warmupTime  += time {f(i, iters)}
	warmupIters += iters
//	log(" i %d  wi %d  wt %f".format(iters, warmupIters, warmupTime))
	iters *= 2	
      }

      warmupIters / time{f(i,warmupIters)};
    }

    val trialIters = tp * benchMillis
    var trials = 0
    var times: Seq[Double] = List()

    def mean = times.sum/trials
    def stddev = sqrt(times.map(x => (x - mean) * (x - mean)).sum/trials)
    def cov = 100 * (stddev / abs(mean))
    def throughput = trialIters / (mean * 1000)

    def trial(scale: Double) {
      trials += 1
      val runFor = (trialIters * scale).toInt
      val t = time{f(i, runFor)} / scale
      times = t +: times

      log(" - trial %2d:  s %1.1f  t %6.0f  tp %5.2f  cov %5.2f %s".format(
	trials, scale, t, trialIters / (t * 1000), cov,
        if (cov > 5) "***\u0007" else ""))
    }

    log(" - expected throughput: %5.2f".format(tp / 1000))
    log(" - trial iters: %.0f".format(trialIters))

    trial(1.0)
    trial(0.5)
    trial(1.5)

    log(" - measured throughput: %5.2f".format(throughput))
    log("")
    Measurement(mean,cov,throughput,trialIters.toInt,trials,i)
  }

  private def runSeries(name: String, f: (Int, Int) => Unit): Option[Series] = 
    try {
      log(name)
      Some(Series(name, (1 to maxCores).map(runOne(f))))
    } catch {
      case NotApplicable => {
	log(" - N/A")
	log("")
	None
      }
    }

  private def simpleName = getClass().getSimpleName().replace("$","")

  def runAll = 
    BenchResult(simpleName,
      for {
	(name, f) <- List(("reagent", reagent(_,_)),
			  ("by hand", hand(_,_)),
			  ("juc",     juc(_,_)))
	_ = log("=== %s ===".format(simpleName))
	s = runSeries(name, f)
	if s.isDefined
      } yield s.get
    )
}

private object PushPop extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandStack[AnyRef]()
    parRepeat(cores, iters) {
      s.push(SomeData)
      s.push(SomeData)
      untilSome {s.tryPop}
      untilSome {s.tryPop}
    }
  } 
  def reagent(cores: Int, iters: Int) {
    val s = new TreiberStack[AnyRef]()
    parRepeat(cores, iters) {
      s.push ! SomeData;
      s.push ! SomeData;
      untilSome {s.tryPop ! ()}
      untilSome {s.tryPop ! ()}
    }
  }
}

private object EnqDeq extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandQueue[AnyRef]()
    parRepeat(cores, iters) {
      s.enq(SomeData)
      untilSome {s.tryDeq}
    }
  } 
  def reagent(cores: Int, iters: Int) {
    val s = new MSQueue[AnyRef]()
    parRepeat(cores, iters) {
      s.enq ! SomeData;
      untilSome {s.tryDeq ! ()}
    }
  }
  override def juc(cores: Int, iters: Int) {
    val s = new ConcurrentLinkedQueue[AnyRef]()
    parRepeat(cores, iters) {
      s.add(SomeData)
      whileNull {s.poll()}
    }
  }
}

object Bench extends App {
  import config._
  
  def printSeries(s: Series) {
    print("%10.10s |".format(s.name))
    s.ms.foreach {m => 
      print(" %8.2f |".format(m.throughput))
    }
    println("")
  }

  def printSeriesN(s: Series, compTo: Double) {
    print("%10.10s |".format(s.name))
    s.ms.foreach {m => 
      print(" %8.2f |".format(m.throughput / compTo))
    }
    println("")
  }

  def printRes(br: BenchResult) {
    // raw throughput results
    println("-" * (12 + maxCores * 11))

    print("%10.10s |".format(br.name))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    println("-" * (12 + maxCores * 11))

    br.ss.foreach(printSeries(_))

    println("")

    // normalized results
    println("-" * (12 + maxCores * 11))

    print("%9.9sN |".format(br.name))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    println("-" * (12 + maxCores * 11))

    br.ss.foreach(printSeriesN(_, br.ss(0).ms(0).throughput))

    println("")
    println("")
  }
  
  List(PushPop, EnqDeq).map(_.runAll).foreach(printRes(_))
}
