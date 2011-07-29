import scala.annotation.tailrec
import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import chemistry._

object StackSpec extends Spec {
  class `a stack` {    
    def `should tryPop as None when empty` {
      var s = new TreiberStack[java.lang.Integer]()
      s.tryPop ! () must beNone
    }
    // def `should tryPopAll as nil when empty` {
    //   var s = new TreiberStack[java.lang.Integer]()
    //   s.tryPopAll must have size(0)
    // }
    def `should tryPop as Some _ when full` {
      var s = new TreiberStack[java.lang.Integer]()
      s.push ! 1;
      s.tryPop ! () must beSome
    }
    // def `should tryPopAll as nonempty when full` {
    //   var s = new TreiberStack[java.lang.Integer]()
    //   s push 1
    //   s.tryPopAll.size must be_>(0)
    // }
    def `should tryPop as None after emptying` {
      var s = new TreiberStack[java.lang.Integer]()
      s.push ! 1;
      s.tryPop ! ();
      s.tryPop ! () must beNone
    }
    // def `should tryPop as None after tryPopAll` {
    //   var s = new TreiberStack[java.lang.Integer]()
    //   s push 1
    //   s push 2
    //   s.tryPopAll
    //   s.tryPop must beNone
    // }
    def `should tryPop in reverse order` {
      var s = new TreiberStack[java.lang.Integer]()
      s.push ! 1;
      s.push ! 2;
      (s.tryPop!(), s.tryPop!()) must beEqualTo(Some(2), Some(1))
    }
    // def `should tryPopAll in reverse order` {
    //   var s = new TreiberStack[java.lang.Integer]()
    //   s push 1
    //   s push 2
    //   s.tryPopAll must beEqualTo(List(2,1))
    // }

    def `should push from multiple threads in locally-ordered way` {
      var s = new TreiberStack[java.lang.Integer]()
      Threads.spawnAndJoin (List(
    	() => for (i <-      1 to 100000) s.push ! i,
    	() => for (i <- 100001 to 200000) s.push ! i))
      var left = 100000
      var right = 200000

      @tailrec def check: Boolean = s.tryPop!() match {
	case None => true
	case Some(ii) => {
	  val i = ii.intValue // yuck
	  if (i <= 100000) {
	    if (left != i) {
	      print("failed at ")
	      print(left)
	      print(" got ")
	      println(i)
	      false
	    } else {
	      left -= 1
	      check
	    }
	  } else {
	    if (right != i) {
	      print("failed at ")
	      print(right)
	      print(" got ")
	      println(i)
	      false
	    } else {
	      right -= 1	  
	      check
	    }
	  }
	}
      }
      check must beTrue
    }
  }
}
