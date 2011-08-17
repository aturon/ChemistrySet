// Internal representation of queued up actions making up a potential
// reaction.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

private abstract sealed class Reaction {
  import Reaction._

  def withPostCommit(postCommit: Unit => Unit): Reaction =
    PostCommit(postCommit, this)
  def withCAS(ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef): Reaction =
    CAS(ref, ov, nv, this)

  def tryCommit: Boolean = {
    val kcas = new KCAS(this)
    
    @tailrec def acquire(rx: Reaction): Boolean = 
      if (!kcas.isPending) false
      else rx match {
	case Inert => true
	case PostCommit(_, rest)    => acquire(rest)
	case CAS(ref, ov, nv, rest) => ref.get match {
	  case (owner: KCAS) => false // blocking version
	    // if (owner.curVal(ref) == ov) {
	    //   if (ref.compareAndSet(owner, kcas)) acquire(rest)
	    //   else false
	    // } else false
	  case curVal if (curVal == ov) => 
	    if (ref.compareAndSet(ov, kcas)) acquire(rest)
	    else false
	  case _ => false
	}
      }

    @tailrec def rollBack(rx: Reaction)

    if (acquire(this)) {
      if (kcas.complete) {
	true
      } else {
	// roll back
	false
      }
    } else {
	// roll back
      false
    }
  }
}

private object Reaction {
  private final case class PostCommit(
    action: Unit => Unit, rest: Reaction
  ) extends Reaction

  private final case class CAS(
    ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef, rest: Reaction
  ) extends Reaction

  private object KCAS {
    private sealed abstract class Status
    private final case object Pending extends Status
    private final case object Complete extends Status
    private final case object Aborted extends Status
  }
  final class KCAS(val rx: Reaction) {
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
}

private case object Inert extends Reaction
