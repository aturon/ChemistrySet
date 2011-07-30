// Misc utilities used throughout the Chemistry Set.  Not exported

package chemistry

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.ops._

private object Util {
  def undef[A]: A = throw new Exception()

  def time(thunk: => Unit): Double = {   
    val t1 = System.nanoTime()
    thunk
    val t2 = System.nanoTime()
    (t2 - t1).toDouble / 1000000
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

  // launch a list of threads in parallel, and wait till they all
  // finish, propagating exceptions.  Also records the longest
  // difference in thread startup time.
  def par[A](threads: Int)(code: => A): (Seq[A], Double) = {
    val svs = (1 to threads).map(_ => new SyncVar[Either[(Long, A),Throwable]])
    val start = System.nanoTime
    svs.foreach(sv => spawn {
      val threadStart = System.nanoTime
      sv.set(try Left(threadStart - start, code) catch { case e => Right(e) })
    })
    val resPairs = svs.map(_.get).map {
      case Left((t, a)) => (t,a)
      case Right(e) => throw e
    }
    val startupDelays = resPairs.map(_._1)
    val results = resPairs.map(_._2)
    (results, (startupDelays.max - startupDelays.min).toDouble / 1000000)
  }
}

