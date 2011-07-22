package chemistry

sealed class TreiberStack[A] {
  private val head = Ref[List[A]](List())

  final def push(x:A): Reagent[Unit] = head upd { 
    xs => (x::xs, ())
  }
  final val pop: Reagent[Option[A]] = head upd {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }
}
