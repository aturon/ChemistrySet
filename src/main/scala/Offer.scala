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
  object Aborted extends WaiterStatus
}
private final class Waiter[-A](val blocking: Boolean) extends Offer[A] {
  import Waiter._

  private[chemistry] val status: AtomicReference[AnyRef] = 
    new AtomicReference(Waiting)

  // the thread that *created* the Waiter
  private val waiterThread = Thread.currentThread() 
  def wake {
    if (blocking) LockSupport.unpark(waiterThread)
  }

//  def rxForConsume = Inert.withCAS(status, Waiting, Consumed)
//  def tryConsume: Boolean = status.compareAndSet(Waiting, Consumed)
  def isActive: Boolean = status.get == Waiting

  // sadly, have to use `Any` to work around variance problems
  def poll: Option[Any] = status.get match {
    case (_:WaiterStatus) => None
    case ans => Some(ans)
  }
  

  // sadly, have to use `Any` to work around variance problems
  // should only be called by original creater of the Waiter
  def abort: Option[Any] = 
    if (isActive && status.compareAndSet(Waiting, Aborted)) None
    else Some(status.get)

  def reset {
    status.set(Waiting)
  }
}

