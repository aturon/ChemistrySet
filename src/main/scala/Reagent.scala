// The core reagent implementation and accompanying combinators

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._
import chemistry.Util.Implicits._

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

abstract class Reagent[-A, +B] {
  private[chemistry] def tryReact(a: A, rx: Reaction): B
  def compose[C](next: Reagent[B,C]): Reagent[A,C]

  final def !(a: A): B = {
    def slowPath: B = {
      val backoff = new Backoff
      val waiter = new Waiter[B]
      val recheck: Reagent[A,B] = for {
      	r <- this
	// might be able to use this in kcas
      	_ <- waiter.status.cas(Waiting, Committed) 
      } yield r

      //logWait(waiter)

      while (true) waiter.status.get() match { // scalac can't do @tailrec here
	case Committed => return waiter.answer.asInstanceOf[B]
	case _ => try {
	  return recheck.tryReact(a, Inert) 
	} catch {
	  case ShouldRetry => backoff.once()
	  case ShouldBlock => LockSupport.park(waiter)
	}
      }
      throw Util.Impossible
    }

    // first try "fast path": react without creating/enqueuing a waiter
    while (true) { // scalac can't do @tailrec here
      try {
    	return tryReact(a, Inert) 
      } catch {
    	case ShouldRetry => return slowPath
        case ShouldBlock => return slowPath
      }
    }
    throw Util.Impossible
  }

  @inline final def !?(a:A) : Option[B] = {
    try {
      Some(tryReact(a, Inert))
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
    compose(lift({ case b if f(b) => b }))
  @inline final def <+>[C <: A, D >: B](that: Reagent[C,D]): Reagent[C,D] = 
    choice(this, that)
}

object ret { 
  private final case class Ret[A,B](pure: A, k: Reagent[A,B]) 
		     extends Reagent[Any,B] {
    def tryReact(x: Any, rx: Reaction): B = 
      k.tryReact(pure, rx)
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
  def tryReact(a: A, rx: Reaction): A = a // eventually, will do kCAS
  def compose[B](next: Reagent[A,B]) = next
}

object never extends Reagent[Any, Nothing] {
  def tryReact(a: Any, rx: Reaction): Nothing =
    throw ShouldBlock
  def compose[A](next: Reagent[Nothing, A]) = never
}

object computed {
  private final case class Computed[A,B,C](c: A => Reagent[Unit,B], 
					   k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction): C = 
      c(a).compose(k).tryReact((), rx)
    def compose[D](next: Reagent[C,D]) = Computed(c, k.compose(next))
  }
  @inline def apply[A,B](c: A => Reagent[Unit,B]): Reagent[A,B] = 
    Computed(c, Commit[B]())
}

object lift {
  private final case class Lift[A,B,C](f: PartialFunction[A,B], 
				       k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction): C =
      if (f.isDefinedAt(a)) k.tryReact(f(a), rx) else throw ShouldBlock
    def compose[D](next: Reagent[C,D]) = Lift(f, k.compose(next))
  }
  @inline def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B]  = 
    Lift(f, Commit[B]())
}

object choice {
  private case class Choice[A,B](r1: Reagent[A,B], r2: Reagent[A,B]) 
	       extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction): B = 
      try r1.tryReact(a, rx) catch {
	case ShouldRetry => 
	  try r2.tryReact(a, rx) catch {       // ShouldRetry falls thru
	    case ShouldBlock => throw ShouldRetry 
	  }
	case ShouldBlock => r2.tryReact(a, rx) // exceptions fall thru
      }
    def compose[C](next: Reagent[B,C]) = 
      Choice(r1.compose(next), r2.compose(next))
  }
  @inline def apply[A,B](r1: Reagent[A,B], r2: Reagent[A,B]): Reagent[A,B] =
    Choice(r1, r2)
}
