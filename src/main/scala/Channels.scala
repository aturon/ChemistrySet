// Message passing constructions: synchronous channels and swap
// channels.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.locks._

private abstract class Message[A,B] extends DeletionFlag {
  def exchange[C](k: Reagent[A,C]): Reagent[B,C]
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
    type Cache = Retry
    def useCache = false
    def tryReact(c: C, rx: Reaction, cache: Cache): Any = {
      Ref.rxWithCAS(rx, waiter.status, 
		    Waiter.Waiting, c.asInstanceOf[AnyRef], kk) match {
	case (rx: Reaction) if waiter.blocking =>
	  kk.tryReact(m, rx.withPostCommit((_:Unit) => waiter.wake), null)
	case (rx: Reaction) =>
	  kk.tryReact(m, rx, null)
	case ow => ow
      }
    }
    def makeOfferI(c: C, offer: Offer[D]) = throw Util.Impossible
    def composeI[E](next: Reagent[D,E]): Reagent[C,E] =
      CompleteExchange(kk >=> next)
    def maySync = kk.maySync
    def alwaysCommits = false		
  }

  def exchange[D](kk: Reagent[A,D]): Reagent[B, D] =
    k >=> CompleteExchange(kk)
  def isDeleted = !waiter.isActive
}

private final case class Endpoint[A,B,C]( 
  outgoing: Pool[Message[A,B]],
  incoming: Pool[Message[B,A]],
  k: Reagent[B,C]
) extends Reagent[A,C] {
  type Cache = Retry
  def useCache = false

  @inline def tryReact(a: A, rx: Reaction, cache: Cache): Any = {
    // sadly, @tailrec not acceptable here due to exception handling
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null if retry => return RetryUncached
      case null          => return Blocked
      case incoming.Node(msg, next) => 
	msg.exchange(k).tryReact(a, rx, null) match {
	  case (_: Retry) => retry = true; cursor = next
	  case Blocked    => cursor = next
	  case ans        => return ans
	} 
    }
    throw Util.Impossible
  }
  @inline def makeOfferI(a: A, offer: Offer[C]) {
    offer match { 
      case (w: Waiter[_]) => outgoing.put(RMessage(a, k, w))
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
    val p1 = new Pool[Message[A,B]]
    val p2 = new Pool[Message[B,A]]
    (Endpoint(p1,p2,Commit[B]()), Endpoint(p2,p1,Commit[A]()))
  }
}
object Chan {
  @inline def apply[A](): (Reagent[Unit,A], Reagent[A,Unit]) =
    SwapChan[Unit,A]()
}
