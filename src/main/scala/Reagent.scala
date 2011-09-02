// The core reagent implementation and accompanying combinators

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._
import chemistry.Util.Implicits._

private[chemistry] sealed abstract class BacktrackCommand
private[chemistry] case object Blocked extends BacktrackCommand 
private[chemistry] abstract class Retry extends BacktrackCommand 
private[chemistry] case object RetryUncached extends Retry

private object OfferFail extends Exception

abstract class Reagent[-A, +B] {
  private[chemistry] type Cache >: Null <: Retry
  private[chemistry] def useCache: Boolean
  // returns either a BacktrackCommand or a B
  private[chemistry] def tryReact(a: A, rx: Reaction, cache: Cache): Any
  protected def makeOfferI(a: A, offer: Offer[B]): Unit
  protected def composeI[C](next: Reagent[B,C]): Reagent[A,C]
  private[chemistry] def alwaysCommits: Boolean
  private[chemistry] def maySync: Boolean
  private[chemistry] def snoop(a: A): Boolean

  @inline private[chemistry] final def convCache(r: Retry): Cache = 
    if (r == RetryUncached) null else r.asInstanceOf[Cache]

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

  private[chemistry] final def withBackoff(a: A, cache: Cache): B = {
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
      
      tryReact(a, Inert, cache) match {
	case (_: Retry) => loop
	case Blocked    => block
	case ans        => ans.asInstanceOf[B]
      }
    }
    loop
  }

  final def !(a: A): B = tryReact(a, Inert, null) match {
    case (r: Retry) => withBackoff(a, convCache(r))
    case Blocked    => block
    case ans        => ans.asInstanceOf[B]
  }

  @inline final def !?(a:A) : Option[B] = {
    tryReact(a, Inert, null) match {
      case (_:Retry) => None	// should we actually retry here?  if we do,
				// more informative: a failed attempt entails
				// a linearization where no match was
				// possible.  but could diverge...
      case Blocked   => None
      case ans       => Some(ans.asInstanceOf[B])
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
  final type Cache = k.Cache
  final def useCache = k.useCache
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
  final def tryReact(a: A, rx: Reaction, cache: Cache): Any = retValue(a) match {
    case (bc: BacktrackCommand) => bc
    case b => k.tryReact(b.asInstanceOf[B], newRx(a, rx), cache)
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
  type Cache = Retry
  def useCache = false
  def tryReact(a: A, rx: Reaction, cache: Cache): Any = {
    if (a == null) println("commit null")
    if (rx.tryCommit) a else RetryUncached
  }
  def snoop(a: A) = true
  def makeOfferI(a: A, offer: Offer[A]) {}
  def composeI[B](next: Reagent[A,B]) = next
  def alwaysCommits = true
  def maySync = false
}

object never extends Reagent[Any, Nothing] {
  type Cache = Retry
  def useCache = false
  def tryReact(a: Any, rx: Reaction, cache: Cache): Any = Blocked
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
    type Cache = Retry
    def useCache = false
    def snoop(a: A) = false
    def tryReact(a: A, rx: Reaction, cache: Cache): Any = 
      c(a).compose(k).tryReact((), rx, null)
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
    type Cache = Retry
    private[chemistry] final case class CCache(r1c: r1.Cache, r2c: r2.Cache)
				  extends Retry
    def useCache = true
    def tryReact(a: A, rx: Reaction, cache: Cache): Any = {
      val r1c = if (cache == null || !r1.useCache) null 
		else if (!r2.useCache) r1.convCache(cache)
		else cache.asInstanceOf[CCache].r1c
      val r2c = if (cache == null || !r2.useCache) null 
		else if (!r1.useCache) r2.convCache(cache)
		else cache.asInstanceOf[CCache].r2c

      def newCache(r1c: Retry, r2c: Retry): Retry = {
	val c = if (!r1.useCache) r2c
		else if (!r2.useCache) r1c
		else CCache(r1.convCache(r1c), r2.convCache(r2c))
        if (c == null) RetryUncached else c
      }

      r1.tryReact(a, rx, r1c) match {
	case (r1c: Retry) => 
	  r2.tryReact(a, rx, r2c) match {
	    case (r2c: Retry) => newCache(r1c, r2c)
	    case Blocked      => newCache(r1c, r2c)  // retry since r1 could
	    case ans          => ans
	  }
	case Blocked => 
	  r2.tryReact(a, rx, r2c) match {
	    case (r2c: Retry) => newCache(r1c, r2c)
	    case ow           => ow
	  }
	case ans => ans
      }
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
