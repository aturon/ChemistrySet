// Atomically updateable reference cells

package chemistry

import java.util.concurrent.atomic._
// import sun.misc._

final class Ref[A <: AnyRef](init: A) {
//  @volatile private var value: Any = init
//  private val waiters = new MSQueue[]()

  private val data = new AtomicReference[AnyRef](init)
  private def get: A = data.get.asInstanceOf[A]
  private def compareAndSet(ov: A, nv: A): Boolean = 
   data.compareAndSet(ov, nv)

//    Ref.unsafe.compareAndSwapObject(this, Ref.valueOffset, ov, nv)

  private final case class Read[B](k: Reagent[A,B]) extends Reagent[Unit,B] {
    def tryReact(u: Unit, rx: Reaction, offer: Offer[B]): B = 
      k.tryReact(get, rx, offer)
    def compose[C](next: Reagent[B,C]) = Read(k.compose(next))
  }
  @inline def read: Reagent[Unit,A] = Read(Commit())

  private final case class CAS[B](expect: A, update: A, k: Reagent[Unit,B]) 
		extends Reagent[Unit, B] {
    def tryReact(u: Unit, rx: Reaction, offer: Offer[B]): B = 
      k.tryReact((), rx.withCAS(data, expect, update), offer)
    def compose[C](next: Reagent[B,C]) = CAS(expect, update, k.compose(next))
  }
  @inline def cas(ov:A,nv:A): Reagent[Unit,Unit] = CAS(ov,nv,Commit[Unit]()) 

  private final case class Upd[B,C,D](f: (A,B) => (A,C), k: Reagent[C,D]) 
		     extends Reagent[B, D] {
    def tryReact(b: B, rx: Reaction, offer: Offer[D]): D = {
      val ov = get
      val (nv, ret) = f(ov, b)
      k.tryReact(ret, rx.withCAS(data, ov, nv), offer)
    }
    def compose[E](next: Reagent[D,E]) = Upd(f, k.compose(next))
  }
  @inline def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    Upd(f, Commit[C]())

  private final case class UpdUnit[B,C](f: PartialFunction[A, (A,B)],
				        k: Reagent[B, C]) 
		     extends Reagent[Unit, C] {
    def tryReact(u: Unit, rx: Reaction, offer: Offer[C]): C = {
      val ov = get
      if (!f.isDefinedAt(ov)) throw ShouldBlock
      val (nv, ret) = f(ov)
      k.tryReact(ret, rx.withCAS(data, ov, nv), offer)
    }
    def compose[D](next: Reagent[C,D]) = UpdUnit(f, k.compose(next))
  }
  @inline def upd[B](f: PartialFunction[A, (A,B)]): Reagent[Unit, B] = 
    UpdUnit(f, Commit[B]())
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

//  private val unsafe = Unsafe.getUnsafe()
//  private val valueOffset = 
//    unsafe.objectFieldOffset(classOf[Ref[_]].getDeclaredField("value"))
}
