// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._

final class Ref[A](init: A) extends AtomicReference[A](init) {
//  private final var data = new AtomicReference[A](init)

//  private val waiters = new MSQueue[]()

  private final case class Read[B](k: Reagent[A,B]) extends Reagent[Unit,B] {
    def tryReact(u: Unit, trans: Transaction): B = 
      k.tryReact(get(), trans)
    def compose[C](next: Reagent[B,C]) = Read(k.compose(next))
  }
  @inline def read: Reagent[Unit,A] = Read(Commit())

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    def tryReact[B](u: Unit, trans: Transaction): B ={
      if (compareAndSet(expect, update))
	k.tryReact((), trans)
      else throw ShouldRetry
    }
    def compose[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit()) 

  private final class Upd[B,C](f: (A,B) => (A,C)) extends Reagent[B, C] {
    def tryReact[D](b: B, trans: Transaction, k: K[C,D]): D = {
      val ov = get()
      val (nv, ret) = f(ov, b)
      if (compareAndSet(ov, nv))
	k.tryReact(ret, trans)
      else throw ShouldRetry
    }
  }
  private final class UpdUnit[B](f: PartialFunction[A, (A,B)]) 
		extends Reagent[Unit, B] {
    def tryReact[C](u: Unit, trans: Transaction, k: K[B,C]): C = {
      val ov = get()
      if (!f.isDefinedAt(ov)) throw ShouldBlock
      val (nv, ret) = f(ov)
      if (compareAndSet(ov, nv))
	k.tryReact(ret, trans)
      else throw ShouldRetry
    }
  }

  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    new Upd(f)
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    new UpdUnit(f)
}
object Ref {
  @inline def apply[A](init: A): Ref[A] = new Ref(init)
  def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}
object upd {
  @inline def apply[A,B,C](r: Ref[A])(f: (A,B) => (A,C)) = r.upd(f)
  @inline def apply[A,B](r: Ref[A])(f: PartialFunction[A, (A,B)]) = r.upd(f)
}
