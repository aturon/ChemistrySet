// an implementation of KCAS

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

/*
// a k-cas-able reference cell
class KCASRef[A <: AnyRef](init: A) {
  private[chemistry] val data = new AtomicReference[AnyRef](init)

  private[chemistry] def afterCAS {}

  @inline private[chemistry] final def getI = (data.get match {
    case (owner: KCAS) => owner.curVal(this)
    case v => v
  }).asInstanceOf[A]

  @inline private [chemistry] final def casI(ov: A, nv: A): Boolean = {
    val success = data.compareAndSet(ov, nv)
    if (success) afterCAS
    success
  }
}
*/

private final case class CAS[A <: AnyRef](
  ref: Ref[A], ov: A, nv: A
) {
  def execAsSingle: Boolean = ref.data.compareAndSet(ov, nv)
}

/*
private object KCAS {
  private sealed abstract class Status
  private final case object Pending extends Status
  private final case object Complete extends Status
  private final case object Aborted extends Status
}
private final class KCAS(val casList: List[CAS[_]]) {
  private val status = new AtomicReference[KCAS.Status](KCAS.Pending)
  private def isPending: Boolean = status.get == KCAS.Pending
  private def complete: Boolean = 
    status.compareAndSet(KCAS.Pending, KCAS.Complete)
  private def abort: Boolean = 
    status.compareAndSet(KCAS.Pending, KCAS.Aborted)

  def curVal(ref: KCASRef[_]): AnyRef = {
    casList find (_.ref == ref) match {
      case None => throw Util.Impossible // only happens if there's a bug
      case Some(CAS(_, ov, nv)) =>
	if (status.get == KCAS.Complete) nv else ov
    }
  }

  def tryCommit: Boolean = {
    // attempt to place KCAS record in each CASed reference.  returns null
    // if successful, and otherwise the point in the CAS list to rollback
    // from.
    @tailrec def acquire(casList: List[CAS[_]]): List[CAS[_]] = 
      if (!isPending) casList
      else casList match {
	case Nil => null
	case CAS(ref, ov, nv) :: rest => ref.data.get match {
	  case (owner: KCAS) => casList // blocking version
	    // if (owner.curVal(ref) == ov) {
	    //   if (ref.compareAndSet(owner, kcas)) acquire(rest)
	    //   else false
	    // } else false
	  case curVal if (curVal == ov) => 
	    if (ref.data.compareAndSet(ov, this)) acquire(rest)
	    else casList
	  case _ => casList
	}
      }

    @tailrec def rollBackBetween(start: List[CAS[_]], end: List[CAS[_]]) { 
      if (start != end) start match {
	case Nil => throw Util.Impossible // somehow went past the `end`
	case CAS(ref, ov, nv) :: rest => {
	  ref.data.compareAndSet(this, ov) // roll back to old value
	  rollBackBetween(rest, end)
	}
      }
    }

    def rollForward(casList: List[CAS[_]]): Unit = {
      // do all the CASes first, to propagate update as quickly as
      // possible
      casList.foreach {
	case CAS(ref, _, nv) => ref.data.compareAndSet(this, nv)
      }
      // now perform relevant post-CAS actions (e.g. waking up
      // waiters)
      casList.foreach {
	case CAS(ref, _, _) => ref.afterCAS
      }
    }

    acquire(casList) match {
      case null => 
	if (complete) { rollForward(casList); true }
	else	      { rollBackBetween(casList, Nil); false }
      case end =>     { rollBackBetween(casList, end); false }
    }
  }
}
*/

private object KCAS {
  def tryCommit(casList: List[CAS[_]]): Boolean = {
    // attempt to place KCAS record in each CASed reference.  returns null
    // if successful, and otherwise the point in the CAS list to rollback
    // from.
    @tailrec def acquire(casList: List[CAS[_]]): List[CAS[_]] = 
      casList match {
	case Nil => null
	case CAS(ref, ov, nv) :: rest => ref.data.get match {
	  case null => casList // blocking version
	  case curVal if (curVal == ov) => 
	    if (ref.data.compareAndSet(ov, null)) acquire(rest)
	    else casList
	  case _ => casList
	}
      }

    @tailrec def rollBackBetween(start: List[CAS[_]], end: List[CAS[_]]) { 
      if (start != end) start match {
	case Nil => throw Util.Impossible // somehow went past the `end`
	case CAS(ref, ov, nv) :: rest => {
	  ref.data.compareAndSet(null, ov) // roll back to old value
	  rollBackBetween(rest, end)
	}
      }
    }

    def rollForward(casList: List[CAS[_]]): Unit = {
      // do all the CASes first, to propagate update as quickly as
      // possible
      casList.foreach {
	case CAS(ref, _, nv) => ref.data.lazySet(nv)
      }
      // now perform relevant post-CAS actions (e.g. waking up
      // waiters)
      casList.foreach {
	case CAS(ref, _, _) => ref.afterCAS
      }
    }

    acquire(casList) match {
      case null => { rollForward(casList); true }
      case end  => { rollBackBetween(casList, end); false }
    }
  }
}
