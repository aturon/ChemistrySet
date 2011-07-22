import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import chemistry._

object QueueSpec extends Spec {
  class `a queue` {    
    def `should dequeue as None when empty` {
      var q = new MSQueue[Integer]()
      (q.deq !) must beNone
    }
    def `should dequeue as Some _ when full` {
      var q = new MSQueue[Integer]()
      q.enq(1)!;
      (q.deq !) must beSome
    }
    def `should dequeue as None after dequeuing` {
      var q = new MSQueue[Integer]()
      q.enq(1)!;
      q.deq !;
      (q.deq !) must beNone
    }
    def `should dequeue in order` {
      var q = new MSQueue[Integer]()
      q.enq(1)!;
      q.enq(2)!;
      (q.deq !, q.deq !, q.deq !) must beEqualTo(Some(1), Some(2), None)
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
}
