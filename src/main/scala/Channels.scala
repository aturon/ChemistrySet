// Message passing constructions: synchronous channels and swap
// channels.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._

private abstract class Message[A,B] extends DeletionFlag {
  def exchange: Reagent[B,A]
}

/*
final private case class CMessage[A,B](
  m: A, k: Reagent[B,Unit]
) extends Message[A,B] {
  def isDeleted = false
  val reagent = k compose ret(m)
}
*/

final private case class RMessage[A,B,C](
  m: A, k: Reagent[B,C], waiter: Waiter[C]
) extends Message[A,B] {
  private case class CompleteExchange[D](kk: Reagent[A,D]) 
	       extends Reagent[C,D] {
    def tryReact(c: C, rx: Reaction, offer: Offer[D]): D =
      kk.tryReact(m, waiter.setAnswer(c) +: waiter.wake +: rx, offer)
    def compose[E](next: Reagent[D,E]): Reagent[C,E] =
      CompleteExchange(kk >=> next)
  }

  val exchange: Reagent[B, A] = k >=> CompleteExchange(Commit[A]())
  def isDeleted = waiter.isActive
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B]],
  incoming: Pool[Message[B,A]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  def tryReact(a: A, rx: Reaction, offer: Offer[C]): C = {
    offer match {
      case (w: Waiter[_]) => outgoing.put ! RMessage(a, k, w)
      case null => {} // do nothing
    }

    // sadly, @tailrec not acceptable here due to exception handling
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null if retry => throw ShouldRetry
      case null          => throw ShouldBlock
      case incoming.Node(msg, next) => try {
	return msg.exchange.compose(k).tryReact(a, rx, offer)
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
