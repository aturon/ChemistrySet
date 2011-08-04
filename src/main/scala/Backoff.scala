// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
  private val rand = new Random
  private var count = 1;

  def once() {
    if (count < 20) count += 1
    Util.noop(rand.next(1 << count))
  }
}
