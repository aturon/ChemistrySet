// Internal representation of queued up actions making up a potential
// reaction.

package chemistry

import scala.collection.immutable.HashSet

private sealed class Reaction private (
  val casList: List[CAS[_]],		// k-cas built up so far
  val pcList: List[Unit => Unit],	// post-commit actions
  val offerSet: HashSet[Offer[_]]       // offers intended for consumption
) {
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
    new Reaction(casList, postCommit +: pcList, offerSet)
  def withCAS[A <: AnyRef](ref: Ref[A], ov: A, nv: A): Reaction =
    new Reaction(CAS(ref, ov, nv) +: casList, pcList, offerSet)
  def withOffer(offer: Offer[_]): Reaction = 
    new Reaction(casList, pcList, offerSet + offer)

  def ++(rx: Reaction): Reaction = 
    new Reaction(casList ++ rx.casList, pcList ++ rx.pcList, offerSet union rx.offerSet)

  def hasOffer(offer: Offer[_]): Boolean = offerSet.contains(offer)

  def tryCommit: Boolean = {
    val success: Boolean = casCount match {
      case 0 => true
      case 1 => casList.head.execAsSingle
      case _ => KCAS.tryCommit(casList)
    }
    if (success)
      pcList.foreach(_.apply())  // perform the post-commit actions
    success
  }
}
private object Reaction {
  val inert = new Reaction(Nil, Nil, HashSet.empty)
}
