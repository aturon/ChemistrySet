package chemistry.bench.competition

import akka.stm._
import scala.collection.immutable._

class STMQueue[A] {
  private val state = Ref(Queue.empty[A])
  def enq(x: A) = atomic { 
    state alter (_.enqueue(x))
  }
  def tryDeq() = atomic {
    val cur = state.get
    if (cur.isEmpty)
      None
    else {
      val (x, xs) = cur.dequeue
      state.set(xs)
      Some(x)
    }
  }
}
