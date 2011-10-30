package chemistry.bench.competition

import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection.immutable._

// Standard lock-free stack, due to Treiber.  Doesn't yet perform
// exponential backoff.
class HandStack[A >: Null] {
  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[List[A]](Nil)

  def push(x: A) {
    val backoff = new chemistry.Backoff
    while (true) {
      val cur = head.get
      val upd = x +: cur
      if (head compareAndSet (cur, upd)) return
      backoff.once
    } 
  }

  def tryPop(): Option[A] = {
    val backoff = new chemistry.Backoff
    while (true) {
      val cur = head.get
      if (cur.isEmpty) 
	return None
      else if (head.compareAndSet(cur,cur.tail)) 
	return Some(cur.head)      
      backoff.once
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
