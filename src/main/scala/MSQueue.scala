// An implementation of the classic Michael-Scott queue via reagents

package chemistry

import scala.annotation.tailrec

final class MSQueue[A >: Null] {
  private final case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
  private val tail = Ref(head.read!())

  val enq: Reagent[A, Unit] = computed { (x:A) =>
    @tailrec def search: Reagent[Unit,Unit] = tail.read ! () match {
      case    Node(_, r@Ref(null)) => r.cas(null, Node(x))
      case ov@Node(_, Ref(nv))     => tail.cas(ov,nv) !? (); search
    }
    search
  }
  val tryDeq: Reagent[Unit, Option[A]] = head.upd[Option[A]] {
    case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
    case emp => (emp, None)
  }
  val deq: Reagent[Unit, A] = head.upd[A] {
    case Node(_, Ref(n@Node(x, _))) => (n, x)
  }
}


