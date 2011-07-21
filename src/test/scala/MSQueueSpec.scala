import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import chemistry._

object MSQueueSpec extends Spec {
/*
  class `a queue` {    
    def `should dequeue as None when empty` {
      var q = new Queue[Integer]()
      q.dequeue must beNone
    }
    def `should dequeue as Some _ when full` {
      var q = new Queue[Integer]()
      q enqueue 1
      q.dequeue must beSome
    }
    def `should dequeue as None after dequeuing` {
      var q = new Queue[Integer]()
      q enqueue 1
      q.dequeue
      q.dequeue must beNone
    }
    def `should dequeue in order` {
      var q = new Queue[Integer]()
      q enqueue 1
      q enqueue 2
      (q.dequeue, q.dequeue) must beEqualTo(Some(1), Some(2))
    }
    // def `should enqueue from multiple threads in locally-ordered way` {
    //   var q = new Queue[Integer]()
    //   Threads.spawnAndJoin (List(
    // 	() => for (i <-      1 to 100000) q enqueue i,
    // 	() => for (i <- 100001 to 200000) q enqueue i))
    //   var left = 1
    //   var right = 100001
    //   var ok = true
    //   for (ii <- q.dequeueAll) {
    // 	val i = ii.intValue // yuck
    // 	if (i <= 100000) {
    // 	  if (left != i) {
    // 	    print("failed at ")
    // 	    print(left)
    // 	    print(" got ")
    // 	    println(i)
    // 	    ok = false
    // 	  }
    // 	  left = i+1
    // 	} else {
    // 	  if (right != i) {
    // 	    print("failed at ")
    // 	    print(right)
    // 	    print(" got ")
    // 	    println(i)
    // 	    ok = false
    // 	  }
    // 	  right = i+1	  
    // 	}
    //   }
    //   ok must beTrue
    // }
  }
  */
}
