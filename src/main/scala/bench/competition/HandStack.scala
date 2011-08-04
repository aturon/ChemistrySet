package chemistry.bench.competition

import java.util.concurrent.atomic._
import scala.annotation.tailrec

// Standard lock-free stack, due to Treiber.  Doesn't yet perform
// exponential backoff.
class HandStack[A >: Null] {
  class Node(val data: A, var next: Node) 

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  def push(x: A) {
    val n = new Node(x, null)
    while (true) {
      n.next = head.get
      if (head compareAndSet (n.next, n)) return
    } 
  }

  def tryPop(): Option[A] = {
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 
    }
    throw new Exception("Impossible")
  } 

  // definitely possible to do this all at once with a CAS on head
  def popAll(): List[A] = {
    @tailrec def grabAll(acc: List[A]): List[A] = 
      tryPop match {
	case None => acc
	case Some(x) => grabAll(x :: acc)
      }
    grabAll(List()).reverse
  }
}
 
