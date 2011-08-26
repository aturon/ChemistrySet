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
    def tryReact(c: C, rx: Reaction): Any = {
      val newRX = rx.withPostCommit((_:Unit) => {
	waiter.setAnswer(c)
	waiter.wake
      })
      Ref.continueWithCAS(waiter.status, newRX, m, kk, 
			  Waiter.Waiting, Waiter.Consumed)
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
  def tryReact(a: A, rx: Reaction): Any = {
    // sadly, @tailrec not acceptable here due to exception handling
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null if retry => return ShouldRetry
      case null          => return ShouldBlock
      case incoming.Node(msg, next) => 
	msg.exchange(k).tryReact(a, rx) match {
	  case ShouldRetry => retry = true; cursor = next
	  case ShouldBlock => cursor = next
	  case ans         => return ans
	}	      
    }
    throw Util.Impossible
  }
  def makeOfferI(a: A, offer: Offer[C]) {
    offer match { 
      case (w: Waiter[_]) => outgoing.put(RMessage(a, k, w))
      // todo: catalysts
    }
    
    // todo: make offers enabled by outstanding messages
//    var cursor = incoming.cursor 
  }
  def composeI[D](next: Reagent[C,D]) = 
    Endpoint(outgoing,incoming,k.compose(next))
  def maySync = true
  def alwaysCommits = false
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
