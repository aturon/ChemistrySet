// The Treiber stack with elimination-backoff, via reagents

package chemistry

final class EliminationStack[A >: Null] {
  private val stack = new TreiberStack[A]
  private val (elimPop, elimPush) = Chan[A]()

  val push: Reagent[A,Unit] = stack.push + elimPush
  val tryPop: Reagent[Unit,Option[A]] = stack.tryPop + elimPop.map(Some(_))
  def pop: Reagent[Unit,A] = stack.pop + elimPop
}
