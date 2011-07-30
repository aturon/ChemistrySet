// Exponential backoff.

package chemistry

import scala.util._

final class Backoff {
  private val rand = new Random()
  private var count = 0;
  def once() {
    if (count < 20) {
      count += 1
      for (i <- 1 to rand.nextInt(1 << count)) 
	rand.nextInt() // no-op that JIT won't optimize away
    } else {
      Thread.sleep(1)
    }
  }
}
