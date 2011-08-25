// A concurrent, unordered bag, used to represent channels

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

trait DeletionFlag {
  def isDeleted: Boolean
}

/*
final class Pool[A <: DeletionFlag] {
  final case class Node(data: A, next: Cursor) 
  val cursor = new Cursor(null)

  final class Cursor private[Pool](node: Node) {
    private[Pool] val ref = Ref(node)
    @tailrec def get: Node = ref.read ! () match {
      case null => null
      case n@Node(data, next) =>
	if (data.isDeleted) { 
//	  ref.cas(n, next.ref.read ! ()) !? ()
	  ref.data.lazySet(next.ref.data.get)
	  get
	} else n
    }
  }

  private val putRA: Reagent[A,Unit] = cursor.ref.updIn {
    (xs,x) => Node(x, new Cursor(xs))
  }

  def put(a: A) {
    putRA ! a
  }
}
*/

private final class PaddedAtomicReference[A](init:A) 
	      extends AtomicReference[A](init) {
  var q0: Long = 0
  var q1: Long = 0
  var q2: Long = 0
  var q3: Long = 0
  var q4: Long = 0
  var q5: Long = 0
  var q6: Long = 0
  var q7: Long = 0
  var q8: Long = 0
  var q9: Long = 0
  var qa: Long = 0
  var qb: Long = 0
  var qc: Long = 0
  var qd: Long = 0
  var qe: Long = 0
}

final class Pool[A <: DeletionFlag] {
  abstract class Node {
    def data: A
    def next: Cursor
  }
  object Node {
    @inline def unapply(n: Node): Option[(A, Cursor)] = Some((n.data, n.next))
  }
  private final case class InnerNode(data: A, next: Cursor) extends Node
  private final case class LinkNode(next: Cursor) extends Node {
    def data = throw Util.Impossible 
  }

  private val cursors = new Array[Cursor](Chemistry.procs)
  cursors(Chemistry.procs-1) = new Cursor(null)
  for (i <- Chemistry.procs-1 to 1 by -1) {
    cursors(i-1) = new Cursor(LinkNode(cursors(i)))
  }

  private def myStart = (Thread.currentThread.getId % Chemistry.procs).toInt

  val cursor = cursors(0)
//  def cursor = cursors(myStart)

  final class Cursor private[Pool](node: Node) {
    private[Pool] val ref = new PaddedAtomicReference(node)
    @tailrec def get: Node = ref.get match {
      case null => null
      case LinkNode(next) => next.get
      case n@Node(data, next) =>
	if (data.isDeleted) { 
	  ref.lazySet(next.ref.get)
	  get
	} else n
    }
  }

  def put(a: A): Unit = {
    var i = myStart //0
    while (true) {
      val oldHead = cursors(i).ref.get
      if (cursors(i).ref.compareAndSet(oldHead, InnerNode(a, new Cursor(oldHead))))
	return
      else i = (i+1) % Chemistry.procs
    }
  }
}

