// Message passing constructions: synchronous channels and swap
// channels.

package chemistry

import scala.annotation.tailrec

private abstract class Message[A,B] extends Reagent[A,B] with DeletionFlag 

final private case class CMessage[A,B](
  m: A, k: Reagent[B,Unit]
) extends Message[A,B] {
  def isDeleted = false
  def tryReact(
  val reagent = k compose ret(m)
}

final private case class RMessage[A,B,C](
  m: A, k: Reagent[B,C], waiter: Waiter[C]
) extends Message[A,B] {
  def isDeleted = waiter.status.read ! () match {
    case Waiting => false
    case Committed => true
  }
  val reagent = k compose ret(m) // need to add post-commit action and
				 // message consumption
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B]],
  incoming: Pool[Message[B,A]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  def tryReact(a: A, rx: Reaction): C = {
    // sadly, @tailrec not acceptable here due to exception handling
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null if retry => throw ShouldRetry
      case null          => throw ShouldBlock
      case incoming.Node(msg, next) => try {
	msg.reagent.compose(k).tryReact(a, rx)
      } catch {
	case ShouldRetry => retry = true; cursor = next
	case ShouldBlock => cursor = next
      }	      
    }    
    throw Util.Impossible
  }
  def compose[D](next: Reagent[C,D]) = 
    Endpoint(outgoing,incoming,k.compose(next))
}
object SwapChan {
  @inline def apply[A,B](): (Reagent[A,B], Reagent[B,A]) = {
    val p1 = new Pool[Message[A,B]]
    val p2 = new Pool[Message[B,A]]
    (Endpoint(p1,p2,Commit[B]()), Endpoint(p2,p1,Commit[A]()))
  }
}
object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}
