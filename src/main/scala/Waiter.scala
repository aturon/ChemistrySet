// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

private abstract class Waiter[A] {
  
}

final private class Waiter[A] {
  var answer: AnyRef, // will hold an A
  status: Ref[WaiterStatus],
  thread: Thread // only relevant if reactant.  maybe should be rolled
		 // into WaiterStatus?
}

private abstract class WaiterStatus
private case object Catalyst extends WaiterStatus
private case object Waiting  extends WaiterStatus
private case object Finished extends WaiterStatus
