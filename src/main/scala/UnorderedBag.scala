// A concurrent, unordered bag; used to represent channels

package chemistry

import scala.annotation.tailrec

trait DeletionFlag {
  def isDeleted: Boolean
}

final class UnorderedBag[A <: DeletionFlag] {
  final case class Node(data: A, next: Cursor)
  val cursor = new Cursor(null)

  final class Cursor private[UnorderedBag](node: Node) {
    private[UnorderedBag] val ref = Ref(node)
    @tailrec def get: Node = ref.read ! () match {
      case null => null
      case n@Node(data, next) =>
	if (data.isDeleted) { 
	  ref.cas(n, next.ref.read ! ()) !? ()
	  get
	} else n
    }
  }

  val add: Reagent[A,Unit] = cursor.ref.upd {
    (xs,x) => (Node(x, new Cursor(xs)), ())
  }
}
