import scala.annotation.tailrec
import System.out._
import scala.concurrent.ops._
import com.codahale.simplespec.Spec
import com.codahale.simplespec.annotation.test
import chemistry._

trait StackTests {
  import org.specs2.matcher.MustMatchers._

  type stack[A >: Null] <: {
    val tryPop: Reagent[Unit,Option[A]]
    val push: Reagent[A,Unit]
    val pop: Reagent[Unit,A]
  }
  protected def newStack[A >: Null](): stack[A]

  @test def `should tryPop as None when empty` {
    var s = newStack[java.lang.Integer]()
    s.tryPop ! () must beNone
  }
  @test def `should tryPop as Some _ when full` {
    var s = newStack[java.lang.Integer]()
    s.push ! 1;
    s.tryPop ! () must beSome
  }
  @test def `should tryPop as None after emptying` {
    var s = newStack[java.lang.Integer]()
    s.push ! 1;
    s.tryPop ! ();
    s.tryPop ! () must beNone
  }
  @test def `should tryPop in reverse order` {
    var s = newStack[java.lang.Integer]()
    s.push ! 1;
    s.push ! 2;
    (s.tryPop!(), s.tryPop!()) must beEqualTo(Some(2), Some(1))
  }

  def stackToTrav[A >: Null](s: stack[A]) = new Traversable[A] {
    def foreach[U](f: A => U) {
      while (true) s.tryPop ! () match {
	case None => return ()
	case Some(a) => f(a)
      }
    }
  }

  def concTest: Boolean = {
    val max = 100000
    val s = newStack[java.lang.Integer]()

    TestUtil.spawnAndJoin (List(
      () => for (i <- 1 to max) s.push ! i,
      () => for (i <- max+1 to 2*max) s.push ! i))

    val outcome = stackToTrav(s).map(_.intValue)
    val left  = for (i <- outcome if i <= max) yield i
    val right = for (i <- outcome if i >  max) yield i
    val comp  = left.toSeq.reverse ++ right.toSeq.reverse
    val eqs   = for ((i,j) <- comp zip (1 to 2*max)) yield i == j

    (true /: eqs)(_ && _)
  }

  @test def `should push from multiple threads in locally-ordered way` {
    val testResults = for (_ <- 1 to 100) yield concTest
    (true /: testResults)(_ && _) must beTrue
  }
}

object StackSpec extends Spec {
  class `a TreiberStack` extends StackTests {    
    type stack[A >: Null] = TreiberStack[A]
    protected def newStack[A >: Null]() = new TreiberStack[A]()
  }
//  class `an EliminationStack` extends StackTests {    
//    type stack[A >: Null] = EliminationStack[A]
//    protected def newStack[A >: Null]() = new EliminationStack[A]()
//  }
}
