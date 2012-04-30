// An implementation of the classic Treiber stack via reagents

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._
import scala.collection.immutable._

final class TreiberStack[A >: Null] {
  private val head = new Ref[List[A]](Nil)

  val push: Reagent[A,Unit] = head.upd[A,Unit] { 
    case (xs,x) => (x::xs, ())
  }

  val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case x::xs => (xs,  Some(x))
    case Nil   => (Nil, None)
  }

  val pop: Reagent[Unit,A] = head.upd[A] {
    case x::xs => (xs, x)
  }
}
