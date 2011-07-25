// dirt simple sequential benchmarks

package chemistry

object Bench extends App {
  import java.util.Date

  val d = new Date()
  val trials = 200
  val iters = 100000

  val showDirect = true

  def getTime = (new Date()).getTime
  def withTime(msg: String, enabled: Boolean = true)(thunk: => Unit) {
    if (!enabled) return

    for (i <- 1 to 3) thunk // warm up
    var sum: Long = 0
    for (i <- 1 to trials) {
      System.gc()
      val t = getTime
      thunk
      val t2 = getTime
      // print(t2 - t)
      // print(" ")
      sum += (t2 - t)
//      System.gc()
    } 
//    println("")
    print("  ")
    print((trials * iters) / (1000 * sum))  // 1000 * sum for us
    print(" -- ")
    println(msg)
//    println(" iters/ms")
  }

  def doPush {
    println("Stacks: push only")

    withTime("Reagent-based") {		// 36101 baseline on Dell
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push ! d
      }
    }

    withTime("Direct", showDirect) {
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }
  }

  def doPushPop {
    println("Stacks: push and pop")

    withTime("Reagent-based") {		// 36101 baseline on Dell
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push ! d;
	s.push ! d;
	s.pop ! ()
	s.pop ! ()
      }
    }

    withTime("Direct", showDirect) {
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
	s.push(d)
	s.pop
	s.pop
      }
    }
  }

  def doEnq {
    println("Queues: enq only")

    withTime("Reagent-based") {				// 4,334/ms
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enq ! d 
      }
    }

    withTime("Direct", showDirect) {				// 7,142/ms
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enqueue(d)
      }
    }

  }

  def doEnqDeq {
    println("Queues: enq and deq")

    withTime("Reagent-based") {				// 4,334/ms
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enq ! d;
	s.deq ! ()
      }
    }

    withTime("Direct", showDirect) {				// 7,142/ms
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enqueue(d)
    	s.dequeue
      }
    }
  }

  doPush
  doPushPop
  doEnq
  doEnqDeq
}
