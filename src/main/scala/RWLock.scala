// Simple nonreentrant rw-lock implementation

package chemistry

//class IllegalRelease extends Exception

final class RWLock {
  private sealed abstract class LockStatus
  private case object Exclusive extends LockStatus
  private case class Shared(count: Ref[java.lang.Integer]) extends LockStatus
  private case object Unlocked extends LockStatus

  private val status = Ref[LockStatus](Unlocked)

/*
  val tryAcqRead: Reagent[Unit,Boolean] = status.upd[Boolean] {
    case Exclusive => (Locked, false)
    case Unlocked  => (Locked, true)
  }
  
  val acq: Reagent[Unit,Unit] = status.upd[Unit] {
    case Unlocked => (Locked, ())
  }

  val rel: Reagent[Unit,Unit] = status.upd[Unit] {
    case Locked   => (Unlocked, ())
    case Unlocked => throw new IllegalRelease
  }
*/
}
