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

final private class RMessage[A,B,C](
  m: A, k: Reagent[B,C], waiter: Waiter[C]
) extends Message[A,B] {
  private case class CompleteExchange[D](kk: Reagent[A,D]) 
	       extends Reagent[C,D] {
    type Cache = Retry
    def useCache = false
    def tryReact(c: C, rx: Reaction, cache: Cache): Any = {
      val newRX = Ref.rxWithCAS(rx, waiter.status, 
				Waiter.Waiting, c.asInstanceOf[AnyRef], kk)
      if (newRX == null) 
	RetryUncached
      else if (waiter.blocking)
	kk.tryReact(m, newRX.withPostCommit((_:Unit) => waiter.wake), null)
      else
	kk.tryReact(m, newRX, null)
    }
    def makeOfferI(c: C, offer: Offer[D]) = throw Util.Impossible
    def composeI[E](next: Reagent[D,E]): Reagent[C,E] =
      CompleteExchange(kk >=> next)
    def maySync = kk.maySync
    def alwaysCommits = false		
    def snoop(c: C) = waiter.isActive && kk.snoop(m)
  }

  val exchange: Reagent[B, A] = k >=> CompleteExchange(Commit[A]())
  def isDeleted = !waiter.isActive
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B]],
  incoming: Pool[Message[B,A]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  type Cache = Retry
  def useCache = false

  def tryReact(a: A, rx: Reaction, cache: Cache): Any = {
    @tailrec def tryFrom(n: incoming.Node, retry: Boolean): Any = 
      if (n == null) {
	if (retry) RetryUncached else Blocked
      } else n.data.exchange.compose(k).tryReact(a, rx, null) match {
	case (_: Retry) => tryFrom(n.next, true)
	case Blocked    => tryFrom(n.next, retry)
	case ans        => ans
      }
    tryFrom(incoming.cursor, false)
  }
  def snoop(a: A): Boolean = incoming.snoop
  def makeOfferI(a: A, offer: Offer[C]) {
    offer match { 
      case (w: Waiter[_]) => outgoing.put(new RMessage(a, k, w))
      // todo: catalysts
    }
    
    // todo: make offers enabled by outstanding messages
//    var cursor = incoming.cursor 
  }
  @inline def composeI[D](next: Reagent[C,D]) = 
    Endpoint(outgoing,incoming,k.compose(next))
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
