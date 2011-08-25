// The core reagent implementation and accompanying combinators

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._
import chemistry.Util.Implicits._

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

private object Reagent {
  val offerSpinBase = 32
  val offerSpinCutoff = 9
}
abstract class Reagent[-A, +B] {
  import Reagent._

  private[chemistry] def tryReact(a: A, rx: Reaction): B
  protected def makeOfferI(a: A, offer: Offer[B]): Unit
  def compose[C](next: Reagent[B,C]): Reagent[A,C]
  def alwaysCommits: Boolean
  def maySync: Boolean

  private[chemistry] def makeOffer(a: A, offer: Offer[B]) {
    makeOfferI(a, offer)
  }

  final def !(a: A): B = {
    def block: B = throw Util.Impossible

/*
	      case ShouldBlock => 
		if (blocking) 
		  LockSupport.park(waiter) 
		else waiter.consume !? () match {
		  case None    => if (waiter.isActive) backoff.once()
		  case Some(_) => return slowPath(true)
		}
*/

    def offer: B = {
      var bcount = 0
//      val rand = new Random

      // scalac can't do @tailrec here, due to exception handling
      val waiter = new Waiter[B](false)
      while (true) {
	waiter.reset
	makeOffer(a, waiter)
	
	var spins = offerSpinBase << bcount
	while (waiter.isActive && spins > 0) spins -= 1

	waiter.abort match {
	  case Some(b) => return b.asInstanceOf[B]
	  case _ => {}
	} 

	try return tryReact(a, Inert) catch {
	  case ShouldRetry => if (bcount < offerSpinCutoff) bcount += 1
	  case ShouldBlock => return block
	}
	
      }
      throw Util.Impossible
    }

    def withBackoff: B = {
      val backoff = new Backoff
      // scalac can't do @tailrec here, due to exception handling
      while (true) {
	try {
	  return tryReact(a, Inert) 
	} catch {
	  case ShouldRetry if maySync => return offer
	  case ShouldRetry => backoff.once()
	  case ShouldBlock => return block
	}
      }
      throw Util.Impossible
    }
    
    try {
      tryReact(a, Inert) 
    } catch {
//      case ShouldRetry if maySync => offer
      case ShouldRetry            => withBackoff
      case ShouldBlock		  => block
    }
  }

  @inline final def !?(a:A) : Option[B] = {
    try {
      Some(tryReact(a, Inert))
    } catch {
      case ShouldRetry => None	// should we actually retry here?  if we do,
				// more informative: a failed attempt entails
				// a linearization where no match was
				// possible.  but could diverge...
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
    def tryReact(x: Any, rx: Reaction): B = 
      k.tryReact(pure, rx)
    def makeOfferI(a: Any, offer: Offer[B]) =
      k.makeOffer(pure, offer)
    def compose[C](next: Reagent[B,C]) = Ret(pure, k.compose(next))
    def alwaysCommits = k.alwaysCommits
    def maySync = k.maySync
  }
  @inline final def apply[A](pure: A): Reagent[Any,A] = Ret(pure, Commit[A]())  
}

// Not sure whether this should be available as a combinaor
// object retry extends Reagent[Any,Nothing] {
//   final def tryReact[A](a: Any, rx: Reaction, k: K[Nothing,A]): A = 
//     throw ShouldRetry
// }

private case class Commit[A]() extends Reagent[A,A] {
  def tryReact(a: A, rx: Reaction): A = 
    if (rx.tryCommit) a else throw ShouldRetry
  def makeOfferI(a: A, offer: Offer[A]) {}
  def compose[B](next: Reagent[A,B]) = next
  def alwaysCommits = true
  def maySync = false
}

object never extends Reagent[Any, Nothing] {
  def tryReact(a: Any, rx: Reaction): Nothing = 
    throw ShouldBlock
  def makeOfferI(a: Any, offer: Offer[Nothing]) {}
  def compose[A](next: Reagent[Nothing, A]) = never
  def alwaysCommits = false
  def maySync = false
}

object computed {
  private final case class Computed[A,B,C](c: A => Reagent[Unit,B], 
					   k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction): C = 
      c(a).compose(k).tryReact((), rx)
    def makeOfferI(a: A, offer: Offer[C]) =
      c(a).compose(k).makeOffer((), offer)
    def compose[D](next: Reagent[C,D]) = Computed(c, k.compose(next))
    def alwaysCommits = false
    def maySync = true
  }
  @inline def apply[A,B](c: A => Reagent[Unit,B]): Reagent[A,B] = 
    Computed(c, Commit[B]())
}

object lift {
  private final case class Lift[A,B,C](f: PartialFunction[A,B], 
				       k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def tryReact(a: A, rx: Reaction): C =
      if (f.isDefinedAt(a)) 
	k.tryReact(f(a), rx) 
      else throw ShouldBlock
    def makeOfferI(a: A, offer: Offer[C]) = 
      if (f.isDefinedAt(a)) k.makeOffer(f(a), offer)
    def compose[D](next: Reagent[C,D]) = Lift(f, k.compose(next))
    def alwaysCommits = k.alwaysCommits
    def maySync = k.maySync
  }
  @inline def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B]  = 
    Lift(f, Commit[B]())
}

object choice {
  private final case class Choice[A,B](r1: Reagent[A,B], r2: Reagent[A,B]) 
		     extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction): B = 
      try r1.tryReact(a, rx) catch {
	case ShouldRetry => 
	  try r2.tryReact(a, rx) catch {
	    // ShouldRetry falls thru
	    case ShouldBlock => throw ShouldRetry 
	  }
	case ShouldBlock => 
	  r2.tryReact(a, rx) // all exceptions fall thru
      }
    def makeOfferI(a: A, offer: Offer[B]) {
      r1.makeOffer(a, offer)
      r2.makeOffer(a, offer)
    }
    def compose[C](next: Reagent[B,C]) = 
      Choice(r1.compose(next), r2.compose(next))
    def alwaysCommits = r1.alwaysCommits && r2.alwaysCommits
    def maySync = r1.maySync || r2.maySync
  }
  @inline def apply[A,B](r1: Reagent[A,B], r2: Reagent[A,B]): Reagent[A,B] =
    Choice(r1, r2)
}

object postCommit {
  private final case class PostCommit[A,B](pc: A => Unit, k: Reagent[A,B])
		     extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction): B = 
      k.tryReact(a, rx.withPostCommit((_:Unit) => pc(a)))
    def makeOfferI(a: A, offer: Offer[B]) =
      k.makeOffer(a, offer)
    def compose[C](next: Reagent[B,C]) = PostCommit(pc, k.compose(next))
    def alwaysCommits = k.alwaysCommits
    def maySync = k.maySync
  }
  @inline def apply[A](pc: A => Unit): Reagent[A,A] = 
    PostCommit(pc, Commit[A]())
}
