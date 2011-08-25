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
    def tryReact(u: Unit, rx: Reaction): B = 
      k.tryReact(get, rx)
    def makeOfferI(u: Unit, offer: Offer[B]) = k.makeOffer(get, offer)
    def compose[C](next: Reagent[B,C]) = Read(k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = k.alwaysCommits
  }
  @inline def read: Reagent[Unit,A] = Read(Commit())					   

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    def tryReact(u: Unit, rx: Reaction): B = 
      Ref.continueWithCAS(data, rx, (), k, expect, update)
    def makeOfferI(u: Unit, offer: Offer[B]) =
      k.makeOffer(u, offer)
    def compose[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false		  
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  private abstract class Upd[B,C,D](k: Reagent[C,D]) extends Reagent[B,D] {
    @inline def compute(a: A, b: B): (A, C)
    @inline def isDefinedAt(a: A, b: B): Boolean

    def tryReact(b: B, rx: Reaction): D = {
      val ov = get
      if (!isDefinedAt(ov, b)) throw ShouldRetry
      val (nv, ret) = compute(ov, b)
      Ref.continueWithCAS(data, rx, ret, k, ov, nv)
    }
    def makeOfferI(b: B, offer: Offer[D]): Unit = {
      val ov = get
      if (!isDefinedAt(ov, b)) return
      val (_, ret) = compute(ov, b)
      k.makeOffer(ret, offer)
    }
    def compose[E](next: Reagent[D,E]) = new Upd[B,C,E](k.compose(next)) {
      @inline final def compute(a: A, b: B) = Upd.this.compute(a,b)
      @inline final def isDefinedAt(a: A, b: B) = Upd.this.isDefinedAt(a,b)
    }
    def maySync = k.maySync
    def alwaysCommits = false    
  }
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    new Upd[B,C,C](Commit[C]()) {
      @inline final def compute(a: A, b: B) = f(a,b)
      @inline final def isDefinedAt(a: A, b: B) = true
    }
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    new Upd[Unit, B, B](Commit[B]()) {
      @inline final def compute(a: A, u: Unit) = f(a)
      @inline final def isDefinedAt(a: A, u: Unit) = f.isDefinedAt(a)
    }
  @inline def updIn[B](f: (A,B) => A): Reagent[B, Unit] = 
    new Upd[B, Unit, Unit](Commit[Unit]()) {
      @inline final def compute(a: A, b: B) = (f(a,b), ())
      @inline final def isDefinedAt(a: A, b: B) = true
    }
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
    ref: AtomicReference[AnyRef], rx: Reaction, ret: A, k: Reagent[A,B], ov: AnyRef, nv: AnyRef
  ): B = 
    if (rx.casCount == 0 && k.alwaysCommits) {
      if (ref.compareAndSet(ov, nv))
	k.tryReact(ret, rx)
      else throw ShouldRetry
    } else {
      k.tryReact(ret, rx.withCAS(ref, ov, nv))
    }
}
