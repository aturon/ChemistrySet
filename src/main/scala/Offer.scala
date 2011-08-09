// Representation of reagents offering to react:
//   - used to discover catalysts
//   - used to discover (possibly blocked) partners for SwapChans
//   - used to discover reagents blocked on Ref cell values

package chemistry

private abstract class WaiterStatus
private object Waiting extends WaiterStatus
private object Committed extends WaiterStatus

private abstract class Offer[A]
private object Catalyst extends Offer[Unit]
final private class Waiter[A] {
  var answer: AnyRef = null // will hold an A
  val status: Ref[WaiterStatus] = new Ref(Waiting)
  val thread: Thread = Thread.currentThread()
}
