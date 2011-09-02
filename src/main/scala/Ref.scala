// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

/*
private final class PaddedAtomicReference[A](init:A) 
	      extends AtomicReference[A](init) {
  var q0: Long = 0
  var q1: Long = 0
  var q2: Long = 0
  var q3: Long = 0
  var q4: Long = 0
  var q5: Long = 0
  var q6: Long = 0
  var q7: Long = 0
  var q8: Long = 0
  var q9: Long = 0
  var qa: Long = 0
  var qb: Long = 0
  var qc: Long = 0
  var qd: Long = 0
  var qe: Long = 0
}
*/

final class Ref[A <: AnyRef](init: A) {
//  private val waiters = new MSQueue[]()

  //  private[chemistry] val data = new PaddedAtomicReference[AnyRef](init)
  // really, the type of data should belong to Reaction
//  private[chemistry] val data = new AtomicReference[AnyRef](init)
//  @inline private def get: A = Reaction.read(data).asInstanceOf[A]
  private[chemistry] val data = new AtomicReference[AnyRef](init)
  // ultimately, Reaction.read(data)
  @inline private def get = data.get.asInstanceOf[A]

  @inline def read: Reagent[Unit,A] = new AutoCont[Unit,A] {
    def retValue(u: Unit): Any = Reaction.read(data)
  }   

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    type Cache = k.Cache
    def useCache = k.useCache
    def tryReact(u: Unit, rx: Reaction, cache: Cache): Any = 
      throw Util.Impossible
/*
      Ref.rxWithCAS(rx, data, expect, update, k) match {
	case (newRx: Reaction) =>
	  k.tryReact((), newRx, cache)
	case ow => ow
      }
*/
    def makeOfferI(u: Unit, offer: Offer[B]) =
      k.makeOffer(u, offer)
    def snoop(u: Unit) = false
    def composeI[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

/*
  private final case class Upd[B,C,D](f: (A,B) => (A,C), k: Reagent[C,D]) 
		     extends Reagent[B, D] {
    def tryReact(b: B, rx: Reaction, cache: Cache): Any = {
      val ov = get
      val (nv, ret) = f(ov, b)
      Ref.continueWithCAS(data, rx, ret, k, ov, nv, attempt)
    }
    def makeOfferI(b: B, offer: Offer[D]): Unit = {
      val ov = get
      val (_, ret) = f(ov, b)
      k.makeOffer(ret, offer)
    }
    def composeI[E](next: Reagent[D,E]) = Upd(f, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
  }
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    Upd(f, Commit[C]())
*/
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    throw Util.Impossible

  abstract class InnerFastUpd[B,C,D] private[chemistry] (val k: Reagent[C,D])
	   extends Reagent[B, D] {
    type Cache = k.Cache
    def useCache = k.useCache

    @inline final def fastReact(b: B): C = {
      while (true) {
	val ov = get
	val nv = newValue(ov, b)
	if (data.compareAndSet(ov, nv)) return retValue(ov, b)
      }
      throw Util.Impossible
    }

    @inline final def tryReact(b: B, rx: Reaction, cache: Cache): Any = {
      if (rx.casCount == 0 && k.alwaysCommits) {
	val ov = get
	val nv = newValue(ov, b)
	if (data.compareAndSet(ov, nv))
	  return k.tryReact(retValue(ov, b), rx, cache)
	RetryUncached
      } else {
	val ov = get
	val nv = newValue(ov, b)
	k.tryReact(retValue(ov, b), rx.withCAS(data, ov, nv), cache)
      }
    }
    def snoop(b: B) = false
    def makeOfferI(b: B, offer: Offer[D]): Unit =
      k.makeOffer(retValue(get, b), offer)
    def composeI[E](next: Reagent[D,E]) = 
      new InnerFastUpd[B,C,E](k.compose(next)) {
	final def newValue(a: A, b: B): A = InnerFastUpd.this.newValue(a, b)
	final def retValue(a: A, b: B): C = InnerFastUpd.this.retValue(a, b)
      }
    def maySync = k.maySync
    def alwaysCommits = false

    def newValue(a: A, b: B): A
    def retValue(a: A, b: B): C
  }
  abstract class FastUpd[B,C] extends InnerFastUpd[B,C,C](Commit[C]()) 

  abstract class InnerCachedUpd[C,D,E] private[chemistry] (
    k: Reagent[D,E]
  ) extends Reagent[C, E] {
    type Cache >: Null <: Retry
    def useCache = true
    def snoop(c: C) = false

    @inline final def fastReact(c: C): D = {
      val cached = initCache
      while (true) {
	val ov = get
	val nv = newValue(ov, cached, c)
	if (data.compareAndSet(ov, nv)) return retValue(ov, c)
      }
      throw Util.Impossible
    }

    @inline final def tryReact(c: C, rx: Reaction, cache: Cache): Any = {
      val cached: Cache = if (cache == null) initCache else cache

      val ret: Any = 
	if (rx.casCount == 0 && k.alwaysCommits) {
	  // pushed inside if to keep close to CAS	
//	  var tries = 20
//	  while (true) {
	    val ov = get
	    val nv = newValue(ov, cached, c)
	    if (data.compareAndSet(ov, nv))
	      return k.tryReact(retValue(ov, c), rx, null)
//	    tries -= 1
//	  }
//	  println("> 20")
	  RetryUncached
	} else {
	  val ov = get
	  val nv = newValue(ov, cached, c)
	  k.tryReact(retValue(ov, c), rx.withCAS(data, ov, nv), null)
	}

      ret match {
	case (_: Retry) => cached
	case _ => ret
      }
    }
    def makeOfferI(c: C, offer: Offer[E]): Unit = {
      val ov = get
      k.makeOffer(retValue(ov, c), offer)
    }
    def composeI[F](next: Reagent[E,F]) = 
      new InnerCachedUpd[C,D,F](k.compose(next)) {
	type Cache = InnerCachedUpd.this.Cache
	def initCache: Cache = InnerCachedUpd.this.initCache
	def newValue(a: A, cache: Cache, arg: C): A =
	  InnerCachedUpd.this.newValue(a, cache, arg)
	def retValue(a: A, arg: C): D =
	  InnerCachedUpd.this.retValue(a, arg)
      }
    def maySync = k.maySync
    def alwaysCommits = false

    def initCache: Cache
    def newValue(a: A, cache: Cache, arg: C): A
    def retValue(a: A, arg: C): D
  }
  abstract class CachedUpd[C,D] 
	   extends InnerCachedUpd[C,D,D](Commit[D]()) 

/*
  private final case class UpdUnit[B,C](f: PartialFunction[A, (A,B)],
				        k: Reagent[B, C]) 
		     extends Reagent[Unit, C] {
    def tryReact(u: Unit, rx: Reaction, cache: Cache): Any = {
      val ov = get
      if (!f.isDefinedAt(ov)) return ShouldBlock
      val (nv, ret) = f(ov)
      Ref.continueWithCAS(data, rx, ret, k, ov, nv, attempt)
    }
    def makeOfferI(u: Unit, offer: Offer[C]): Unit = {
      val ov = get
      if (!f.isDefinedAt(ov)) return
      val (_, ret) = f(ov)
      k.makeOffer(ret, offer)
    }
    def composeI[D](next: Reagent[C,D]) = UpdUnit(f, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
  }
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    UpdUnit(f, Commit[B]())

  @inline def updIn[B](f: (A,B) => A): Reagent[B, Unit] = 
    UpdIn(f, Commit[Unit]())
*/

  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    throw Util.Impossible
}

object upd {
  @inline def apply[A <: AnyRef,B,C](r: Ref[A])(f: (A,B) => (A,C)) = 
    r.upd(f)
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = 
    r.upd(f)
}
/*
object updIn {
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: (A,B) => A) = 
    r.updIn(f)
}
*/
object Ref {
  @inline def apply[A <: AnyRef](init: A): Ref[A] = new Ref(init)
  @inline def unapply[A <: AnyRef](r: Ref[A]): Option[A] = Some(r.get)

  @inline private[chemistry] def rxWithCAS[A, B](
    rx: Reaction, ref: AtomicReference[AnyRef], 
    ov: AnyRef, nv: AnyRef, k: Reagent[A,B]
  ): Reaction = 
    if (rx.casCount == 0 && k.alwaysCommits) {
      if (ref.compareAndSet(ov, nv)) rx else null
    } else {
      rx.withCAS(ref, ov, nv)
    }    
}
