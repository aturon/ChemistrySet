// dirt simple sequential benchmarks

package chemistry

object Bench extends App {
  import java.util.Date

  val d = new Date()
  val trials = 200
  val iters = 100000

  var totDirect: Long = 0
  var totReagent: Long = 0

  def getTime = (new Date()).getTime
  def compare(direct: => Unit)(reagent: => Unit) {
    for (i <- 1 to 3) { direct; reagent }  // warm up

    var sumDirect: Long = 0
    var sumReagent: Long = 0

    for (i <- 1 to trials) {
      System.gc()
      val t1 = getTime
      direct
      val t2 = getTime
      sumDirect += (t2 - t1)

      System.gc()
      val t3 = getTime
      reagent
      val t4 = getTime
      sumReagent += (t4 - t3)
    } 

    print("  ")
    print((100*sumReagent) / sumDirect)  
    print(" = ")
    print((trials * iters) / (1 * sumDirect))  // 1000 for us
    print(" / ")
    println((trials * iters) / (1 * sumReagent))  

    totReagent += sumReagent
    totDirect += sumDirect
  }

  def doPush {
    println("Stacks: push only")

    compare {
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    } {		
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push ! d
      }
    }
  }

  def doPushPop {
    println("Stacks: push and pop")

    compare {
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
	s.push(d)
	s.pop
	s.pop
      }
    } {
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push ! d;
	s.push ! d;
	s.pop ! ()
	s.pop ! ()
      }
    }
  }

  def doEnq {
    println("Queues: enq only")

    compare {
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enqueue(d)
      }
    } {		
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enq ! d 
      }
    }
  }

  def doEnqDeq {
    println("Queues: enq and deq")

    compare {
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enqueue(d)
    	s.dequeue
      }
    } {
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enq ! d;
	s.deq ! ()
      }
    }
  }

  doPush
  doPushPop
  doEnq
  doEnqDeq
  println("")
  print("Weighted average: ")
  print((100*totReagent)/totDirect)
}
