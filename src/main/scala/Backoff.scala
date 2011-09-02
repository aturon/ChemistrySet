// Exponential backoff.

package chemistry

import scala.util._

private object Backoff {
  val maxCount: Int = 14
}
final class Backoff {
  import Backoff._

//  private val rand = new Random(Thread.currentThread.getId)
  var seed: Long = Thread.currentThread.getId
  var count = 0

  def once() {
    if (count == 0) 
      count = 1
    else {
      seed = Random.nextSeed(seed)
      Util.noop(Random.scale(seed, (Chemistry.procs-1) << (count + 2)))
      if (count < maxCount) count += 1
    }
  }

  def flip(n: Int): Boolean = {
    seed = Random.nextSeed(seed)
    seed % n == 0
  }

  @inline def once(until: => Boolean, mult: Int) {
//    if (count == 0) 
//      count = 1
//    else {
//      seed = Random.nextSeed(seed)
      val max = (Chemistry.procs-1) << (count + mult)
      var spins = max
//      var spins = Random.scale(seed, max)

      while (!until && spins > 0) spins -= 1
      if (count < maxCount) count += 1
    }
//  }
}
