// An implementation of the classic Michael-Scott queue via reagents

package chemistry

import scala.annotation.tailrec

/*
final class MSQueue[A >: Null] {
  private final case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
//  private val tail = Ref(head.read!())
  private var tail = head.read!()

  val enq: Reagent[A, Unit] = computed { (x:A) =>
    val newNode = Node(x)
    @tailrec def search: Reagent[Unit,Unit] = tail match {
      case    Node(_, r@Ref(null)) => r.cas(null, newNode)
      case ov@Node(_, Ref(nv))     => tail = nv; search //tail.casI(ov,nv); search
    }
    search
  }
  // val tryDeq: Reagent[Unit, Option[A]] = head.upd[Option[A]] {
  //   case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
  //   case emp => (emp, None)
  // }
  object tryDeq extends Reagent[Unit,Option[A]] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[Option[A]]): Any = 
      head.getI match {
	case old@Node(_, Ref(n@Node(x, _))) =>
	  if (head.casI(old,n)) Some(x) else Retry
	case _ => None
      }
    def composeI[B](next: Reagent[Option[A],B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }

  val deq: Reagent[Unit, A] = head.upd[A] {
    case Node(_, Ref(n@Node(x, _))) => (n, x)
  }
}
*/

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
  // val tryDeq: Reagent[Unit, Option[A]] = head.upd[Option[A]] {
  //   case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
  //   case emp => (emp, None)
  // }
  object tryDeq extends Reagent[Unit,Option[A]] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[Option[A]]): Any = 
      head.data.get match {
	case null => Retry
	case old@Node(_, Ref(n@Node(x, _))) =>
	  if (head.data.compareAndSet(old,n)) Some(x) else Retry
	case _ => None
      }
    def composeI[B](next: Reagent[Option[A],B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }

  val deq: Reagent[Unit, A] = head.upd[A] {
    case Node(_, Ref(n@Node(x, _))) => (n, x)
  }
}


