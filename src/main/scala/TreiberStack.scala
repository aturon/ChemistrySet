// An implementation of the classic Treiber stack via reagents

package chemistry

sealed class TreiberStack[A] {
  private val head = Ref[List[A]](List())

  final def push(x:A): Reagent[Unit] = upd(head) { 
    xs => (x::xs, ())
  }
  final val pop: Reagent[Option[A]] = upd(head) {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }
}
