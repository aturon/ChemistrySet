// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
//  private val rand = new Random(Thread.currentThread.getId)
  var seed: Long = Thread.currentThread.getId
  var count = 0

  def once() {
    if (count < 10) count += 1
    seed = Random.nextSeed(seed)
    Util.noop(Random.scale(seed, (Chemistry.procs-1) << (count + 2)))
  }

  def flip(n: Int): Boolean = {
    seed = Random.nextSeed(seed)
    seed % n == 0
  }

  @inline def once(until: => Boolean) {
    if (count < 20) count += 1
    seed = Random.nextSeed(seed)
    var spins = Random.scale(seed, (Chemistry.procs-1) << (count + 5))
//    var spins = (Chemistry.procs-1) << (count + 4)
    while (!until && spins > 0) spins -= 1
  }
}
