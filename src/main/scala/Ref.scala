// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._

final class Ref[A](init: A) extends AtomicReference[A](init) {
//  private final var data = new AtomicReference[A](init)

//  private val waiters = new MSQueue[]()

  case object read extends Reagent[Unit, A] {
    def tryReact[B](u: Unit, trans: Transaction, k: K[A,B]): B = 
      k.tryReact(get(), trans)
  }

  private final class CAS(expect: A, update: A) extends Reagent[Unit, Unit] {
    def tryReact[B](u: Unit, trans: Transaction, k: K[Unit,B]): B ={
      if (compareAndSet(expect, update))
	k.tryReact((), trans)
      else throw ShouldRetry
    }
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = new CAS(ov,nv) 

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
