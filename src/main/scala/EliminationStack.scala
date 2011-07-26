// The Treiber stack with elimination-backoff, via reagents

package chemistry

sealed class EliminationStack[A] {
  private val head = Ref[List[A]](List())
  private val (elimPop, elimPush) = Chan[A]()

  final val push: Reagent[A,Unit] = head.upd { 
    (xs,x:A) => (x::xs, ())
  } <+> elimPush

  final val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  } <+> elimPop.map(Some(_))

  final val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  } <+> elimPop
}
