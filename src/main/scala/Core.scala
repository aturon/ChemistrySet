// The reagent implementation

package chemistry

import java.util.concurrent.atomic._
import java.util.concurrent.locks._
import scala.annotation.tailrec

sealed private abstract class LogEntry
private case class CASLog[A](r: AtomicReference[A], ov: A, nv: A) 
	     extends LogEntry

final private class Transaction {}

private abstract class WaiterStatus
private case object Catalyst extends WaiterStatus
private case object Waiting  extends WaiterStatus
private case object Finished extends WaiterStatus

sealed private abstract class AbsWaiter
final private case class Waiter[A,B](
  r: Reagent[A,B], 
  arg: A,
  var answer: AnyRef,
  status: Ref[WaiterStatus], 
  thread: Thread
) extends AbsWaiter

private case object Impossible extends Exception

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

private sealed abstract class K[-A,+B] {
  private[chemistry] def tryReact(a: A, trans: Transaction): B
}
private object K {
  final case class Bind[A,B,C](k1: A => Reagent[Unit, B], k2: K[B,C]) 
	     extends K[A,C] {
    def tryReact(a: A, trans: Transaction): C = k1(a).tryReact((), trans, k2)
  }

  final case class Compose[A,B,C](r: Reagent[A, B], k: K[B,C]) 
	     extends K[A,C] {
    def tryReact(a: A, trans: Transaction): C = r.tryReact(a, trans, k)
  }

  final case class Filter[A,B](f: A => Boolean, k: K[A,B]) extends K[A,B] {
    def tryReact(a: A, trans: Transaction): B = 
      if (f(a)) k.tryReact(a, trans) else throw ShouldBlock
  }

  object Final extends K[Any,Any] {
    // ID continuation at the moment; eventually will be responsible for kCAS
    def tryReact(a: Any, trans: Transaction): Any = a
  }
}

sealed abstract class Reagent[-A, +B] {
  private[chemistry] 
  def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C

  final def !(a: A): B = {
    // typecast to work around contravariance and save on allocation
    val finalk = K.Final.asInstanceOf[K[B,B]] 
    
    def slowPath: B = {
      val backoff = new Backoff
      val status = Ref[WaiterStatus](Waiting)
      val recheck: Reagent[A,B] = for {
      	r <- this
      	_ <- status.cas(Waiting, Finished)
      } yield r
      val waiter = Waiter(this, a, null, status, Thread.currentThread())

      //logWait(waiter)

      while (true) status.get() match { // scalac can't do @tailrec here
	case Finished => return waiter.answer.asInstanceOf[B]
	case _ => try {
	  return recheck.tryReact(a, null, finalk) 
	} catch {
	  case ShouldRetry => backoff.once()
	  case ShouldBlock => LockSupport.park(waiter)
	}
      }
      throw Impossible
    }

    // first try "fast path": react without creating/enqueuing a waiter
    while (true) { // scalac can't do @tailrec here
      try {
    	return tryReact(a, null, finalk) 
      } catch {
    	case ShouldRetry => return slowPath
        case ShouldBlock => return slowPath
      }
    }
    throw Impossible
  }

  @inline final def !?(a:A) : Option[B] = {
    // typecast to work around contravariance and save on allocation
    val finalk = K.Final.asInstanceOf[K[B,B]] 

    try {
      Some(tryReact(a, null, finalk))
    } catch {
      case ShouldRetry => None	// should we actually retry here?  if
				// we do, more informative: a failed
				// attempt entails a linearization
				// where no match was possible.  but
				// could diverge...
      case ShouldBlock => None
    }
  }

  final def dissolve(a: A) {
    // todo
  }

  @inline final def flatMap[C](k: B => Reagent[Unit,C]): Reagent[A,C] = 
    bind(this, k)
  @inline final def map[C](f: B => C): Reagent[A,C] = 
    bind(this, (x: B) => ret(f(x)))
  @inline final def >>[C](k: Reagent[Unit,C]): Reagent[A,C] = 
    bind(this, (_:B) => k)
  @inline final def withFilter(f: B => Boolean): Reagent[A,B] =
    postFilter(this, f)
  @inline final def mapFilter[C](f: PartialFunction[B, C]): Reagent[A,C] =
    compose(this, lift(f))
  @inline final def <+>[C <: A, D >: B](that: Reagent[C,D]): Reagent[C,D] = 
    choice(this, that)
}

private object bind {
  final case class Bind[A,B,C](c: Reagent[A,B], k1: B => Reagent[Unit,C]) 
	     extends Reagent[A,C] {
    def tryReact[D](a: A, trans: Transaction, k2: K[C,D]): D = 
      c.tryReact(a, trans, K.Bind(k1, k2))
  }
  @inline def apply[A,B,C](c: Reagent[A,B], 
			   k1: B => Reagent[Unit,C]): Reagent[A,C] = 
    Bind(c, k1)
}

private object compose {
  private final case class Compose[A,B,C](r1: Reagent[A,B], r2: Reagent[B,C])
		     extends Reagent[A,C] {
    def tryReact[D](a: A, trans: Transaction, k: K[C,D]): D =
      r1.tryReact(a, trans, K.Compose(r2, k))
  }
  @inline def apply[A,B,C](r1: Reagent[A,B], r2: Reagent[B,C]): Reagent[A,C] =
    Compose(r1, r2)
}

// PreFilter is not yet exposed as a combinator
private case class PreFilter[A,B](c: Reagent[A,B], f: A => Boolean) 
		   extends Reagent[A,B] {
 def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = 
   if (f(a)) c.tryReact(a, trans, k) else throw ShouldBlock
}

private object postFilter {
  private case class PostFilter[A,B](c: Reagent[A,B], f: B => Boolean) 
  	       extends Reagent[A,B] {
    def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = 
      c.tryReact(a, trans, K.Filter(f, k))
  }
  @inline def apply[A,B](c: Reagent[A,B], f: B => Boolean): Reagent[A,B] =
    PostFilter(c,f)
}

object ret { 
  private final case class Ret[A](pure: A) extends Reagent[Unit,A] {
    def tryReact[B](u: Unit, trans: Transaction, k: K[A,B]): B = 
      k.tryReact(pure, trans)
  }
  @inline final def apply[A](pure: A): Reagent[Unit,A] = Ret(pure)
}

// Not sure whether this should be available as a combinaor
// object retry extends Reagent[Any,Nothing] {
//   final def tryReact[A](a: Any, trans: Transaction, k: K[Nothing,A]): A = 
//     throw ShouldRetry
// }

object never extends Reagent[Any, Nothing] {
  def tryReact[A](a: Any, trans: Transaction, k: K[Nothing, A]): A =
    throw ShouldBlock
}

// this really needs a better name
// could call it "reagent"
object loop {
  private final case class Loop[A,B](c: A => Reagent[Unit,B]) 
		     extends Reagent[A,B] {
    def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = 
      c(a).tryReact((), trans, k)
  }
  @inline def apply[A,B](c: A => Reagent[Unit,B]): Reagent[A,B] = 
    Loop(c)
}

object lift {
  private final case class Lift[A,B](f: PartialFunction[A,B]) 
		     extends Reagent[A,B] {
    def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C =
      if (f.isDefinedAt(a)) k.tryReact(f(a), trans) else throw ShouldBlock
  }
  @inline def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B]  = 
    Lift(f)
}

object choice {
  private case class Choice[A,B](r1: Reagent[A,B], r2: Reagent[A,B]) 
	       extends Reagent[A,B] {
    def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = 
      try r1.tryReact(a, trans, k) catch {
	case ShouldRetry => 
	  try r2.tryReact(a, trans, k) catch {
	    case ShouldBlock => throw ShouldRetry
	  }
	case ShouldBlock => r2.tryReact(a, trans, k)
      }
  }
  @inline def apply[A,B](r1: Reagent[A,B], r2: Reagent[A,B]): Reagent[A,B] =
    Choice(r1, r2)
}

private final class Endpoint[A,B] extends Reagent[A,B] {
  val mq = new MSQueue[Waiter[A,B]]()
  var dual: Endpoint[B,A] = null
  def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = 
    throw ShouldRetry
}
object SwapChan {
  @inline def apply[A,B](): (Reagent[A,B], Reagent[B,A]) = {
    val c1 = new Endpoint[A,B]
    val c2 = new Endpoint[B,A]
    c1.dual = c2; c2.dual = c1
    (c1, c2)
  }
}
object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}

final class Ref[A](init: A) extends AtomicReference[A](init) {
//  private final var data = new AtomicReference[A](init)

//  private val waiters = new MSQueue[]()

  case object read extends Reagent[Unit, A] {
    def tryReact[B](u: Unit, trans: Transaction, k: K[A,B]): B = 
      k.tryReact(get(), trans)
  }

  private final class CAS(expect: A, update: A) extends Reagent[Unit, Unit] {
    def tryReact[B](u: Unit, trans: Transaction, k: K[Unit,B]): B ={
      if (compareAndSet(expect, update))
	k.tryReact((), trans)
      else throw ShouldRetry
    }
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = new CAS(ov,nv) 

  private final class Upd[B,C](f: (A,B) => (A,C)) extends Reagent[B, C] {
    def tryReact[D](b: B, trans: Transaction, k: K[C,D]): D = {
      val ov = get()
      val (nv, ret) = f(ov, b)
      if (compareAndSet(ov, nv))
	k.tryReact(ret, trans)
      else throw ShouldRetry
    }
  }
  private final class UpdUnit[B](f: PartialFunction[A, (A,B)]) 
		extends Reagent[Unit, B] {
    def tryReact[C](u: Unit, trans: Transaction, k: K[B,C]): C = {
      val ov = get()
      if (!f.isDefinedAt(ov)) throw ShouldBlock
      val (nv, ret) = f(ov)
      if (compareAndSet(ov, nv))
	k.tryReact(ret, trans)
      else throw ShouldRetry
    }
  }

  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    new Upd(f)
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    new UpdUnit(f)
}
object Ref {
  @inline def apply[A](init: A): Ref[A] = new Ref(init)
  def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}
object upd {
  @inline def apply[A,B,C](r: Ref[A])(f: (A,B) => (A,C)) = r.upd(f)
  @inline def apply[A,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = r.upd(f)
}
