// Message passing constructions: synchronous channels and swap
// channels.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._

final private class Message[A,B,C](
  payload: A,		 // the actual content of the message
  senderRx: Reaction,	 // the checkpointed reaction of the sender
  senderK: Reagent[B,C], // the sender's reagent continuation
  val offer: Offer[C]	 // the sender's offer
) extends DeletionFlag {
  private case class CompleteExchange[D](receiverK: Reagent[A,D]) 
	       extends Reagent[C,D] {
    def tryReact(c: C, rx: Reaction, enclosingOffer: Offer[D]): Any =      
      offer.consumeAndContinue(
	c, payload, rx ++ senderRx, receiverK, enclosingOffer)
    def composeI[E](next: Reagent[D,E]): Reagent[C,E] =
      CompleteExchange(receiverK >=> next)
    def maySync = receiverK.maySync
    def alwaysCommits = false
    def snoop(c: C) = offer.isActive && receiverK.snoop(payload)
  }

  val exchange: Reagent[B, A] = senderK >=> CompleteExchange(Commit[A]())
  def isDeleted = !offer.isActive
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B,_]],
  incoming: Pool[Message[B,A,_]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  def tryReact(a: A, rx: Reaction, offer: Offer[C]): Any = {
    // todo: avoid attempts of claiming the same message twice
    @tailrec def tryFrom(n: incoming.Node, retry: Boolean): Any = 
      if (n == null) {
	if (retry) Retry else Block
      } else if ((n.data.offer eq offer) || rx.hasOffer(n.data.offer)) {
	tryFrom(n.next, retry)
      } else n.data.exchange.compose(k).tryReact(a, rx.withOffer(n.data.offer), offer) match {
	case Retry => tryFrom(n.next, true)
	case Block => tryFrom(n.next, retry)
	case ans   => ans
      }

    // send message if so requested.  note that we send the message
    // *before* attempting to react with existing messages in the
    // other direction.
    if (offer != null) outgoing.put(new Message(a, rx, k, offer))

    // now attempt an immediate reaction
    tryFrom(incoming.cursor, false) match {
      case Block => Block // todo: fall back on "multithreaded" reagents
      case ow => ow
    }
  }
  def snoop(a: A): Boolean = incoming.snoop
  @inline def composeI[D](next: Reagent[C,D]) = 
    Endpoint(outgoing, incoming, k.compose(next))
  @inline def maySync = true
  @inline def alwaysCommits = false
}

object SwapChan {
  @inline def apply[A,B](): (Reagent[A,B], Reagent[B,A]) = {
    val p1 = new CircularPool[Message[A,B,_]]
    val p2 = new CircularPool[Message[B,A,_]]
    (Endpoint(p1,p2,Commit[B]()), Endpoint(p2,p1,Commit[A]()))
  }
}

object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}

// is there a possibility of building the exchanger directly here?
// also, can the endpoint logic be decomposed/parameterized more?
