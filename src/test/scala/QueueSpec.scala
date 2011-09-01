import scala.annotation.tailrec
import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import com.codahale.simplespec.annotation.test
import chemistry._

trait QueueTests {
  import org.specs2.matcher.MustMatchers._

  type queue[A >: Null] <: {
    val tryDeq: Reagent[Unit,Option[A]]
    val enq: Reagent[A,Unit]
    val deq: Reagent[Unit,A]
  }
  protected def newQueue[A >: Null](): queue[A]

  @test def `should tryDeq as None when empty` {
    var q = newQueue[java.lang.Integer]()
    q.tryDeq ! () must beNone
  }
  @test def `should tryDeq as Some _ when full` {
    var q = newQueue[java.lang.Integer]()
    q.enq ! 1;
    q.tryDeq ! () must beSome
  }
  @test def `should tryDeq as None after tryDequeuing` {
    var q = newQueue[java.lang.Integer]()
    q.enq!1;
    q.tryDeq!();
    q.tryDeq !() must beNone
  }
  @test def `should tryDeq in order` {
    var q = newQueue[java.lang.Integer]()
    q.enq! 1;
    q.enq! 2;
    (q.tryDeq! (), q.tryDeq! ()) must beEqualTo(Some(1), Some(2))
  }
  @test def `should enqueue from multiple threads in locally-ordered way` {
    val max = 100000

    var s = newQueue[java.lang.Integer]()
    TestUtil.spawnAndJoin (List(
      () => for (i <- 1 to max) s.enq ! i,
      () => for (i <- max+1 to 2*max) s.enq ! i))

    val outcome = new Traversable[Int] {
      def foreach[U](f: Int => U) {
	s.tryDeq ! () match {
	  case None => ()
	  case Some(i) => f(i.intValue); foreach(f)
	}
      }
    }

    val left  = for (i <- outcome if i <= max) yield i
    val right = for (i <- outcome if i >  max) yield i
    val comp  = left.toSeq ++ right.toSeq
    val eqs   = for ((i,j) <- comp zip (1 to 2*max)) yield i == j

    (true /: eqs)(_ && _) must beTrue
  }
}

object QueueSpec extends Spec {
/*
  class `an MSQueue` extends QueueTests {
    type queue[A >: Null] = MSQueue[A]
    protected def newQueue[A >: Null]() = new MSQueue[A]()
  }
  class `a LaggingQueue` extends QueueTests {
    type queue[A >: Null] = LaggingQueue[A]
    protected def newQueue[A >: Null]() = new LaggingQueue[A]()
  }
*/
}
