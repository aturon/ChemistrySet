// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._

final class Ref[A <: AnyRef](init: A) {
//  private val waiters = new MSQueue[]()

  // really, the type of data should belong to Reaction
  private val data = new AtomicReference[AnyRef](init)
  private def get: A = Reaction.read(data).asInstanceOf[A]

  private final case class Read[B](k: Reagent[A,B]) extends Reagent[Unit,B] {
    def tryReact(u: Unit, rx: Reaction, offer: Offer[B]): B = 
      k.tryReact(get, rx, offer)
    def compose[C](next: Reagent[B,C]) = Read(k.compose(next))
  }
  @inline def read: Reagent[Unit,A] = Read(Commit())

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(u: Unit, rx: Reaction, offer: Offer[B]): B = 
      Ref.continueWithCAS(data, (), k, kIsCommit, expect, update, rx, offer)
    def compose[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  private final case class Upd[B,C,D](f: (A,B) => (A,C), k: Reagent[C,D]) 
		     extends Reagent[B, D] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(b: B, rx: Reaction, offer: Offer[D]): D = {
      val ov = get
      val (nv, ret) = f(ov, b)
      Ref.continueWithCAS(data, ret, k, kIsCommit, ov, nv, rx, offer)
    }
    def compose[E](next: Reagent[D,E]) = Upd(f, k.compose(next))
  }
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    Upd(f, Commit[C]())

  private final case class UpdUnit[B,C](f: PartialFunction[A, (A,B)],
				        k: Reagent[B, C]) 
		     extends Reagent[Unit, C] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(u: Unit, rx: Reaction, offer: Offer[C]): C = {
      val ov = get
      if (!f.isDefinedAt(ov)) throw ShouldBlock
      val (nv, ret) = f(ov)
      Ref.continueWithCAS(data, ret, k, kIsCommit, ov, nv, rx, offer)
    }
    def compose[D](next: Reagent[C,D]) = UpdUnit(f, k.compose(next))
  }
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    UpdUnit(f, Commit[B]())

  private final case class UpdIn[B,C](f: (A,B) => A, k: Reagent[Unit, C]) 
		     extends Reagent[B, C] {
    private val kIsCommit = k.isInstanceOf[Commit[_]]
    def tryReact(b: B, rx: Reaction, offer: Offer[C]): C = {
      val ov = get
      val nv = f(ov, b)
      Ref.continueWithCAS(data, (), k, kIsCommit, ov, nv, rx, offer)
    }
    def compose[D](next: Reagent[C,D]) = UpdIn(f, k.compose(next))
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

  @inline private def continueWithCAS[A <: AnyRef,B,C](
    ref: AtomicReference[AnyRef], ret: B, k: Reagent[B,C], 
    kIsCommit: Boolean, ov: A, nv: A, rx: Reaction, offer: Offer[C]
  ): C = 
    if (rx.casCount == 0 && kIsCommit)
      if (ref.compareAndSet(ov,nv))
	k.tryReact(ret, rx, offer)
      else throw ShouldRetry
    else k.tryReact(ret, rx.withCAS(ref, ov, nv), offer)
}
