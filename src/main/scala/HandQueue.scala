package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

// Standard lock-free queue due to Michael and Scott.  Doesn't yet
// perform exponential backoff.
class HandQueue[A >: Null] {
  class Node(val data: A) {
    val next = new AtomicReference[Node](null)
  }

  private val sentinel = new Node(null);

  // head always points to sentinel node,
  //   from which the rest of the queue's data is reachable
  private val head = new AtomicReference[Node](sentinel)

  // tail may lag behind, but the final node in the list
  //   (which has .next eq null) must be reachable
  private val tail = new AtomicReference[Node](sentinel) 

  @inline private def getAndGetNext(ar: AtomicReference[Node]): (Node, Node) = {
    val n = ar.get
    (n, n.next.get) // note: n.next ne null, but n.next.get might
  }    

  def enqueue(x: A) {
    val n = new Node(x)   // n.next.get eq null when enqueued
    while (true) {
      getAndGetNext(tail) match {
	case (t, null) => if (t.next.compareAndSet(null, n)) return
	case (t, tt  ) => tail.compareAndSet(t, tt)
      }
    }
  }

  def dequeue: Option[A] = {
    while (true) {
      getAndGetNext(head) match {
	case (h, null) => return None
	case (h, ht  ) => if (head.compareAndSet(h, ht)) return Some(ht.data) 
      }
    }
    throw new Exception("Impossible")
  } 

  // is it possible to be smarter here, e.g.
  // by CASing on head and/or tail to grab the
  // whole queue at once?
  def dequeueAll: List[A] = {
    @tailrec def grabAll(acc: List[A]): List[A] = 
      dequeue match {
	case None => acc
	case Some(x) => grabAll(x :: acc)
      }
    grabAll(List()).reverse
  }
}
