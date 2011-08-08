// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

private abstract class Offer {
  
}

private object Catalyst extends Offer

final private class Waiter[A] {
  var answer: AnyRef, // will hold an A
  consumed: Ref[],
  thread: Thread
}
