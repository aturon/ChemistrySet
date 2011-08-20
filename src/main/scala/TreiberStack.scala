// An implementation of the classic Treiber stack via reagents

package chemistry

final class TreiberStack[A] {
  private val head = Ref[List[A]](List())

/*
  val push: Reagent[A,Unit] = head.fastUpd[A,Unit](
    { (xs,x) => x::xs },
    { (_, _) => () }
  )

  val tryPop: Reagent[Unit,Option[A]] = head.fastUpd[Unit,Option[A]](
    { case (x::xs, _) => xs
      case (emp,   _) => emp },
    { case (x::xs, _) => Some(x)
      case (emp,   _) => None }
  )
*/

  val push: Reagent[A,Unit] = head.updIn { 
    (xs,x) => x::xs
  }

  val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }

/*
  object push extends Reagent[A,Unit] {
    def tryReact(x:A, rx: Reaction, offer: Offer[Unit]) = {
      val xs = head.data.get.asInstanceOf[List[_]]
      if (head.data.compareAndSet(xs,x::xs)) () else throw ShouldRetry
    }
    def compose[B](next: Reagent[Unit,B]) = throw Util.Impossible
  }

  object tryPop extends Reagent[Unit,Option[A]] {
    def tryReact(u:Unit, rx: Reaction, offer: Offer[Option[A]]): Option[A] = head.data.get.asInstanceOf[List[A]] match {
      case (ov@(x::xs)) => 
	if (head.data.compareAndSet(ov,xs)) Some(x) else throw ShouldRetry
      case emp     => None
    }
    def compose[B](next: Reagent[Option[A],B]) = throw Util.Impossible
  }
*/

  val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  }
}
