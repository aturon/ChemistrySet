// Internal representation of queued up actions making up a potential
// reaction.

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

private sealed class Reaction private (
  val casList: List[Reaction.CAS], 
  val pcList: List[Unit => Unit]) {
  import Reaction._
  
  def casCount: Int = casList.size

  // is it safe to do a CAS *while creating the reaction*?  generally, this is
  // fine as long as the whole reaction is guaranteed to be a 1-cas.
  def canCASImmediate[A,B](k: Reagent[A,B], offer: Offer[B]): Boolean = 
    casCount == 0 && k.alwaysCommits && (offer match {
      case null => true
      case (_: Catalyst[_]) => true
      case (_: Waiter[_]) => false
    })

  def withPostCommit(postCommit: Unit => Unit): Reaction =
    new Reaction(casList, postCommit +: pcList)
  def withCAS(ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef): Reaction =
    new Reaction(CAS(ref, ov, nv) +: casList, pcList)

  def ++(rx: Reaction): Reaction = 
    new Reaction(casList ++ rx.casList, pcList ++ rx.pcList)

  def tryCommit: Boolean = {
    val success: Boolean = casCount match {
      case 0 => true
      case 1 => casList.head.execAsSingle
      case _ => {
	val kcas = new KCAS(casList)

	// attempt to place KCAS record in each CASed reference.  returns null
	// if successful, and otherwise the point in the CAS list to rollback
	// from.
	@tailrec def acquire(casList: List[CAS]): List[CAS] = 
	  if (!kcas.isPending) casList
	  else casList match {
	    case Nil => null
	    case CAS(ref, ov, nv) :: rest => ref.get match {
	      case (owner: KCAS) => casList // blocking version
		// if (owner.curVal(ref) == ov) {
		//   if (ref.compareAndSet(owner, kcas)) acquire(rest)
		//   else false
		// } else false
	      case curVal if (curVal == ov) => 
		if (ref.compareAndSet(ov, kcas)) acquire(rest)
		else casList
	      case _ => casList
	    }
	  }

	@tailrec def rollBackBetween(start: List[CAS], end: List[CAS]) { 
	  if (start != end) start match {
	    case Nil => throw Util.Impossible // somehow went past the `end`
	    case CAS(ref, ov, nv) :: rest => {
	      ref.compareAndSet(kcas, ov) // roll back to old value
	      rollBackBetween(rest, end)
	    }
	  }
	}

	def rollForward(casList: List[CAS]): Unit = casList.foreach {
	  case CAS(ref, _, nv) => ref.compareAndSet(kcas, nv)
	}

	acquire(casList) match {
	  case null => 
	    if (kcas.complete) { rollForward(casList); true }
	    else	       { rollBackBetween(casList, Nil); false }
	  case end =>          { rollBackBetween(casList, end); false }
	}
      }
    }

    if (success) {
      pcList.foreach(_.apply())  // perform the post-commit actions
    }

    success
  }
}
private object Reaction { 
  final case class CAS(
    ref: AtomicReference[AnyRef], ov: AnyRef, nv: AnyRef
  ) {
    def execAsSingle: Boolean = ref.compareAndSet(ov, nv)
  }

  private object KCAS {
    private sealed abstract class Status
    private final case object Pending extends Status
    private final case object Complete extends Status
    private final case object Aborted extends Status
  }
  private final class KCAS(val casList: List[CAS]) {
    private val status = new AtomicReference[KCAS.Status](KCAS.Pending)
    def complete: Boolean = status.compareAndSet(KCAS.Pending,
						 KCAS.Complete)
    def abort:    Boolean = status.compareAndSet(KCAS.Pending,
						 KCAS.Aborted)
    def isPending: Boolean = status.get == KCAS.Pending

    def curVal(ref: AtomicReference[AnyRef]): AnyRef = {
      casList find (_.ref == ref) match {
	case None => throw Util.Impossible // only happens if there's a bug
	case Some(CAS(_, ov, nv)) =>
	  if (status.get == KCAS.Complete) nv else ov
      }
    }
  }

  def read(ref: AtomicReference[AnyRef]): AnyRef = ref.get match {
    case (owner: KCAS) => owner.curVal(ref)
    case v => v
  }

  val inert = new Reaction(Nil, Nil)
}
