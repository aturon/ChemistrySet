// An implementation of the classic Treiber stack via reagents

package chemistry

final class TreiberStack[A] {
  private val head = Ref[List[A]](List())

  val push: Reagent[A,Unit] = head.upd { 
    (xs,x) => (x::xs, ())
  }

  val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }

  val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  }
}
