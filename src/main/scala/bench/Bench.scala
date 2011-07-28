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

private abstract class Benchmark {
  private val verbose = true
  def log(s: String) {
    if (verbose) println(s)
  }

  private val maxCores = 1
  private val warmupMillis = 2000
  private val benchMillis = 2000

  protected def hand(cores: Int, iters: Int) { throw NotApplicable } 
  protected def juc(cores: Int, iters: Int) { throw NotApplicable } 
  def reagent(cores: Int, iters: Int): Unit
  
  private def getTime = System.nanoTime()

  private def time(thunk: => Unit): Double = {   
    for (i <- 1 to 2) {
//      log(" ... explicit GC")
      System.gc()
    }
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

    // warm up
    log(" - warmup")
    var iters = 128
    var warmupIters = 0
    var warmupTime: Double = 0

    while (warmupTime < warmupMillis) {
      warmupTime  += time {f(i, iters)}
      warmupIters += iters
      iters *= 2
      log(warmupIters.toString)
    }

    val tp = warmupIters / time{f(i,warmupIters)};
    log(" - expected throughput: %5.2f".format(tp / 1000))
    log(" - trial iters: %.0f".format(tp * benchMillis))

    var trials = 0
    var times: Seq[Double] = List()

    def mean = times.sum/trials
    def stddev = sqrt(times.map(x => (x - mean) * (x - mean)).sum/trials)
    def cov = 100 * (stddev / abs(mean))
    def throughput = iters / (mean * 1000)

    def trial(scale: Double) {
      trials += 1
      val runFor = (tp * benchMillis * scale).toInt
      val t = time{f(i, runFor)} / scale
      times = t +: times

      log(" - trial %2d:  s %1.1f  t %6.0f  tp %5.2f  cov %5.2f".format(
	trials, scale, t, iters / (t * 1000), cov))
    }

    trial(1.0)
    trial(0.5)
    trial(1.5)

//    while (cov > 5) {
      

      // if (trials > 2 && cov > 10) {
      // 	log(" *** cov too high, restarting ***")
      // 	log("")
      // 	return None
      // }
//    }

    log("")
    Measurement(mean,cov,throughput,iters,trials,i)
  }

  private def runSeries(name: String, f: (Int, Int) => Unit): Option[Series] = 
    try {
      log(name)
      Some(Series(name, (1 to maxCores).map(runOne(f))))
    } catch {
      case NotApplicable => {
	if (verbose) {
	  println(" - N/A")
	  println("")
	}
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

private object Push extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandStack[AnyRef]()
    for (i <- 1 to iters) s.push(SomeData)
  }
  def reagent(cores: Int, iters: Int) {		
    val s = new TreiberStack[AnyRef]()
    for (i <- 1 to iters) s.push ! SomeData 
  }
}

private object PushPop extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandStack[AnyRef]()
    for (i <- 1 to iters) {
      s.push(SomeData)
      s.push(SomeData)
      untilSome {s.tryPop}
      untilSome {s.tryPop}
    }
  } 
  def reagent(cores: Int, iters: Int) {
    val s = new TreiberStack[AnyRef]()
    for (i <- 1 to iters) {
      s.push ! SomeData;
      s.push ! SomeData;
      untilSome {s.tryPop ! ()}
      untilSome {s.tryPop ! ()}
    }
  }
}

private object Enq extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.enq(SomeData)
    }
  } 
  def reagent(cores: Int, iters: Int) {
    val s = new MSQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.enq ! SomeData 
    }
  }
  override def juc(cores: Int, iters: Int) {
    val s = new ConcurrentLinkedQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.add(SomeData)
    }
  }
}

private object EnqDeq extends Benchmark {
  override def hand(cores: Int, iters: Int) {
    val s = new HandQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.enq(SomeData)
      untilSome {s.tryDeq}
    }
  } 
  def reagent(cores: Int, iters: Int) {
    val s = new MSQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.enq ! SomeData;
      untilSome {s.tryDeq ! ()}
    }
  }
  override def juc(cores: Int, iters: Int) {
    val s = new ConcurrentLinkedQueue[AnyRef]()
    for (i <- 1 to iters) {
      s.add(SomeData)
      whileNull {s.poll()}
    }
  }
}

object Bench extends App {
/*
    print("%10.10s".format(b.name))
    metrics.map({
      case Some((m,c,t)) => 
	" %7.2f %5.2f (%3.0f)".format(t, rm/m, 100 * c)
      case None => 
	" %19s".format("N/A", "")
    }).foreach(print(_))
*/

/*
  def printBench() {
    println()
    println("-" * (12 + maxCores * 11))

    print("%10.10s |".format("Cores"))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    println("-" * (12 + maxCores * 11))

    println(rm)

    println("")
  }
*/

  def printRes(br: BenchResult) {
    
  }
  
  Push.runAll
//  List(Push, PushPop, Enq, EnqDeq).map(_.runAll).foreach(printRes(_))
}
