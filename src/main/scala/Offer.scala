// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

import java.util.concurrent.locks._
import java.util.concurrent.atomic._

private abstract class Offer[-A] {
  def isActive: Boolean
  def consumeAndContinue[B,C](
    completeWith: A, continueWith: B, 
    rx: Reaction, k: Reagent[B, C], enclosingOffer: Offer[C]
  ): Any
}

private object Catalyst extends Offer[Unit] {
  val isActive = true
  def consumeAndContinue[B,C](
    completeWith: Unit, continueWith: B, 
    rx: Reaction, k: Reagent[B, C], enclosingOffer: Offer[C]
  ): Any = 
    k.tryReact(continueWith, rx, enclosingOffer)
}

private object Waiter {
  abstract class WaiterStatus
  object Waiting extends WaiterStatus
  object Aborted extends WaiterStatus
}
private final class Waiter[-A](val blocking: Boolean) 
	      extends Offer[A] with DeletionFlag {
  import Waiter._

  private[chemistry] val status: AtomicReference[AnyRef] = 
    new AtomicReference(Waiting)

  // the thread that *created* the Waiter
  private val waiterThread = Thread.currentThread() 
  private def wake(u: Unit) {
    if (blocking) LockSupport.unpark(waiterThread)
  }
  
  @inline def isActive: Boolean = status.get == Waiting
  def isDeleted = !isActive // for use in pools

  // Poll current waiter value:
  //   - None if Waiting or Aborted
  //   - Some(ans) if completed with ans
  // sadly, have to use `Any` to work around variance problems
  @inline def poll: Option[Any] = status.get match {
    case (_:WaiterStatus) => None
    case ans => Some(ans)
  }
  
  // Attempt to abort, returning true iff successful
  @inline def tryAbort = status.compareAndSet(Waiting, Aborted)
  @inline def rxWithAbort(rx: Reaction): Reaction =
    rx.withCAS(status, Waiting, Aborted)

  @inline def tryComplete(a: A) = 
    status.compareAndSet(Waiting, a.asInstanceOf[AnyRef])
  @inline def rxWithCompletion(rx: Reaction, a: A): Reaction = 
    rx.withCAS(status, Waiting, a.asInstanceOf[AnyRef])

  def consumeAndContinue[B,C](
    completeWith: A, continueWith: B, 
    rx: Reaction, k: Reagent[B, C], enclosingOffer: Offer[C]
  ): Any = {
    val newRX = 
      if (rx.canCASImmediate(k, enclosingOffer)) {
	if (!tryComplete(completeWith)) // attempt early, and
	  return Retry	                // retry early on failure
	else rx		      
      } else rxWithCompletion(rx, completeWith)

    if (blocking)
      k.tryReact(continueWith, newRX.withPostCommit(wake), enclosingOffer)
    else
      k.tryReact(continueWith, newRX, enclosingOffer)    
  }

  // def reset { status.set(Waiting) }
}

