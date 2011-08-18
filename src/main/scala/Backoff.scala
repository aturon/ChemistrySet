// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
  private val rand = new Random
  private var c = 1;

  def count = c

  def once() {
    if (c < 20) c += 1
    Util.noop(rand.next(1 << c))
  }
}
