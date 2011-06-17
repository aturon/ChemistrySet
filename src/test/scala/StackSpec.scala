import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import chemistry._

object TreiberStackSpec extends Spec {
  class `a stack` {    
    def `should pop as None when empty` {
      var s = new TreiberStack[Integer]()
      s.pop must beNone
    }
    // def `should popAll as nil when empty` {
    //   var s = new TreiberStack[Integer]()
    //   s.popAll must have size(0)
    // }
    def `should pop as Some _ when full` {
      var s = new TreiberStack[Integer]()
      s push 1
      s.pop must beSome
    }
    // def `should popAll as nonempty when full` {
    //   var s = new TreiberStack[Integer]()
    //   s push 1
    //   s.popAll.size must be_>(0)
    // }
    def `should pop as None after emptying` {
      var s = new TreiberStack[Integer]()
      s push 1
      s.pop
      s.pop must beNone
    }
    // def `should pop as None after popAll` {
    //   var s = new TreiberStack[Integer]()
    //   s push 1
    //   s push 2
    //   s.popAll
    //   s.pop must beNone
    // }
    def `should pop in reverse order` {
      var s = new TreiberStack[Integer]()
      s push 1
      s push 2
      (s.pop, s.pop) must beEqualTo(Some(2), Some(1))
    }
    // def `should popAll in reverse order` {
    //   var s = new TreiberStack[Integer]()
    //   s push 1
    //   s push 2
    //   s.popAll must beEqualTo(List(2,1))
    // }
    // def `should push from multiple threads in locally-ordered way` {
    //   var s = new TreiberStack[Integer]()
    //   Threads.spawnAndJoin (List(
    // 	() => for (i <-      1 to 100000) s push i,
    // 	() => for (i <- 100001 to 200000) s push i))
    //   var left = 100000
    //   var right = 200000
    //   var ok = true
    //   for (ii <- s.popAll) {
    // 	val i = ii.intValue // yuck
    // 	if (i <= 100000) {
    // 	  if (left != i) {
    // 	    print("failed at ")
    // 	    print(left)
    // 	    print(" got ")
    // 	    println(i)
    // 	    ok = false
    // 	  }
    // 	  left = i-1
    // 	} else {
    // 	  if (right != i) {
    // 	    print("failed at ")
    // 	    print(right)
    // 	    print(" got ")
    // 	    println(i)
    // 	    ok = false
    // 	  }
    // 	  right = i-1	  
    // 	}
    //   }
    //   ok must beTrue
    // }
  }
}
