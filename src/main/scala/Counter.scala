// A simple nonblocking counter.  Can be used to implement semaphores,
// or the equivalent to asynchronous unit channels in the join calculus.

package chemistry

final class Counter(init: Int = 0) {
  private val state = Ref[java.lang.Integer](init)

  val get = state.upd[Int] { case i => (i, i) }
  val inc = state.upd[Int] { case i => (i+1, i) }
  val dec = state.upd[Int] { case n if (n > 0) => (n-1, n) }
  val tryDec = state.upd[Option[Int]] {
     case n if (n == 0) => (0,   None)
     case n             => (n-1, Some(n))
  }
}
