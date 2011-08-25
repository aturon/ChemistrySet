/*
package chemistry.bench.competition

import chemistry._
import java.util.concurrent.atomic._
import java.util.concurrent._
import scala.annotation.tailrec

private abstract class OfferStatus
private final case object Waiting extends OfferStatus
private final case object Consumed extends OfferStatus

// Elminiation backoff stack
class HandPoolStack[A >: Null] {
  class Node(val data: A, var next: Node) 

  private class Offer[A](@volatile var data: A) extends DeletionFlag {
    val status = new AtomicReference[OfferStatus](Waiting)
    def isDeleted = status.get match {
      case Waiting => false
      case Consumed => true
    }
    def tryConsume = status.compareAndSet(Waiting, Consumed)
    def reset { status.set(Waiting) }
  }

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

/*
  private val pushPool = new ArrayPool[Offer[A]]
  private val popPool = new ArrayPool[Offer[A]]

  def push(x: A) {
    val n = new Node(x, null)
    var backoff = 1  
    while (true) {
      n.next = head.get
      if (head.compareAndSet(n.next, n)) return

      popPool.get(popPool.cursor)match {
	case null => {}
	case theirOffer => {
	  if (theirOffer.tryConsume) {
	    theirOffer.data = x
	    return
	  } 
	}
      }

      val offer = new Offer(x)
      if (pushPool.tryPut(offer)) {
	val timeout = 128 << backoff
	val t = System.nanoTime
	while (!offer.isDeleted && System.nanoTime - t < timeout) {}

	if (offer.isDeleted || !offer.tryConsume) return
	backoff += 1
      }
    }
  }

  def tryPop(): Option[A] = {
    var backoff = 1
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 

      pushPool.get(pushPool.cursor) match {
	case null => {}
	case theirOffer => 
	  if (theirOffer.tryConsume) return Some(theirOffer.data)	
      }

      val offer = new Offer[A](null)
      if (popPool.tryPut(offer)) {
	val timeout = 128 << backoff
	val t = System.nanoTime
	while (!offer.isDeleted && System.nanoTime - t < timeout) {}

	if (offer.isDeleted || !offer.tryConsume) {
	  while (offer.data == null) {}
	  return Some(offer.data)
	}
	backoff += 1
      }
    }
    throw new Exception("Impossible")
  }
*/

  private val pushPool = new Pool[Offer[A]]
  private val popPool = new Pool[Offer[A]]

  def push(x: A) {
    val n = new Node(x, null)
    var backoff = 1
    val offer = new Offer(x)
    while (true) {
      n.next = head.get
      if (head.compareAndSet(n.next, n)) return

      // offer.reset
      // pushPool.put(offer)

      val node = popPool.cursor.get 
      node match {
	case null => {}
	case popPool.Node(theirOffer, _) => {
//	  if (offer.isDeleted || !offer.tryConsume) return
	  if (theirOffer.tryConsume) {
	    theirOffer.data = x
	    return
	  } 
	  // offer.reset
	  // pushPool.put(offer)
	}
      }

      offer.reset
      pushPool.put(offer)

      val timeout = 128 << backoff
      val t = System.nanoTime
      while (!offer.isDeleted && System.nanoTime - t < timeout) {}

      if (offer.isDeleted) return
      else if (!offer.tryConsume) return
      backoff += 1
    }
  }

  def tryPop(): Option[A] = {
    var backoff = 1
    val offer = new Offer[A](null)
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 

      // offer.reset
      // popPool.put(offer)

      val node = pushPool.cursor.get       
      node match {
	case null => {}
	case pushPool.Node(theirOffer, _) => {
	  // if (offer.isDeleted || !offer.tryConsume) {
	  //   while (offer.data == null) {}
	  //   return Some(offer.data)
	  // }
	  if (theirOffer.tryConsume) return Some(theirOffer.data)
	  // offer.reset
	  // popPool.put(offer)
	}
      }

      offer.reset
      popPool.put(offer)

      val timeout = 128 << backoff
      val t = System.nanoTime
      while (!offer.isDeleted && System.nanoTime - t < timeout) {}

      if (offer.isDeleted || !offer.tryConsume) {
	while (offer.data == null) {}
	return Some(offer.data)
      }
      backoff += 1
    }
    throw new Exception("Impossible")
  }
}
*/

package chemistry.bench.competition

import chemistry.Pool
import chemistry.Util
import chemistry.DeletionFlag
import java.util.concurrent.atomic._
import java.util.concurrent._
import scala.annotation.tailrec

private abstract class OfferStatus
private final case object Waiting extends OfferStatus
private final case object Consumed extends OfferStatus

private sealed abstract class BacktrackCommand extends Exception
private case object ShouldBlock extends BacktrackCommand
private case object ShouldRetry extends BacktrackCommand

private class Offer[A](@volatile var data: A) extends DeletionFlag {
  val status = new AtomicReference[OfferStatus](Waiting)
  def isDeleted = status.get match {
    case Waiting => false
    case Consumed => true
  }
  def tryConsume = status.compareAndSet(Waiting, Consumed)
}

abstract class R[A,B,C] {
  private[competition] def tryReact(a: A, offer: Offer[C]): B
  // final def !(a: A): B = {
  // }		     
}

private final case class Choice[A,B,C](r1: R[A,B,C], r2: R[A,B,C]) 
		   extends R[A,B,C] {
  def tryReact(a: A, offer: Offer[C]): B =
    try r1.tryReact(a, offer) catch {
      case ShouldRetry => r2.tryReact(a, offer) 
    }
}

/*
private abstract class Message[A,B] extends DeletionFlag {
  def exchange: R[B,A]
}

final private case class RMessage[A,B](m: A, offer: Offer[B]) 
		   extends Message[A,B] {
  private case object CompleteExchange extends R[B,A] {
    def tryReact(b: B, offer2: Offer[A]): A = 
      if (offer.tryConsume) {
	offer.data = b
	m
      } else throw ShouldBlock    
  }

  val exchange: R[B, A] = CompleteExchange
  def isDeleted = offer.isActive
}

private final case class EP[A,B](outgoing: Pool[Message[A,B]],
				 incoming: Pool[Message[B,A]]) 
		   extends R[A,B] {
  def tryReact(a: A, offer: B): B = {
    var cursor = incoming.cursor
    var retry: Boolean = false
    while (true) cursor.get match {
      case null if retry => throw ShouldRetry
      case null          => {
	outgoing.fastPut(RMessage(a, offer))
	throw ShouldRetry
      }
      case incoming.Node(msg, next) => try {
	return msg.exchange.tryReact(a, rx, null).asInstanceOf[C]
      } catch {
	case ShouldRetry => retry = true; cursor = next
	case ShouldBlock => cursor = next
      }	      
    }
    throw Util.Impossible
  }
}
private object SwapChan {
  @inline def apply[A,B](): (R[A,B], R[B,A]) = {
    val p1 = new Pool[Message[A,B]]
    val p2 = new Pool[Message[B,A]]
    (Endpoint(p1,p2), Endpoint(p2,p1))
  }
}
*/

class HandPoolStack[A >: Null] {
  class Node(val data: A, var next: Node) 

  private val pushPool = new Pool[Offer[A]]
  private val popPool = new Pool[Offer[A]]

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  private val pushR: R[A, Unit, A] =
    Choice(
      new R[A, Unit, A] {
	def tryReact(x: A, offer: Offer[A]): Unit = {
	  val n = new Node(x, head.get)
	  if (head compareAndSet (n.next, n)) return ()
	  else throw ShouldRetry
	}
      },
      new R[A, Unit, A] {
	def tryReact(x: A, offer: Offer[A]): Unit = {
	  popPool.cursor.get match {
	    case popPool.Node(offer, _) => 
	      if (offer.tryConsume) {
		offer.data = x
		return
	      } 
	    case _ => {}
	  }
	  pushPool.put(offer)
	  throw ShouldRetry
	}
      }
    )

  def push(x: A) {
    var backoff = 1
    while (true) {
      val offer = new Offer[A](x)
      try return pushR.tryReact(x, offer) catch {
	case ShouldRetry => {
	  val timeout = 128 << backoff
	  val t = System.nanoTime
	  while (!offer.isDeleted && System.nanoTime - t < timeout) {}

	  if (offer.isDeleted) return ()
	  else if (!offer.tryConsume) return ()
	  backoff += 1
	}
      }
    }
  }

  private val tryPopR: R[Unit, Option[A], A] =
    Choice(
      new R[Unit, Option[A], A] {
	def tryReact(u: Unit, offer: Offer[A]): Option[A] = {
	  val h = head.get
	  if (h eq null) 
	    return None
	  else if (head compareAndSet (h, h.next)) 
	    return Some(h.data)
	  else throw ShouldRetry
	}
      },
      new R[Unit, Option[A], A] {
	def tryReact(u: Unit, offer: Offer[A]): Option[A] = {
	  pushPool.cursor.get match {
	    case pushPool.Node(offer, _) => 
	      if (offer.tryConsume) return Some(offer.data)
	    case _ => {}
	  }
	  popPool.put(offer)
	  throw ShouldRetry
	}
      }
    )

  def tryPop(): Option[A] = {
    var backoff = 1
    while (true) {
      val offer = new Offer[A](null)
      try return tryPopR.tryReact((), offer) catch {
	case ShouldRetry => {
	  val timeout = 128 << backoff
	  val t = System.nanoTime
	  while (!offer.isDeleted && System.nanoTime - t < timeout) {}

	  if (offer.isDeleted || !offer.tryConsume) {
	    while (offer.data == null) {}
	    return Some(offer.data)
	  } 
	  backoff += 1
	}
      }
    }
    throw new Exception("Impossible")
  }
}
