// A concurrent, unordered bag, used to represent channels

package chemistry

import scala.annotation.tailrec

trait DeletionFlag {
  def isDeleted: Boolean
}

final class Pool[A <: DeletionFlag] {
  final case class Node(data: A, next: Cursor)
  val cursor = new Cursor(null)

  final class Cursor private[Pool](node: Node) {
    private[Pool] val ref = Ref(node)
    @tailrec def get: Node = ref.read ! () match {
      case null => null
      case n@Node(data, next) =>
	if (data.isDeleted) { 
//	  ref.cas(n, next.ref.read ! ()) !? ()
	  ref.data.lazySet(next.ref.data.get)
	  get
	} else n
    }
  }

  val put: Reagent[A,Unit] = cursor.ref.updIn {
    (xs,x) => Node(x, new Cursor(xs))
  }

/**** should these delete the acquired item? ****
  val tryTake = cursor.ref.upd[Option[A]] {
    case null => (null, None)
    case Node(data, next) => (next.ref.read ! (), Some(data))
  }
  val take = cursor.ref.upd[A] {
    case Node(data, next) => (next.ref.read ! (), data)
  }
*/
}
