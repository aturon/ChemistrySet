import scala.annotation.tailrec
import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import chemistry._

object LaggingQueueSpec extends Spec {
  class `a queue` {        
    def `should tryDeq as None when empty` {
      var q = new LaggingQueue[java.lang.Integer]()
      q.tryDeq ! () must beNone
    }
    def `should tryDeq as Some _ when full` {
      var q = new LaggingQueue[java.lang.Integer]()
      q.enq ! 1;
      q.tryDeq ! () must beSome
    }
    def `should tryDeq as None after tryDequeuing` {
      var q = new LaggingQueue[java.lang.Integer]()
      q.enq!1;
      q.tryDeq!();
      q.tryDeq !() must beNone
    }
    def `should tryDeq in order` {
      var q = new LaggingQueue[java.lang.Integer]()
      q.enq! 1;
      q.enq! 2;
      (q.tryDeq! (), q.tryDeq! (), q.tryDeq! ()) must beEqualTo(Some(1), Some(2), None)
    }
    def `should enqueue from multiple threads in locally-ordered way` {
      var q = new LaggingQueue[java.lang.Integer]()
      TestUtil.spawnAndJoin (List(
    	() => for (i <-      1 to 100000) q.enq!i,
    	() => for (i <- 100001 to 200000) q.enq!i))
      var left = 1
      var right = 100001

      @tailrec def check: Boolean = q.tryDeq!() match {
	case None => true
	case Some (ii) => {
	  val i = ii.intValue // yuck
	  if (i <= 100000) {
	    if (left != i) {
	      print("failed at ")
	      print(left)
	      print(" got ")
	      println(i)
	      false
	    } else {
	      left += 1
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
	      right += 1
	      check
	    }
	  }
	}
      }
      check must beTrue
    }
  }
}

