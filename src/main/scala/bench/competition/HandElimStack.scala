package chemistry.bench.competition

import java.util.concurrent.atomic._
import java.util.concurrent._
import scala.annotation.tailrec

// Elminiation backoff stack
class HandElimStack[A >: Null] {
  class Node(val data: A, var next: Node) 
  val elimArray = new Exchanger[A]

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  def push(x: A) {
    val n = new Node(x, null)
    var backoff = 1
    while (true) {
      n.next = head.get
      if (head compareAndSet (n.next, n)) return

      try elimArray.exchange(x, 128 << backoff, TimeUnit.NANOSECONDS) match {
	case null => return
	case _ => {}
      }
      catch {
	case _ => backoff += 1
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

      try elimArray.exchange(null, 128 << backoff, TimeUnit.NANOSECONDS) match {
	case null => {}
	case x => return Some(x)
      }
      catch {
	case _ => backoff += 1
      }
      
    }
    throw new Exception("Impossible")
  }
}
