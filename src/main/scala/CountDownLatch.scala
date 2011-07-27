// One-time use count-down latch

package chemistry

final class CountDownLatch(count: Int) {
  private val state = new Counter(count)
  val getCount  = state.get
  val countDown = state.tryDec.map(_ => ())
  val await     = state.get.withFilter(_ == 0)
}
