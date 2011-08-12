// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

import java.util.concurrent.locks._

private abstract class Offer[-A] {
  def commit: Reagent[A,Unit]
  def isActive: Boolean
}

private object Catalyst extends Offer[Unit] {
  val commit = Commit[Unit]()
  val isActive = true
}

private object Waiter {
  private abstract class WaiterStatus
  private object Waiting extends WaiterStatus
  private object Committed extends WaiterStatus
  private object Cancelled extends WaiterStatus
}
private final class Waiter[-A](blocking: Boolean) extends Offer[A] {
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

  private val status: Ref[WaiterStatus] = new Ref(Waiting)

  @inline private def setAnswer(a: A) {
    answer = a.asInstanceOf[AnyRef] // not sure if this will fly
    answerWritten = true // tell the reader that answer is now valid
  }

  val commit = {
    val pc = if (blocking) {     
      val thread = Thread.currentThread() // the thread that *created*
					  // the Waiter
      (a:A) => { setAnswer(a); LockSupport.unpark(thread) }
    } else setAnswer(_)
    postCommit(pc) >> status.cas(Waiting, Committed)
  }

  val cancel: Reagent[Unit,Unit] = status.cas(Waiting, Cancelled)

  def isActive: Boolean = status.read ! () == Waiting

  // sadly, have to use `Any` to work around variance problems
  @inline def poll: Option[Any] = if (isActive) None else {
    while (!answerWritten) {} // spin until answer is actually available
    Some(answer)
  }
}

