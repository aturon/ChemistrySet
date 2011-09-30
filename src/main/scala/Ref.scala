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

  @inline def read: Reagent[Unit,A] = new AutoCont[Unit,A] {
    def retValue(u: Unit): Any = get
  }

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    def tryReact(u: Unit, rx: Reaction): Any = 
      if (rx.canCASImmediate(k)) {
	if (data.compareAndSet(expect, update))
	  k.tryReact((), rx)
	else Retry
      } else k.tryReact((), rx.withCAS(data, expect, update))

    def makeOfferI(u: Unit, offer: Offer[B]) =
      k.makeOffer(u, offer)
    def composeI[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
    def snoop(u: Unit) = false
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  private final case class Upd[B,C,D](f: (A,B) => (A,C), k: Reagent[C,D]) 
		     extends Reagent[B, D] {
    def tryReact(b: B, rx: Reaction): Any = {
      if (rx.canCASImmediate(k)) {
	// this bit is repeated: we want the actual CAS to be as close
	// to the read as possible, to decrease chance of interference.
	val ov = get
	val (nv, ret) = f(ov, b)
	if (data.compareAndSet(ov, nv)) 
	  k.tryReact(ret, rx) 
	else Retry
      } else {
	val ov = get
	val (nv, ret) = f(ov, b)
	k.tryReact(ret, rx.withCAS(data, ov, nv))
      }
    }
    def makeOfferI(b: B, offer: Offer[D]): Unit = {
      val ov = get
      val (_, ret) = f(ov, b)
      k.makeOffer(ret, offer)
    }
    def composeI[E](next: Reagent[D,E]) = Upd(f, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
    def snoop(b: B) = false
  }
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    Upd(f, Commit[C]())

  abstract class InnerFastUpd[B,C,D] private[chemistry] (k: Reagent[C,D])
	   extends Reagent[B, D] {
    def tryReact(b: B, rx: Reaction): Any = {
      if (rx.canCASImmediate(k)) {
	var tries = 3
	while (tries > 0) {
	  val ov = get
	  val nv = newValue(ov, b)
	  if (data.compareAndSet(ov, nv))
	    return k.tryReact(retValue(ov, b), rx)
	  tries -= 1
	}
	Retry
      } else {
	val ov = get
	val nv = newValue(ov, b)
	k.tryReact(retValue(ov, b), rx.withCAS(data, ov, nv))
      }
    }
    def makeOfferI(b: B, offer: Offer[D]): Unit = {
      val ov = get
      k.makeOffer(retValue(ov, b), offer)
    }
    def composeI[E](next: Reagent[D,E]) = 
      new InnerFastUpd[B,C,E](k.compose(next)) {
	final def newValue(a: A, b: B): A = InnerFastUpd.this.newValue(a, b)
	final def retValue(a: A, b: B): C = InnerFastUpd.this.retValue(a, b)
      }
    def maySync = k.maySync
    def alwaysCommits = false
    def snoop(b: B) = false

    def newValue(a: A, b: B): A
    def retValue(a: A, b: B): C
    def retryValue(cur: A, lastAttempt: A, b: B): A = newValue(cur, b)
  }
  abstract class FastUpd[B,C] extends InnerFastUpd[B,C,C](Commit[C]())

  
 
}
object upd {
  @inline def apply[A <: AnyRef,B,C](r: Ref[A])(f: (A,B) => (A,C)) = 
    r.upd(f)
/*
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = 
    r.upd(f)
*/
}
object Ref {
  @inline def apply[A <: AnyRef](init: A): Ref[A] = new Ref(init)
  @inline def unapply[A <: AnyRef](r: Ref[A]): Option[A] = Some(r.get)
}
