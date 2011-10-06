// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

import java.util.concurrent.locks._
import java.util.concurrent.atomic._

private abstract class Offer[-A] extends DeletionFlag {
  // is the Offer still available?
  def isActive: Boolean

  // consume the Offer within the current reaction, and continue on with the
  // rest of a reagent
  def consumeAndContinue[B,C](
    completeWith: A,		// the value yielded to the Offerer
    continueWith: B,		// the value yielded to the continuation
    rx: Reaction,		// the reaction so far
    k: Reagent[B, C],		// the continuation
    enclosingOffer: Offer[C]	// the offer, if any, that the enclosing
				// reagent is making
  ): Any

  // an alias used for Offers appearing in Pools
  def isDeleted = !isActive 

  // used only when the Offer is enrolled in a Ref, and the value of the Ref
  // changes
  def abortAndWake: Unit
}

private class Catalyst[-A](dissolvent: Reagent[Unit,A]) extends Offer[A] {
  private val alive = new AtomicReference[Boolean](true)

  def isActive = alive.get
  def consumeAndContinue[B,C](
    completeWith: A, continueWith: B, 
    rx: Reaction, k: Reagent[B, C], enclosingOffer: Offer[C]
  ): Any = 
    k.tryReact(continueWith, rx, enclosingOffer)

  def abortAndWake {
    if (alive.compareAndSet(true, false)) 
      Reagent.dissolve(dissolvent) // reinstate the catalyst
  }
}

private object Waiter {
  abstract class WaiterStatus
  object Waiting extends WaiterStatus
  object Aborted extends WaiterStatus
}
private final class Waiter[-A](val blocking: Boolean) 
	      extends Offer[A] with DeletionFlag {
  import Waiter._

  private[chemistry] val status = new KCASRef[AnyRef](Waiting)

  // the thread that *created* the Waiter
  private val waiterThread = Thread.currentThread() 
  private def wake(u: Unit) {
    if (blocking) LockSupport.unpark(waiterThread)
  }
  
  @inline def isActive: Boolean = status.getI == Waiting

  // Poll current waiter value:
  //   - None if Waiting or Aborted
  //   - Some(ans) if completed with ans
  // sadly, have to use `Any` to work around variance problems
  @inline def poll: Option[Any] = status.getI match {
    case (_:WaiterStatus) => None
    case ans => Some(ans)
  }
  
  // Attempt to abort, returning true iff successful
  @inline def tryAbort = status.casI(Waiting, Aborted)
  @inline def rxWithAbort(rx: Reaction): Reaction =
    rx.withCAS(status, Waiting, Aborted)

  def abortAndWake = if (tryAbort) wake()

  @inline def tryComplete(a: A) = 
    status.casI(Waiting, a.asInstanceOf[AnyRef])
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

