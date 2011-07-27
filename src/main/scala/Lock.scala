// Simple boolean flag lock implementation; nonreentrant

package chemistry

class IllegalRelease extends Exception

final class Lock {
  private sealed abstract class LockStatus
  private case object Locked extends LockStatus
  private case object Unlocked extends LockStatus

  private val status = Ref[LockStatus](Unlocked)

  val tryAcq: Reagent[Unit,Boolean] = status.upd[Boolean] {
    case Locked   => (Locked, false)
    case Unlocked => (Locked, true)
  }
  
  val acq: Reagent[Unit,Unit] = status.upd[Unit] {
    case Unlocked => (Locked, ())
  }

  val rel: Reagent[Unit,Unit] = status.upd[Unit] {
    case Locked => (Unlocked, ())
    case Unlocked => throw new IllegalRelease
  }
}
