// Misc utilities used throughout the Chemistry Set.  Not exported.

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

  def mean(ds: Seq[Double]): Double = ds.sum/ds.length
  def stddev(ds: Seq[Double]): Double = {
    val m = mean(ds)
    sqrt(ds.map(d => (d-m) * (d-m)).sum/ds.length)
  }
  def cov(ds: Seq[Double]): Double = 100 * (stddev(ds) / abs(mean(ds)))
  
  // compute6 from j.u.c.
  def noop(times: Int = 1): Int = {
    var seed: Int = 1
    var n: Int = times
    while (seed == 1 || n > 0) { // need to inspect seed or is optimized away
      seed = seed ^ (seed << 1)
      seed = seed ^ (seed >>> 3)
      seed = seed ^ (seed << 10)
      n -= 1
    }
    seed
  }

  // Handy exception to throw at unreachable code locations
  object Impossible extends Exception

  object Implicits {
    implicit def functionToPartialFunction[A,B](f: A => B):
      PartialFunction[A,B] = 
      new PartialFunction[A,B] {
	def isDefinedAt(x:A) = true
	def apply(x:A) = f(x)
      }
  }

  // Untagged unions, due to Miles Sabin
  // see www.chuusai.com/2011/06/09/scala-union-types-curry-howard/
  type Not[A] = A => Nothing
  type NotNot[A] = Not[Not[A]]
  type UntaggedSum[A,B] = { type F[X] = NotNot[X] <:< Not[Not[A] with Not[B]] }
}

// an unsynchronized, but thread-varying RNG
private final class Random(var seed: Long = 1) {
  def nextSeed {
    seed = Random.nextSeed(seed)
  }

  def next(max: Int): Int = {    
    nextSeed
    Random.scale(seed, max)
  }

  def fuzz(around: Int, percent: Int = 50): Int = {
    val max = (around * percent) / 100
    math.max(around + next(max) - (max >> 1), 0)
  }
}
private object Random {
  def nextSeed(oldSeed: Long): Long = {
    var seed = oldSeed
    seed = seed ^ (seed << 13)
    seed = seed ^ (seed >>> 7)
    seed = seed ^ (seed << 17)
    seed
  }
  def scale(seed: Long, max: Int): Int = {
    if (max <= 0) max else {
      val r = (seed % max).toInt
      if (r < 0) -r else r
    }
  }
}
