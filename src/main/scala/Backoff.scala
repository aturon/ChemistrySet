// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
  private val rand = new Random(Thread.currentThread.getId)
  private var c = 0;

  def count = c

  def once() {
    if (c < 20) c += 1
    Util.noop(rand.next(16 << c))
  }
}
