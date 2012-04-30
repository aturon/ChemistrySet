import scala.annotation.tailrec
import System.out._
import java.util.concurrent.atomic._
import scala.concurrent.ops._
import org.specs2.mutable._
import chemistry._

trait PoolTests extends Specification {
  private abstract class DelStatus
  private final case object Deleted extends DelStatus
  private final case object Active extends DelStatus
  private case class TestItem(i: Int) extends DeletionFlag {
    val deletedFlag = new AtomicReference[DelStatus](Active)
    def isDeleted = deletedFlag.get == Deleted
    def delete = deletedFlag.compareAndSet(Active, Deleted)
  }

  protected def newPool[A <: DeletionFlag](): Pool[A]
  protected def title: String

  private def np = newPool[TestItem]()

  title should {
    "return a null cursor when empty" in {
      np.cursor === null      
    }
    "return a nonnull cursor when nonempty" in {
      val p = np
      p.put(TestItem(2))
      p.cursor !== null
    }
    "when a singleton, contain the single item" in {
      val p = np
      p.put(TestItem(2))
      p.cursor.data.i === 2
    }
    "return a null cursor after removing all items" in {
      val p = np
      val ti = TestItem(2)
      p.put(ti)
      ti.delete
      p.cursor === null
    }
    "iterate through all inserted items (in any order)" in {
      val p = np
      p.put(TestItem(1))
      p.put(TestItem(2))
      val t1 = p.cursor
      val t2 = t1.next
      List(t1.data.i, t2.data.i) must contain(1,2).only
    }
  }
}

object CircularPoolSpec extends PoolTests {
  def title = "a CircularPool"
  def newPool[A <: DeletionFlag]() = new CircularPool[A]
}
