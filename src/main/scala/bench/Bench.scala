// dirt simple sequential benchmarks

package chemistry

import scala.collection.mutable._

object Bench extends App {
  import java.util.Date

  val d = new Date()
  val trials = 10
  val iters = 1000000

  var totDirect: Long = 0
  var totReagent: Long = 0

  def getTime = (new Date()).getTime
  def compare(title: String, direct: => Unit, reagent: => Unit) {
    def exec(thunk: => Unit): Seq[Long] = {
      for (i <- 1 to 3+ (trials/10)) thunk  // warm up
      val times: ArrayBuffer[Long] = ArrayBuffer[Long]()
      for (i <- 1 to trials) {
	System.gc()
	val t1 = getTime
	thunk
	val t2 = getTime	
	times += (t2 - t1) 
      }
      println(times)
      times
    }

    println(title)

    val sumDirect = exec(direct).sum
    val sumReagent = exec(reagent).sum
    
    print("  ")
    print((100*sumReagent) / sumDirect)  
    print(" = ")
    print((trials * iters) / (1 * sumDirect))  // 1000 for us
    print(" / ")
    println((trials * iters) / (1 * sumReagent))  
    println("")

    totReagent += sumReagent
    totDirect += sumDirect
  }

  def diPush {
    val s = new HandStack[java.util.Date]()
    for (i <- 1 to iters) s.push(d)
  }
  def rePush {		
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to iters) s.push ! d 
  }
  compare("Stacks: push only", diPush, rePush)
    
  def diPushPop {
    val s = new HandStack[java.util.Date]()
    for (i <- 1 to iters) {
      s.push(d)
      s.push(d)
      s.pop
      s.pop
    }
  } 
  def raPushPop {
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to iters) {
      s.push ! d;
      s.push ! d;
      s.pop ! ()
      s.pop ! ()
    }
  }
  compare("Stacks: push and pop", diPushPop, raPushPop)
  
  def diEnq {
    val s = new HandQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enqueue(d)
    }
  } 
  def raEnq {		
    val s = new MSQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq ! d 
    }
  }
  compare("Queues: enq only", diEnq, raEnq)
  
  def diEnqDeq {
    val s = new HandQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enqueue(d)
      s.dequeue
    }
  } 
  def raEnqDeq {
    val s = new MSQueue[java.util.Date]()
    for (i <- 1 to iters) {
      s.enq ! d;
      s.deq ! ()
    }
  }
  compare("Queues: enq and deq", diEnqDeq, raEnqDeq)
  
  println("")
  print("Weighted average: ")
  print((100*totReagent)/totDirect)
}
