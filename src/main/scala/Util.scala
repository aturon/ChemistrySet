// Misc utilities used throughout the Chemistry Set.  Not exported

package chemistry

import scala.util._
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.ops._
import scala.math._
import java.lang.Thread

private object Util {
  def undef[A]: A = throw new Exception()

  @inline def nanoToMilli(nano: Long): Double = nano / 1000000

  def time(thunk: => Unit): Double = {   
    val t1 = System.nanoTime
    thunk
    val t2 = System.nanoTime
    nanoToMilli(t2 - t1)
  }

  @tailrec def untilSome[A](thunk: => Option[A]): Unit = 
    thunk match {
      case None    => untilSome(thunk)
      case Some(_) => ()
    }

  @tailrec def whileNull[A <: AnyRef](thunk: => A): Unit = 
    thunk match {
      case null => whileNull(thunk)
      case _    => ()
    }

  def fork(code: => Unit) {
    val runnable = new Runnable { def run() { code } }
    (new Thread(runnable)).start()
  }

  // launch a list of threads in parallel, and wait till they all
  // finish, propagating exceptions.  Also records timing information.
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

  def mean(ds: Seq[Double]): Double = ds.sum/ds.length
  def stddev(ds: Seq[Double]): Double = {
    val m = mean(ds)
    sqrt(ds.map(d => (d-m) * (d-m)).sum/ds.length)
  }
  def cov(ds: Seq[Double]): Double = 100 * (stddev(ds) / abs(mean(ds)))
  
  // compute6 from j.u.c.
  @inline def noop(times: Int = 1) {
    var seed = 1;
    for (_ <- 1 to times) {
      seed = seed ^ (seed << 1)
      seed = seed ^ (seed >>> 3)
      seed = seed ^ (seed << 10)
    }
    seed
  }
}

