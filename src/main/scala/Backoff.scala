// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
//  private val rand = new Random(Thread.currentThread.getId)
  private var seed: Long = Thread.currentThread.getId
  private var c = 0;

  def count = c

  def once() {
    if (c < 10) c += 1
    seed = Random.nextSeed(seed)
    Util.noop(Random.scale(seed, (Chemistry.procs-1) << (c + 2)))
  }

  def once(until: => Boolean) {
    if (c < 20) c += 1
//    var spins = rand.next((Chemistry.procs-1) << (c + 8))
    var spins = (Chemistry.procs-1) << (c + 8)
    while (!until && spins > 0) spins -= 1
  }
}
