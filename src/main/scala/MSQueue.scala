// An implementation of the classic Michael-Scott queue via reagents

package chemistry

import scala.annotation.tailrec

final class MSQueue[A >: Null] {
  private abstract class Q
  private final case class Node(data: A, next: Ref[Q] = Ref(Emp)) extends Q
  private final case object Emp extends Q
  private val head = Ref[Node](Node(null))
//  private val tail = Ref(head.read!())
  private var tail = head.read!()

  val enq: Reagent[A, Unit] = computed { (x:A) =>
    val newNode = Node(x)
    @tailrec def search: Reagent[Unit,Unit] = {
      val nextRef = tail.next
      val next = nextRef.data.get
      if (next eq null) search
      else if (next eq Emp) nextRef.cas(Emp, newNode)
      else {
	tail = next.asInstanceOf[Node]
	search
      }
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


