import System.out._
import com.codahale.simplespec.Spec

object BackoffSpec extends Spec {
  class `exponential backoff` {
    def `should take time` {
      val b = new Backoff()
      val start = System.currentTimeMillis
      for (i <- 1 to 12) b.spin
      val end = System.currentTimeMillis
      (end - start > 100) must be(true)
    }
  }
}
