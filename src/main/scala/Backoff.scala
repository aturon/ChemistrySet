// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
  private var seed  = System.nanoTime
  private var count = 2;

  private def nextSeed() {
    seed = seed ^ (seed << 13)
    seed = seed ^ (seed >>> 7)
    seed = seed ^ (seed << 17)
  }

  def once() {
    if (count < 20) count += 1
    nextSeed
    Util.noop(seed.toInt % (1 << count))
  }
}
