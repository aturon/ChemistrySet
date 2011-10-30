package chemistry.bench.competition

import akka.stm._
import scala.collection.immutable._

class STMStack[A] {
  private val state = Ref[List[A]](Nil)
  def push(x: A) = atomic {
    state alter (x +: _)
  }
  def tryPop(): Option[A] = atomic {
    val cur = state.get
    if (cur.isEmpty)
      None
    else {
      state.set(cur.tail)
      Some(cur.head)
    }
  }
}
