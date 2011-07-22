// The reagent implementation

package chemistry

import java.util.concurrent.atomic._
import java.util.concurrent.locks._
import scala.annotation.tailrec

private object Util {
  def undef[A]: A = throw new Exception()
}

sealed private abstract class LogEntry
private case class CASLog[A](r: AtomicReference[A], ov: A, nv: A) 
	     extends LogEntry

sealed private class Transaction {}

private abstract class WaiterStatus
private case object Catalyst extends WaiterStatus
private case object Waiting  extends WaiterStatus
private case object Finished extends WaiterStatus

sealed private abstract class AbsWaiter
sealed private case class Waiter[A](
  r: Reagent[A], var answer: AnyRef,
  status: Ref[WaiterStatus], 
  thread: Thread
) extends AbsWaiter

private abstract class Failed
private case object ShouldBlock extends Failed
private case object ShouldRetry extends Failed

sealed abstract class Reagent[+A] {
  // "doFn" in the CML implementation
  private[chemistry] def tryReact(trans: Transaction): Any 
  // "blockFn" in the CML implementation
  private[chemistry] def logWait(w: AbsWaiter): Unit

  final def ! : A = {
    def slowPath: A = {
      val status = Ref[WaiterStatus](Waiting)
      val recheck: Reagent[A] = for {
	_ <- status.cas(Waiting, Finished)
	r <- this
      } yield r
      val waiter = Waiter(this, null, status, Thread.currentThread())
      @tailrec def recheckThenBlock: A = status.get() match {
	case Finished => waiter.answer.asInstanceOf[A]
	case _ => recheck.tryReact(null) match {
	  case ShouldRetry => recheckThenBlock // should backoff
	  case ShouldBlock => LockSupport.park(waiter); recheckThenBlock
	  case result => result.asInstanceOf[A] 
	}
      }
      logWait(waiter)
      recheckThenBlock
    }

    // first try "fast path": react without creating/enqueuing a waiter
    tryReact(null) match {
      case (_ : Failed) => slowPath
      case result => result.asInstanceOf[A] 
    }
  }

  @inline final def !? : Option[A] = {
    tryReact(null) match {
      case ShouldRetry => None	// should we actually retry here?  if
				// we do, more informative: a failed
				// attempt entails a linearization
				// where no match was possible.  but
				// could loop!
      case ShouldBlock => None
      case result => Some(result.asInstanceOf[A])
    }
  }

  @inline final def flatMap[B](k: A => Reagent[B]): Reagent[B] = 
    RBind(this, k)
  @inline final def map[B](f: A => B): Reagent[B] = 
    RBind(this, (x: A) => ret(f(x)))
  @inline final def >>[B](k: Reagent[B]): Reagent[B] = 
    RBind(this, (_:A) => k)

  // @inline final def <+>[C <: A, D >: B](
  //   that: Reagent[C,D]): Reagent[C,D] = 
  //   new Reagent(this.choices ++ that.choices)

}

private sealed case class RBind[A,B](c: Reagent[A], k: A => Reagent[B]) extends Reagent[B] {
  @inline final def tryReact(trans: Transaction): Any = 
    c.tryReact(trans) match {
      case ShouldBlock => ShouldBlock
      case ShouldRetry => ShouldRetry
      case res => k(res.asInstanceOf[A]).tryReact(trans)
    }

  @inline final def logWait(w: AbsWaiter) {
//      a.logWait(w)
//      b.logWait(w)
  }
}

private sealed case class ret[A](pure: A) extends Reagent[A] {
  @inline final def tryReact(trans: Transaction): Any = pure
  @inline final def logWait(w: AbsWaiter) {}
}

object retry extends Reagent[Nothing] {
  @inline final def tryReact(trans: Transaction): Any = ShouldRetry
  @inline final def logWait(w: AbsWaiter) {}
}

object loop {
  private case class RLoop[A](c: () => Reagent[A]) extends Reagent[A] {
    @inline final def tryReact(trans: Transaction): Any = 
      c().tryReact(trans)
    @inline final def logWait(w: AbsWaiter) {}
  }
  @inline final def apply[A](c: => Reagent[A]): Reagent[A] = RLoop(() => c)
}

/*
private class Endpoint[A,B] extends Reagent[A,B] {
  var dual: Endpoint[B,A] = null
}
object SwapChan {
  def apply[A,B]: (Reagent[A,B], Reagent[B,A]) = {
    val c1 = new Endpoint[A,B]
    val c2 = new Endpoint[B,A]
    c1.dual = c2; c2.dual = c1
    (c1, c2)
  }
}
*/

class Ref[A](init: A) extends AtomicReference[A](init) {
//  private final var data = new AtomicReference[A](init)

//  private val waiters = new MSQueue[]()

  case object read extends Reagent[A] {
    @inline final def tryReact(trans: Transaction): A = get()
    @inline final def logWait(w: AbsWaiter) {}
  }

  sealed case class cas(expect: A, update: A) extends Reagent[Unit] {
    @inline final def tryReact(trans: Transaction): Unit = 
      compareAndSet(expect, update)
    @inline final def logWait(w: AbsWaiter) {}
  }
  def mkcas(ov:A,nv:A) = cas(ov,nv)  // deal with weird compiler bug

  sealed case class upd[B](f: A => (A,B)) extends Reagent[B] {
    @inline final def tryReact(trans: Transaction): B = {
      val ov = get()
      val (nv, ret) = f(ov)
      compareAndSet(ov, nv)
      ret
    }
    @inline final def logWait(w: AbsWaiter) {}
  }
}
object Ref {
  final def apply[A](init: A): Ref[A] = new Ref(init)
  final def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}
object upd {
  final def apply[A,B](r: Ref[A])(f: A => (A,B)): Reagent[B] = r.upd(f)
}
