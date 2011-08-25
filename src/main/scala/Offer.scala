// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

import java.util.concurrent.locks._
import java.util.concurrent.atomic._

private abstract class Offer[-A] {
  def isActive: Boolean
}

private object Catalyst extends Offer[Unit] {
  val isActive = true
}

private object Waiter {
  abstract class WaiterStatus
  object Waiting extends WaiterStatus
  object Consumed extends WaiterStatus
}
private final class Waiter[-A](val blocking: Boolean) extends Offer[A] {
  import Waiter._

  // Since the answer is written *after* the Waiter is marked as
  // Committed, there is a potential race condition between the thread
  // writing it and the woken thread reading it.  To avoid this race,
  // we add an extra flag, answerWritten, forcing a strict order.
  //
  // These two variables are likely to be on the same cache line, so
  // hopefully will be communicated together in the common case.
  @volatile private var answer: AnyRef = null // will hold an A
  @volatile private var answerWritten: Boolean = false

  private[chemistry] val status: AtomicReference[AnyRef] = 
    new AtomicReference(Waiting)

  def setAnswer(a: A) {
    answer = a.asInstanceOf[AnyRef] // not sure if this will fly
    answerWritten = true // tell the reader that answer is now valid
  }

  // the thread that *created* the Waiter
  private val waiterThread = Thread.currentThread() 
  def wake {
    if (blocking) LockSupport.unpark(waiterThread)
  }

  def rxForConsume = Inert.withCAS(status, Waiting, Consumed)
  def tryConsume: Boolean = status.compareAndSet(Waiting, Consumed)
  def isActive: Boolean = status.get == Waiting

  // sadly, have to use `Any` to work around variance problems
  def poll: Option[Any] = if (isActive) None else {
    while (!answerWritten) {} // spin until answer is actually available
    Some(answer)
  }

  // sadly, have to use `Any` to work around variance problems
  def abort: Option[Any] = 
    if (isActive && tryConsume) None
    else {
      while (!answerWritten) {} // spin until answer is actually available
      Some(answer)
    }

  def reset {
    status.set(Waiting)
  }
}

