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

// ****************************************************************
// ***** NOTE
//   NEED TO STORE PARTIAL REACTION IN MESSAGE,
//   AND SUPPORT CONCATENATION OF REACTIONS
// ****************************************************************

final private class RMessage[A,B,C](
  payload: A,		 // the actual content of the message
  senderRx: Reaction,	 // the checkpointed reaction of the sender
  senderK: Reagent[B,C], // the sender's reagent continuation
  waiter: Waiter[C]	 // the sender's waiter
) extends Message[A,B] {
  private case class CompleteExchange[D](receiverK: Reagent[A,D]) 
	       extends Reagent[C,D] {
    def tryReact(c: C, rx: Reaction, offer: Offer[D]): Any = {
      val newRX = 
	if (rx.canCASImmediate(receiverK, offer)) {
	  if (!waiter.tryComplete(c)) // attempt early, and
	    return Retry	      // retry early on failure
	  else rx		      
	} else waiter.rxWithCompletion(rx, c)

      if (waiter.blocking)
	receiverK.tryReact(payload, 
			   newRX.withPostCommit((_:Unit) => waiter.wake),
			   offer)
      else
	receiverK.tryReact(payload, newRX, offer)
    }
    def composeI[E](next: Reagent[D,E]): Reagent[C,E] =
      CompleteExchange(receiverK >=> next)
    def maySync = receiverK.maySync
    def alwaysCommits = false		
    def snoop(c: C) = waiter.isActive && receiverK.snoop(payload)
  }

  val exchange: Reagent[B, A] = senderK >=> CompleteExchange(Commit[A]())
  def isDeleted = !waiter.isActive
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B]],
  incoming: Pool[Message[B,A]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  def tryReact(a: A, rx: Reaction, offer: Offer[C]): Any = {
    @tailrec def tryFrom(n: incoming.Node, retry: Boolean): Any = 
      if (n == null) {
	if (retry) Retry else Block
      } else n.data.exchange.compose(k).tryReact(a, rx, offer) match {
	case Retry => tryFrom(n.next, true)
	case Block => tryFrom(n.next, retry)
	case ans   => ans
      }

    // send message if so requested.  note that we send the message
    // *before* attempting to react with existing messages in the
    // other direction.
    offer match { 
      case null => {}
      case (w: Waiter[_]) => outgoing.put(new RMessage(a, k, w))
      // todo: catalysts
    }

    tryFrom(incoming.cursor, false)
  }
  def snoop(a: A): Boolean = incoming.snoop
  @inline def composeI[D](next: Reagent[C,D]) = 
    Endpoint(outgoing, incoming, k.compose(next))
  @inline def maySync = true
  @inline def alwaysCommits = false
}
object SwapChan {
  @inline def apply[A,B](): (Reagent[A,B], Reagent[B,A]) = {
    val p1 = new CircularPool[Message[A,B]]
    val p2 = new CircularPool[Message[B,A]]
    (Endpoint(p1,p2,Commit[B]()), Endpoint(p2,p1,Commit[A]()))
  }
}
object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}
