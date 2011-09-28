// The core reagent implementation and accompanying combinators

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._
import chemistry.Util.Implicits._

private[chemistry] sealed abstract class BacktrackCommand
private[chemistry] case object Block extends BacktrackCommand 
private[chemistry] case object Retry extends BacktrackCommand 

abstract class Reagent[-A, +B] {
  // returns either a BacktrackCommand or a B
  private[chemistry] def tryReact(a: A, rx: Reaction): Any
  protected def makeOfferI(a: A, offer: Offer[B]): Unit
  protected def composeI[C](next: Reagent[B,C]): Reagent[A,C]
  private[chemistry] def alwaysCommits: Boolean
  private[chemistry] def maySync: Boolean
  private[chemistry] def snoop(a: A): Boolean

  @inline private[chemistry] final def makeOffer(a: A, offer: Offer[B]) {
    // abort early if offer has already been consumed
    if (offer.isActive && maySync) makeOfferI(a, offer)
  }
  final def compose[C](next: Reagent[B,C]): Reagent[A,C] = next match {
    case Commit() => this.asInstanceOf[Reagent[A,C]] // B = C
    case _ => composeI(next)
  }

  def block: B = {
/*
      val waiter = new Waiter[B](true)
      val initRX = waiter.rxForConsume
      while (true) {
	waiter.reset
	makeOffer(a, waiter)

	tryReact(a, initRX) match {
	  case ShouldRetry => throw Util.Impossible
	  case ShouldBlock => throw Util.Impossible
	  case ans         => return ans.asInstanceOf[B]
	}
      }
*/ 

/*
	      case ShouldBlock => 
		if (blocking) 
		  LockSupport.park(waiter) 
		else waiter.consume !? () match {
		  case None    => if (waiter.isActive) backoff.once()
		  case Some(_) => return slowPath(true)
		}
*/
    throw Util.Impossible
  }

  private[chemistry] final def withBackoff(a: A): B = {
    val backoff = new Backoff
    val doOffer = maySync
    var waiter: Waiter[B] = null      

    @tailrec def loop: B = {
      if (doOffer) {
	if (waiter == null) {	      
	  waiter = new Waiter[B](false) 
	  makeOffer(a, waiter)
	} else waiter.reset
	backoff.once(waiter.isActive && !snoop(a), 2)
	waiter.abort match {
	  case Some(b) => return b.asInstanceOf[B] 
	  case None => {}
	}
      } else backoff.once
      
      tryReact(a, Inert) match {
	case (_: Retry) => loop
	case Blocked    => block
	case ans        => ans.asInstanceOf[B]
      }
    }
    loop
  }

  final def !(a: A): B = tryReact(a, Inert, null) match {
    case Retry => withBackoff(a)
    case Block => block
    case ans   => ans.asInstanceOf[B]
  }

  @inline final def !?(a:A) : Option[B] = {
    tryReact(a, Inert, null) match {
      case Retry => None // should we actually retry here?  if we do, more
			 // informative: a failed attempt entails a
			 // linearization where no match was possible.  but
			 // could diverge...
      case Block => None
      case ans   => Some(ans.asInstanceOf[B])
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

private abstract class AutoContImpl[A,B,C](val k: Reagent[B, C]) 
		 extends Reagent[A,C] {
  def retValue(a: A): Any // BacktrackCommand or B
  def newRx(a: A, rx: Reaction): Reaction = rx
  final def makeOfferI(a: A, offer: Offer[C]) = retValue(a) match {
    case (_: BacktrackCommand) => {}
    case b => k.makeOffer(b.asInstanceOf[B], offer)
  }
  final def snoop(a: A) = retValue(a) match {
    case (_: BacktrackCommand) => false
    case b => k.snoop(b.asInstanceOf[B])
  }
  final def tryReact(a: A, rx: Reaction): Any = retValue(a) match {
    case (bc: BacktrackCommand) => bc
    case b => k.tryReact(b.asInstanceOf[B], newRx(a, rx))
  }
  final def composeI[D](next: Reagent[C,D]) = 
    new AutoContImpl[A,B,D](k >=> next) {
      def retValue(a: A): Any = 
	AutoContImpl.this.retValue(a)
      override def newRx(a: A, rx: Reaction): Reaction = 
	AutoContImpl.this.newRx(a, rx)
    }
  final def alwaysCommits = k.alwaysCommits
  final def maySync = k.maySync
}
private abstract class AutoCont[A,B] extends AutoContImpl[A,B,B](Commit[B]())

object ret {
  @inline final def apply[A](pure: A): Reagent[Any,A] = new AutoCont[Any,A] {
    def retValue(a: Any): Any = pure
  }
}

// Not sure whether this should be available as a combinaor
// object retry extends Reagent[Any,Nothing] {
//   final def tryReact[A](a: Any, rx: Reaction, k: K[Nothing,A]): A = 
//     throw ShouldRetry
// }

private final case class Commit[A]() extends Reagent[A,A] {
  def tryReact(a: A, rx: Reaction): Any = {
    if (rx.tryCommit) a else Retry
  }
  def snoop(a: A) = true
  def makeOfferI(a: A, offer: Offer[A]) {}
  def composeI[B](next: Reagent[A,B]) = next
  def alwaysCommits = true
  def maySync = false
}

object never extends Reagent[Any, Nothing] {
  def tryReact(a: Any, rx: Reaction): Any = Block
  def snoop(a: Any) = false
  def makeOfferI(a: Any, offer: Offer[Nothing]) {}
  def composeI[A](next: Reagent[Nothing, A]) = never
  def alwaysCommits = false
  def maySync = false
}

object computed {
  private final case class Computed[A,B,C](c: A => Reagent[Unit,B], 
					   k: Reagent[B,C]) 
		     extends Reagent[A,C] {
    def snoop(a: A) = false
    def tryReact(a: A, rx: Reaction): Any = 
      c(a).compose(k).tryReact((), rx)
    def makeOfferI(a: A, offer: Offer[C]) =
      c(a).compose(k).makeOffer((), offer)
    def composeI[D](next: Reagent[C,D]) = Computed(c, k.compose(next))
    def alwaysCommits = false
    def maySync = true
  }
  @inline def apply[A,B](c: A => Reagent[Unit,B]): Reagent[A,B] = 
    Computed(c, Commit[B]())
}

object lift {
  @inline def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B] = 
    new AutoCont[A,B] {
      def retValue(a: A): Any = if (f.isDefinedAt(a)) f(a) else Blocked
    }
}

object choice {
  private final case class Choice[A,B](r1: Reagent[A,B], r2: Reagent[A,B]) 
		     extends Reagent[A,B] {
    def tryReact(a: A, rx: Reaction): Any = 
      r1.tryReact(a, rx) match {
	case Retry => 
	  r2.tryReact(a, rx) match {
	    case Retry => Retry
	    case Block => Retry // must retry r1
	    case ans   => ans
	  }
	case Block => r2.tryReact(a, rx) 
	case ans => ans
      }
    def makeOfferI(a: A, offer: Offer[B]) {
      r1.makeOffer(a, offer)
      r2.makeOffer(a, offer)
    }
    def composeI[C](next: Reagent[B,C]) = 
      Choice(r1.compose(next), r2.compose(next))
    def alwaysCommits = r1.alwaysCommits && r2.alwaysCommits
    def maySync = r1.maySync || r2.maySync
    def snoop(a: A) = r2.snoop(a) || r1.snoop(a) 
  }
  @inline def apply[A,B](r1: Reagent[A,B], r2: Reagent[A,B]): Reagent[A,B] =
    Choice(r1, r2)
}

object postCommit {
  @inline def apply[A](pc: A => Unit): Reagent[A,A] = new AutoCont[A,A] {
    def retValue(a: A): Any = a
    override def newRx(a: A, rx: Reaction): Reaction = 
      rx.withPostCommit((_:Unit) => pc(a))
  }
}
