import java.util.concurrent.atomic._
import scala.annotation.tailrec

class Stack[A >: Null] {
  class Node(val data: A) {
    val next = new AtomicReference[Node](null)
  }

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  def push(x: A) {
    val n = new Node(x)   // n.next.get eq null when enqueued
    while (true) {
      val h = head.get
      n.next lazySet h
      if (head compareAndSet (h, n)) return
    } 
  }

  def pop: Option[A] = {
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else {
	val ht = h.next.get
	if (head compareAndSet (h, ht)) return Some(h.data) 
      }
    }
    throw new Exception("Impossible")
  } 

  // definitely possible to do this all at once with a CAS on head
  def popAll: List[A] = {
    @tailrec def grabAll(acc: List[A]): List[A] = 
      pop match {
	case None => acc
	case Some(x) => grabAll(x :: acc)
      }
    grabAll(List()).reverse
  }
}
