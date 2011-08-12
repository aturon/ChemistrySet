// The core reagent implementation and accompanying combinators

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._
import chemistry.Util.Implicits._

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

abstract class Reagent[-A, +B] {
  private[chemistry] def tryReact(
    a: A, rx: Reaction, offer: Offer[B], blocking: Boolean
  ): B
  def compose[C](next: Reagent[B,C]): Reagent[A,C]

  final def !(a: A): B = {
    def slowPath(blocking: Boolean): B = {
      val waiter = new Waiter[B](blocking)
      val retry: Reagent[A,B] = for {
      	r <- this
      	_ <- waiter.cancel // might be able to use this in kcas
      } yield r

      try {
	return retry.tryReact(a, Inert, waiter, blocking)
      } catch {
	case ShouldRetry => {}
	case ShouldBlock => if (!blocking)
      }

      val backoff = new Backoff

      // scalac can't do @tailrec here, due to exception handling
      while (true) waiter.poll match { 
	case Some(b) => return b.asInstanceOf[B]
	case None => try {
	  return retry.tryReact(a, Inert, null, false) 
	} catch {
	  case ShouldRetry => backoff.once()
	  case ShouldBlock => 
	    if (blocking) 
	      LockSupport.park(waiter) 
	    else waiter.cancel !? () match {
	      case None    => if (waiter.isActive) backoff.once()
	      case Some(_) => return slowPath(true)
	    }
	}
      }
      throw Util.Impossible
    }

    // "fast path": react without creating/enqueuing a waiter
    def fastPath: B = try {
      tryReact(a, Inert, null, false) 
    } catch {
      case ShouldRetry => slowPath(false)
      case ShouldBlock => slowPath(true)
    }

    fasthPath // start with fast path
  }

  @inline final def !?(a:A) : Option[B] = {
    try {
      Some(tryReact(a, Inert, null, false))
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
    compose(computed(k))
  @inline final def map[C](f: B => C): Reagent[A,C] = 
    compose(lift(f))
 @inline final def >>[C](next: Reagent[Unit,C]): Reagent[A,C] = 
   compose(lift((_:B) => ()).compose(next))
  @inline final def mapFilter[C](f: PartialFunction[B, C]): Reagent[A,C] =
    compose(lift(f))
  @inline final def withFilter(f: B => Boolean): Reagent[A,B] =
    compose(lift((_: B) match { case b if f(b) => b }))
  @inline final def <+>[C <: A, D >: B](that: Reagent[C,D]): Reagent[C,D] = 
    choice(this, that)
  @inline final def >=>[C](k: Reagent[B,C]): Reagent[A,C] =
    compose(k)
}

object ret { 
  private final case class Ret[A,B](pure: A, k: Reagent[A,B]) 
		     extends Reagent[Any,B] {
    def tryReact(x: Any, rx: Reaction, offer: Offer[B], blocking: Boolean): B = 
      k.tryReact(pure, rx, offer, blocking)
    def compose[C](next: Reagent[B,C]) = Ret(pure, k.compose(next))
  }
  @inline final def apply[A](pure: A): Reagent[Any,A] = Ret(pure, Commit[A]())
}

// Not sure whether this should be available as a combinaor
// object retry extends Reagent[Any,Nothing] {
//   final def tryReact[A](a: Any, rx: Reaction, k: K[Nothing,A]): A = 
//     throw ShouldRetry
// }

private case class Commit[A]() extends Reagent[A,A] {
  def tryReact(a: A, rx: Reaction, offer: Offer[A], blocking: Boolean): A = 
    if (rx.tryCommit) a else throw ShouldRetry
  def compose[B](next: Reagent[A,B]) = next
}

object never extends Reagent[Any, Nothing] {
  def tryReact(
    a: Any, rx: Reaction, offer: Offer[Nothing], blocking: Boolean
  ): Nothing = throw ShouldBlock
  def compose[A](next: Reagent[Nothing, A]) = never
}

object computed {
  private final case class Computed[A,B,C](c: A => Reagent[Unit,B], 
					   k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction, offer: Offer[C], blocking: Boolean): C = 
      c(a).compose(k).tryReact((), rx, offer, blocking)
    def compose[D](next: Reagent[C,D]) = Computed(c, k.compose(next))
  }
  @inline def apply[A,B](c: A => Reagent[Unit,B]): Reagent[A,B] = 
    Computed(c, Commit[B]())
}

object lift {
  private final case class Lift[A,B,C](f: PartialFunction[A,B], 
				       k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction, offer: Offer[C], blocking: Boolean): C =
      if (f.isDefinedAt(a)) 
	k.tryReact(f(a), rx, offer, blocking) 
      else throw ShouldBlock
    def compose[D](next: Reagent[C,D]) = Lift(f, k.compose(next))
  }
  @inline def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B]  = 
    Lift(f, Commit[B]())
}

object choice {
  private final case class Choice[A,B](r1: Reagent[A,B], r2: Reagent[A,B]) 
		     extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction, offer: Offer[B], blocking: Boolean): B = 
      try r1.tryReact(a, rx, offer, blocking) catch {
	case ShouldRetry => 
	  try r2.tryReact(a, rx, offer, blocking) catch {
	    // ShouldRetry falls thru
	    case ShouldBlock => throw ShouldRetry 
	  }
	case ShouldBlock => 
	  r2.tryReact(a, rx, offer, blocking) // all exceptions fall thru
      }
    def compose[C](next: Reagent[B,C]) = 
      Choice(r1.compose(next), r2.compose(next))
  }
  @inline def apply[A,B](r1: Reagent[A,B], r2: Reagent[A,B]): Reagent[A,B] =
    Choice(r1, r2)
}

object postCommit {
  private final case class PostCommit[A,B](pc: A => Unit, k: Reagent[A,B])
		     extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction, offer: Offer[B], blocking: Boolean): B = 
      k.tryReact(a, pc(a) +: rx, offer, blocking)
    def compose[C](next: Reagent[B,C]) = PostCommit(pc, k.compose(next))
  }
  @inline def apply[A](pc: A => Unit): Reagent[A,A] = 
    PostCommit(pc, Commit[A]())
}
