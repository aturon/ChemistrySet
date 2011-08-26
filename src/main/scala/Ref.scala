// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._

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

  // really, the type of data should belong to Reaction
  private[chemistry] val data = new AtomicReference[AnyRef](init)
//  private[chemistry] val data = new PaddedAtomicReference[AnyRef](init)
  private def get: A = Reaction.read(data).asInstanceOf[A]

  private final case class Read[B](k: Reagent[A,B]) extends Reagent[Unit,B] {
    def tryReact(u: Unit, rx: Reaction): Any = 
      k.tryReact(get, rx)
    def makeOfferI(u: Unit, offer: Offer[B]) = k.makeOffer(get, offer)
    def composeI[C](next: Reagent[B,C]) = Read(k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = k.alwaysCommits
  }
  @inline def read: Reagent[Unit,A] = Read(Commit())					   

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    def tryReact(u: Unit, rx: Reaction): Any = 
      Ref.continueWithCAS(data, rx, (), k, expect, update)
    def makeOfferI(u: Unit, offer: Offer[B]) =
      k.makeOffer(u, offer)
    def composeI[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false		  
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  private final case class Upd[B,C,D](f: (A,B) => (A,C), k: Reagent[C,D]) 
		     extends Reagent[B, D] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(b: B, rx: Reaction): Any = {
      val ov = get
      val (nv, ret) = f(ov, b)
      Ref.continueWithCAS(data, rx, ret, k, ov, nv)
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

  private final case class UpdUnit[B,C](f: PartialFunction[A, (A,B)],
				        k: Reagent[B, C]) 
		     extends Reagent[Unit, C] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(u: Unit, rx: Reaction): Any = {
      val ov = get
      if (!f.isDefinedAt(ov)) return ShouldBlock
      val (nv, ret) = f(ov)
      Ref.continueWithCAS(data, rx, ret, k, ov, nv)
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

  private final case class UpdIn[B,C](f: (A,B) => A, k: Reagent[Unit, C]) 
		     extends Reagent[B, C] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(b: B, rx: Reaction): Any = {
      val ov = get
      val nv = f(ov, b)
      Ref.continueWithCAS(data, rx, (), k, ov, nv)
    } 
    def makeOfferI(b: B, offer: Offer[C]): Unit = {
      k.makeOffer((), offer)
    }
    def composeI[D](next: Reagent[C,D]) = UpdIn(f, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
  }
  @inline def updIn[B](f: (A,B) => A): Reagent[B, Unit] = 
    UpdIn(f, Commit[Unit]())
}
object upd {
  @inline def apply[A <: AnyRef,B,C](r: Ref[A])(f: (A,B) => (A,C)) = 
    r.upd(f)
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = 
    r.upd(f)
}
object updIn {
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: (A,B) => A) = 
    r.updIn(f)
}
object Ref {
  @inline def apply[A <: AnyRef](init: A): Ref[A] = new Ref(init)
  @inline def unapply[A <: AnyRef](r: Ref[A]): Option[A] = Some(r.get)

  @inline private[chemistry] def continueWithCAS[A, B](
    ref: AtomicReference[AnyRef], rx: Reaction, ret: A, 
    k: Reagent[A,B], ov: AnyRef, nv: AnyRef
  ): Any = 
    if (rx.casCount == 0 && k.alwaysCommits) {
      if (ref.compareAndSet(ov, nv))
	k.tryReact(ret, rx)
      else ShouldRetry
    } else {
      k.tryReact(ret, rx.withCAS(ref, ov, nv))
    }
}
