// Message passing constructions: synchronous channels and swap
// channels.

package chemistry

import scala.annotation.tailrec

final private case class MessageWaiter[A,B](
  
) extends Deletion {
  def delete {
  }
  def isDeleted = false
}
private final class Endpoint[A,B] extends Reagent[A,B] {
  val holder = new Pool[Waiter[A,B]]()
  var dual: Endpoint[B,A] = null
  def tryReact[C](a: A, trans: Transaction, k: K[B,C]): C = {
    var retry: Boolean = false
    @tailrec def traverse(cursor: holder.Cursor): C = cursor.get match {
      case null => throw (if (retry) ShouldRetry else ShouldBlock)
      case holder.Node(data, next) => try {
	val b = data.
      } catch {
      }	
    }    
    traverse(holder.cursor)
  }
}
object SwapChan {
  @inline def apply[A,B](): (Reagent[A,B], Reagent[B,A]) = {
    val c1 = new Endpoint[A,B]
    val c2 = new Endpoint[B,A]
    c1.dual = c2; c2.dual = c1
    (c1, c2)
  }
}
object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}
