// A two-way exchanger: unlike channels, exchangers do not distinguish
// one side from another

package chemistry

final class Exchanger[A] {
  private val (c, d) = SwapChan[A,A]()

  // could randomize order based on threadid -- or even build that in
  // to reagents.  note that this trick *only* works for 2-way
  // exchange!
  val exchange: Reagent[A,A] = c <+> d 
}
