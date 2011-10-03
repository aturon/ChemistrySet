// Internal representation of queued up actions making up a potential
// reaction.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

private abstract sealed class Reaction {
  import Reaction._

  def casCount: Int

  // is it safe to do a CAS *while creating the reaction*?  generally, this is
  // fine as long as the whole reaction is guaranteed to be a 1-cas.
  def canCASImmediate[A,B](k: Reagent[A,B], offer: Offer[B]): Boolean = 
    casCount == 0 && k.alwaysCommits && (offer match {
      case null => true
      case Catalyst => true
      case (_: Waiter[_]) => false
    })

  def withPostCommit(postCommit: Unit => Unit): Reaction =
    PostCommit(postCommit, this)
  def withCAS(ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef): Reaction =
    CAS(ref, ov, nv, this)
  def withAbortOffer[A](offer: Offer[A]): Reaction = offer match {
    case null     => this
    case Catalyst => this // this case will probably never arise
    case (w: Waiter[_]) => w.rxWithAbort(this)
  }

  def tryCommit: Boolean = {
    val success: Boolean = casCount match {
      case 0 => true
      case 1 => {
	@tailrec def findAndTryCAS(rx: Reaction): Boolean = rx match {
	  case Inert => throw Util.Impossible
	  case PostCommit(_, rest)    => findAndTryCAS(rest)
	  case CAS(ref, ov, nv, rest) => ref.compareAndSet(ov, nv) 
	}
	findAndTryCAS(this)
      }
      case _ => {
	val kcas = new KCAS(this)

	// attempt to place KCAS record in each CASed reference.  returns null
	// if successful, and otherwise the point in the reaction to rollback
	// from.
	@tailrec def acquire(rx: Reaction): Reaction = 
	  if (!kcas.isPending) rx
	  else rx match {
	    case Inert => null
	    case PostCommit(_, rest)    => acquire(rest)
	    case CAS(ref, ov, nv, rest) => ref.get match {
	      case (owner: KCAS) => rx // blocking version
		// if (owner.curVal(ref) == ov) {
		//   if (ref.compareAndSet(owner, kcas)) acquire(rest)
		//   else false
		// } else false
	      case curVal if (curVal == ov) => 
		if (ref.compareAndSet(ov, kcas)) acquire(rest)
		else rx
	      case _ => rx
	    }
	  }

	@tailrec def rollBackBetween(start: Reaction, end: Reaction) { 
	  if (start != end) start match {
	    case Inert => throw Util.Impossible // somehow went past the `end`
	    case PostCommit(_, rest) => rollBackBetween(rest, end)
	    case CAS(ref, ov, nv, rest) => {
	      ref.compareAndSet(kcas, ov) // roll back to old value
	      rollBackBetween(rest, end)
	    }
	  }
	}

	// We roll forward the KCAS and perform the postCommits in two
	// separate passes, because we want to roll forward as quickly as
	// possible after acquisition.  That desire is motivated by cache
	// coherence concerns, which suggest that we currently own the cache
	// lines for the CASed refs.

	@tailrec def rollForward(rx: Reaction): Unit = rx match {
	  case Inert => {}
	  case PostCommit(_, rest) => rollForward(rest)
	  case CAS(ref, ov, nv, rest) => {
	    ref.compareAndSet(kcas, nv) // roll forward to new value
	    rollForward(rest)
	  }
	}    

	acquire(this) match {
	  case null => 
	    if (kcas.complete) { rollForward(this); true }
	    else	       { rollBackBetween(this, Inert); false }
	  case rx =>           { rollBackBetween(this, rx); false }
	}
      }
    }

    @tailrec def postCommits(rx: Reaction): Unit = rx match {
      case Inert => {}
      case PostCommit(pc, rest)   => pc(); postCommits(rest)
      case CAS(ref, ov, nv, rest) => postCommits(rest)
    }

    if (success) postCommits(this)
    success
  }
}

private object Reaction {
  private final case class PostCommit(
    action: Unit => Unit, rest: Reaction
  ) extends Reaction {
    def casCount = rest.casCount
  }

  private final case class CAS(
    ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef, rest: Reaction
  ) extends Reaction {
    def casCount = rest.casCount + 1
  }

  private object KCAS {
    private sealed abstract class Status
    private final case object Pending extends Status
    private final case object Complete extends Status
    private final case object Aborted extends Status
  }
  private final class KCAS(val rx: Reaction) {
    private val status = new AtomicReference[KCAS.Status](KCAS.Pending)
    def complete: Boolean = status.compareAndSet(KCAS.Pending,
						 KCAS.Complete)
    def abort:    Boolean = status.compareAndSet(KCAS.Pending,
						 KCAS.Aborted)
    def isPending: Boolean = status.get == KCAS.Pending

    def curVal(ref: AtomicReference[AnyRef]): AnyRef = {
      @tailrec def seek(rx: Reaction): AnyRef = rx match {
	case Inert => throw Util.Impossible // only happens if there's a bug
	case PostCommit(_, rest) => seek(rest)
	case CAS(casRef, ov, nv, rest) =>
	  if (ref == casRef) {
	    if (status.get == KCAS.Complete) nv else ov
	  } else seek(rest)
      }
      seek(rx)
    }
  }

  def read(ref: AtomicReference[AnyRef]): AnyRef = ref.get match {
    case (owner: KCAS) => owner.curVal(ref)
    case v => v
  }
}

private case object Inert extends Reaction {
  def casCount = 0
}
