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

private case object Impossible extends Exception

private abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

private sealed abstract class ReagentK[-A,+B] {
  private[chemistry] def tryReact(a: A, trans: Transaction): B
}

private sealed case class BindK[A,B,C](k1: A => Reagent[B], k2: ReagentK[B,C]) 
		    extends ReagentK[A,C] {
  def tryReact(a: A, trans: Transaction): C = k1(a).tryReact(trans, k2)
}

private object FinalK extends ReagentK[Any,Any] {
  // ID continuation at the moment; eventually will be responsible for kCAS
  def tryReact(a: Any, trans: Transaction): Any = a
}

sealed abstract class Reagent[+A] {
  private[chemistry] def tryReact[B](trans: Transaction, k: ReagentK[A,B]): B

  final def ! : A = {
    // we want only one global instance of FinalK, but the cost is a
    // silly typecast
    val finalk = FinalK.asInstanceOf[ReagentK[A,A]] 
    
    def slowPath: A = {
      val status = Ref[WaiterStatus](Waiting)
      val recheck: Reagent[A] = for {
	_ <- status.cas(Waiting, Finished)
	r <- this
      } yield r
      val waiter = Waiter(this, null, status, Thread.currentThread())

      //logWait(waiter)

      // written with while because scalac couldn't handle tail recursion
      while (true) status.get() match {
	case Finished => return waiter.answer.asInstanceOf[A]
	case _ => try {
	  return recheck.tryReact(null, finalk) 
	} catch {
	  case ShouldRetry => () // should backoff
	  case ShouldBlock => LockSupport.park(waiter)
	}
      }
      throw Impossible
    }

    // first try "fast path": react without creating/enqueuing a waiter
    // written with while because scalac couldn't handle tail recursion
    while (true) {
      try {
    	return tryReact(null, finalk) 
      } catch {
    	case ShouldRetry => () // should backoff
        case ShouldBlock => return slowPath
      }
    }
    throw Impossible
  }

  @inline final def !? : Option[A] = {
    // we want only one global instance of FinalK, but the cost is a
    // silly typecast
    val finalk = FinalK.asInstanceOf[ReagentK[A,A]] 

    try {
      Some(tryReact(null, finalk))
    } catch {
      case ShouldRetry => None	// should we actually retry here?  if
				// we do, more informative: a failed
				// attempt entails a linearization
				// where no match was possible.  but
				// could diverge...
      case ShouldBlock => None
    }
  }

  @inline final def flatMap[B](k: A => Reagent[B]): Reagent[B] = 
    Bind(this, k)
  @inline final def map[B](f: A => B): Reagent[B] = 
    Bind(this, (x: A) => ret(f(x)))
  @inline final def >>[B](k: Reagent[B]): Reagent[B] = 
    Bind(this, (_:A) => k)

  // @inline final def <+>[C <: A, D >: B](
  //   that: Reagent[C,D]): Reagent[C,D] = 
  //   new Reagent(this.choices ++ that.choices)

}

//private 

private case class Bind[A,B](c: Reagent[A], k1: A => Reagent[B]) 
	     extends Reagent[B] {
  @inline final def tryReact[C](trans: Transaction, k2: ReagentK[B,C]): C = 
    c.tryReact(trans, BindK(k1, k2))
}

sealed case class ret[A](pure: A) extends Reagent[A] {
  @inline final def tryReact[B](trans: Transaction, k: ReagentK[A,B]): B = 
    k.tryReact(pure, trans)
}

object retry extends Reagent[Nothing] {
  @inline final def tryReact[A](trans: Transaction, k: ReagentK[Nothing,A]): A = 
    throw ShouldRetry
}

// this really needs a better name
// could call it "reagent"
object loop {
  private case class Loop[A](c: () => Reagent[A]) extends Reagent[A] {
    @inline final def tryReact[B](trans: Transaction, k: ReagentK[A,B]): B = 
      c().tryReact(trans, k)
  }
  @inline final def apply[A](c: => Reagent[A]): Reagent[A] = Loop(() => c)
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
    @inline final def tryReact[B](trans: Transaction, k: ReagentK[A,B]): B = 
      k.tryReact(get(), trans)
  }

  sealed case class cas(expect: A, update: A) extends Reagent[Unit] {
    @inline final def tryReact[B](trans: Transaction, k: ReagentK[Unit,B]): B = {
      compareAndSet(expect, update)
      k.tryReact((), trans)
    }
  }
  def mkcas(ov:A,nv:A) = cas(ov,nv)  // deal with weird compiler bug

  sealed case class upd[B](f: A => (A,B)) extends Reagent[B] {
    @inline final def tryReact[C](trans: Transaction, k: ReagentK[B,C]): C = {
      val ov = get()
      val (nv, ret) = f(ov)
      compareAndSet(ov, nv)
      k.tryReact(ret, trans)
    }
  }
}
object Ref {
  final def apply[A](init: A): Ref[A] = new Ref(init)
  final def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}
object upd {
  final def apply[A,B](r: Ref[A])(f: A => (A,B)): Reagent[B] = r.upd(f)
}
