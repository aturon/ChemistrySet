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
    def tryReact(u: Unit, rx: Reaction, offer: Offer[B]): Any = 
      if (rx.canCASImmediate(k, offer)) {
	if (data.compareAndSet(expect, update))
	  k.tryReact((), rx, offer)
	else Retry
      } else k.tryReact((), rx.withCAS(data, expect, update), offer)

    def composeI[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
    def maySync = k.maySync
    def alwaysCommits = false
    def snoop(u: Unit) = false
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  abstract class InnerUpd[B,C,D] private[chemistry] (k: Reagent[C,D])
	   extends Reagent[B, D] {
    def tryReact(b: B, rx: Reaction, offer: Offer[D]): Any = {
      if (rx.canCASImmediate(k, offer)) {
	var tries = 3
	while (tries > 0) {
	  val ov = get
	  if (!valid(ov,b)) return Retry
	  val nv = newValue(ov, b)
	  if (data.compareAndSet(ov, nv))
	    return k.tryReact(retValue(ov, b), rx, offer)
	  tries -= 1
	}
	Retry
      } else {
	val ov = get
	if (!valid(ov,b)) return Retry
	val nv = newValue(ov, b)
	k.tryReact(retValue(ov, b), rx.withCAS(data, ov, nv), offer)
      }
    }
    def composeI[E](next: Reagent[D,E]) = 
      new InnerUpd[B,C,E](k.compose(next)) {
	final def newValue(a: A, b: B): A = InnerUpd.this.newValue(a, b)
	final def retValue(a: A, b: B): C = InnerUpd.this.retValue(a, b)
      }
    def maySync = k.maySync
    def alwaysCommits = false
    def snoop(b: B) = false

    def valid(a: A, b: B): Boolean = true
    def newValue(a: A, b: B): A
    def retValue(a: A, b: B): C
    def retryValue(cur: A, lastAttempt: A, b: B): A = newValue(cur, b)
  }
  abstract class Upd[B,C] extends InnerUpd[B,C,C](Commit[C]())

  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    new Upd[B,C] {
      @inline def newValue(a: A, b: B): A = f(a,b)._1
      @inline def retValue(a: A, b: B): C = f(a,b)._2
    }

  @inline def upd[B](f: PartialFunction[A,(A,B)]): Reagent[Unit, B] =
    new Upd[Unit,B] {
      @inline override def valid(a: A, u: Unit): Boolean = f.isDefinedAt(a)
      @inline def newValue(a: A, u: Unit): A = f(a)._1
      @inline def retValue(a: A, u: Unit): B = f(a)._2
    }
 
}
object upd {
  @inline def apply[A <: AnyRef,B,C](r: Ref[A])(f: (A,B) => (A,C)) = 
    r.upd(f)
  @inline def apply[A <: AnyRef,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = 
    r.upd(f)
}
object Ref {
  @inline def apply[A <: AnyRef](init: A): Ref[A] = new Ref(init)
  @inline def unapply[A <: AnyRef](r: Ref[A]): Option[A] = Some(r.get)
}
