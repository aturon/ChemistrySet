package chemistry.bench.competition

import java.util.concurrent.locks._
import scala.annotation.tailrec
import scala.collection.mutable._

// Stack w/ coarse-grained locking
class LockStack[A >: Null] {
  private val stack = new Stack[A]()
  private val lock  = new ReentrantLock()

  def push(x: A) {
    lock.lock()
    stack.push(x)
    lock.unlock()
  }

  def tryPop(): Option[A] = {
    lock.lock()
    val ret = if (stack.isEmpty) None else Some(stack.pop())      
    lock.unlock()
    ret
  } 
}
