package chemistry.bench.competition.hand2

import chemistry.Pool
import chemistry.Util
import chemistry.DeletionFlag
import java.util.concurrent.atomic._
import java.util.concurrent._
import scala.annotation.tailrec

private abstract class OfferStatus
private final case object Waiting extends OfferStatus
private final case object Aborted extends OfferStatus

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

private final class Offer[-A] extends DeletionFlag {
  private val status = new AtomicReference[AnyRef](Waiting)
  def reset = status.set(Waiting)
  def isDeleted = status.get match {
    case Waiting => false
    case _ => true
  }
  def tryConsume(a: A) = status.compareAndSet(Waiting, a.asInstanceOf[AnyRef])
  def abort: Option[Any] = 
    if (!isDeleted && status.compareAndSet(Waiting, Aborted)) None
    else Some(status.get)
}

private abstract class Message[A,B] extends DeletionFlag {
  def exchange: R[B,A]
}

final private case class RMessage[A,B](m: A, offer: Offer[B]) 
		   extends Message[A,B] {
  private case class CompleteExchange() extends R[B,A] {
    def tryReact(b: B): A = 
      if (offer.tryConsume(b)) m
      else throw ShouldBlock
    def makeOfferI(b: B, offer: Offer[A]) = throw Util.Impossible
    def maySync = false
  }

  def exchange: R[B, A] = CompleteExchange()
  def isDeleted = offer.isDeleted
}

private final case class EP[A,B](outgoing: Pool[Message[A,B]],
				 incoming: Pool[Message[B,A]]) 
		   extends R[A,B] {
  def tryReact(a: A): B = {
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null => throw ShouldRetry
      case incoming.Node(msg, next) => try {
	return msg.exchange.tryReact(a).asInstanceOf[B]
      } catch {
	case ShouldRetry => retry = true; cursor = next
	case ShouldBlock => cursor = next
      }	      
    }
    throw Util.Impossible
  }
  def makeOfferI(a: A, offer: Offer[B]) {
    outgoing.put(RMessage(a, offer))
  }
  def maySync = true
}
private object SwapChan {
  @inline def apply[A,B](): (R[A,B], R[B,A]) = {
    val p1 = new Pool[Message[A,B]]
    val p2 = new Pool[Message[B,A]]
    (EP(p1,p2), EP(p2,p1))
  }
}

abstract class R[-A,+B] {
  private[competition] def tryReact(a: A): B

  protected def makeOfferI(a: A, offer: Offer[B]): Unit
//  protected def composeI[C](next: Reagent[B,C]): Reagent[A,C] = throw Util.Imposible
//  private[competition] def alwaysCommits: Boolean
  private[competition] def maySync: Boolean

  private[competition] final def makeOffer(a: A, offer: Offer[B]) {
    // abort early if offer has already been consumed
    if (!offer.isDeleted) makeOfferI(a, offer)
  }

  final def !(x: A): B = {
    def offer: B = {
      val off = new Offer[B]
      var backoff = 0
      while (true) {
	off.reset
	makeOffer(x, off)

	// val timeout = 128 << backoff
	// val t = System.nanoTime
	// while (!off.isDeleted && System.nanoTime - t < timeout) {}

	var spins = (4 * 256) << backoff
	while (!off.isDeleted && spins > 0) spins -= 1

	off.abort match {
	  case Some(b) => return b.asInstanceOf[B]
	  case _ => {}
	}

	try return tryReact(x) catch {
	  case ShouldRetry => backoff += 1	  
	}
      }
      throw Util.Impossible
    }

    try return tryReact(x) catch {
      case ShouldRetry => offer
    }
  }
}

private final case class Choice[A,B](r1: R[A,B], r2: R[A,B]) 
		   extends R[A,B] {
  def tryReact(a: A): B =
    try r1.tryReact(a) catch {
      case ShouldRetry => r2.tryReact(a) 
    }
  def makeOfferI(a: A, offer: Offer[B]) {
    r1.makeOffer(a, offer)
    r2.makeOffer(a, offer)
  }
  def maySync = r1.maySync || r2.maySync
}

private class TStack[A >: Null] {
  class Node(val data: A, var next: Node) 

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  val pushTR = new R[A, Unit] {
    def tryReact(x: A): Unit = {
      val n = new Node(x, head.get)
      if (head compareAndSet (n.next, n)) return ()
      else throw ShouldRetry
    }
    def makeOfferI(a: A, offer: Offer[Unit]) {}
    def maySync = false
  }

  val tryPopTR = new R[Unit, A] {
    @tailrec final def tryReact(u: Unit): A = {
      val h = head.get
      if (h eq null) 
	return tryReact(u)
      else if (head compareAndSet (h, h.next)) 
	return h.data
      else throw ShouldRetry
    }
    def makeOfferI(a: Unit, offer: Offer[A]) {}
    def maySync = false
  }
}

private class EBStack[A >: Null] {
  private val stack = new TStack[A]
  private val (pushElim, popElim) = SwapChan[A,Unit]

/*
  private val pushOR = new R[A, Unit] {
    def tryReact(x: A, offer: Offer[Unit]): Unit = {
      popPool.cursor.get match {
	case popPool.Node(offer, _) => 
	  if (offer.tryConsume) {
	    offer.setAnswer(Some(x))
	    return
	  } 
	case _ => {}
      }
      pushPool.put(offer)
      throw ShouldRetry
    }
  }
*/
  val pushR: R[A, Unit] = Choice(stack.pushTR, pushElim)

/*
  private val tryPopOR = new R[Unit, A] {
    def tryReact(u: Unit, offer: Offer[A]): A = {
      pushPool.cursor.get match {
	case pushPool.Node(offer, _) => 
	  if (offer.tryConsume) {
	    val ans = offer.data
	    offer.setAnswer(null)
	    return ans.asInstanceOf[Option[A]]
	  }
	case _ => {}
      }
      popPool.put(offer)
      throw ShouldRetry
    }
  }
*/
  val tryPopR: R[Unit, A] = Choice(stack.tryPopTR, popElim)
}

class HandPoolStack2[A >: Null] {
  private val stack = new EBStack[A]

  def push(x: A) = stack.pushR ! x
  def tryPop(): Option[A] = Some(stack.tryPopR ! ())
}
