package chemistry.bench.competition

import java.util.concurrent.locks._
import scala.annotation.tailrec
import scala.collection.mutable._

// Queue w/ coarse-grained locking
class LockQueue[A >: Null] {
  private val queue = new Queue[A]()
  val lock  = new ReentrantLock()

  def enq(x: A) {
    lock.lock()
    queue.enqueue(x)
    lock.unlock()
  }

  def tryDeq(): Option[A] = {
    lock.lock()
    val ret = if (queue.isEmpty) None else Some(queue.dequeue())      
    lock.unlock()
    ret
  } 
}
