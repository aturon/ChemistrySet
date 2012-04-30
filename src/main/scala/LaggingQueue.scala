// An implementation of the Michael-Scott queue via reagents which
// allows the tail pointer to lag arbitrarily

package chemistry

import scala.annotation.tailrec

final class LaggingQueue[A >: Null] {
  private final case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
  private var tail = head.read!()

  val enq: Reagent[A, Unit] = computed { (x:A) =>
    @tailrec def search: Reagent[Unit,Unit] = tail match {
      case Node(_, r@Ref(null)) => r.cas(null, Node(x))
      case Node(_, Ref(nv))     => tail = nv; search
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


