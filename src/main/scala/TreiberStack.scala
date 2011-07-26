// An implementation of the classic Treiber stack via reagents

package chemistry

sealed class TreiberStack[A] {
  private val head = Ref[List[A]](List())

  final val push: Reagent[A,Unit] = head.upd { 
    (xs,x) => (x::xs, ())
  }

  final val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }

  final val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  }
}
