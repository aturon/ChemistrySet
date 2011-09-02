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

  private val pushPool = new CircularPool[Offer[A]]
  private val popPool = new CircularPool[Offer[A]]

  def push(x: A) {
    val n = new Node(x, null)
    val backoff = new Backoff
    var offer: Offer[A] = null
    while (true) {
      n.next = head.get
      if (head.compareAndSet(n.next, n)) return

      val node = popPool.cursor
      if (node != null) {
	val theirOffer = node.data
	if (theirOffer.tryConsume) {
	  theirOffer.data = x
	  return 
	} 
      }

      if (offer eq null) offer = new Offer(x)
      offer.reset
      pushPool.put(offer)

      backoff.once(!offer.isDeleted && !popPool.snoop, 2)
//      backoff.once(!offer.isDeleted && popPool.cursor == null, 2)

      // val timeout = 128 << backoff
      // val t = System.nanoTime
      // while (!offer.isDeleted && System.nanoTime - t < timeout) {}

      if (offer.isDeleted || !offer.tryConsume) return 
    }
    throw Util.Impossible
  }

  def tryPop(): Option[A] = {
    val backoff = new Backoff
    var offer: Offer[A] = null
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 

      val node = pushPool.cursor
      if (node != null) { 
	val theirOffer = node.data
	if (theirOffer.tryConsume) {
	  while (theirOffer.data == null) {}
	  return Some(theirOffer.data)
	}
      }

      if (offer eq null) offer = new Offer[A](null)
      offer.reset
      popPool.put(offer)

      // val timeout = 128 << backoff
      // val t = System.nanoTime
      // while (!offer.isDeleted && System.nanoTime - t < timeout) {}

      backoff.once(!offer.isDeleted && !pushPool.snoop, 2)

      if (offer.isDeleted || !offer.tryConsume) {
	while (offer.data == null) {}
	return Some(offer.data)
      }
    }
    throw Util.Impossible
  }
}
