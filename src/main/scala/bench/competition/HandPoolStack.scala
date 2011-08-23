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
  }

  private val pushPool = new Pool[Offer[A]]
  private val popPool = new Pool[Offer[A]]

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  def push(x: A) {
    val n = new Node(x, null)
    var backoff = 1
    while (true) {
      n.next = head.get
      if (head compareAndSet (n.next, n)) return

      popPool.cursor.get match {
	case popPool.Node(offer, _) => 
	  if (offer.tryConsume) {
	    offer.data = x
	    return
	  }
	case _ => {}
      }

      val offer = new Offer(x)
      pushPool.put ! offer

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
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 

      pushPool.cursor.get match {
	case pushPool.Node(offer, _) => 
	  if (offer.tryConsume) return Some(offer.data)
	case _ => {}
      }

      val offer = new Offer[A](null)
      popPool.put ! offer

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
