// A concurrent, unordered bag, used to represent channels

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._
import java.util.concurrent.locks._

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

private object ArrayPool {
  //val size = math.max(1,Chemistry.procs / 2)
  val size = 8
}
final class ArrayPool[A >: Null <: DeletionFlag] {
  import ArrayPool._

  private val arr = new Array[PaddedAtomicReference[A]](size)
  for (i <- (0 to size-1)) arr(i) = new PaddedAtomicReference[A](null)

  private def myStart = (Thread.currentThread.getId % size).toInt

  def tryPut(a: A): Boolean = {
//    val slot = myStart
    var slot = 0
    while (slot < size) {
      val cur = arr(slot).get
      if (cur != null && !cur.isDeleted) return false
      if (arr(slot).compareAndSet(cur, a)) return true
      slot += 1
    }
    false
  }

  def cursor = 0
  @tailrec def get(cursor: Int): A = {
    val cur = arr(cursor).get
    if (cur != null && !cur.isDeleted) 
      cur
    else if (cursor + 1 < size) 
      get(cursor+1)
    else 
      null
  }
  def next(cursor: Int): A = 
    if (cursor + 1 < size) get(cursor+1) else null
}

final class Pool[A <: DeletionFlag] {
  abstract class Node {
    def data: A
    def next: Cursor
  }
  object Node {
    @inline def unapply(n: Node): Option[(A, Cursor)] =
      if (n != null) Some((n.data, n.next)) else None
  }
  private final case class InnerNode(data: A, next: Cursor) extends Node
  private final case class LinkNode(next: Cursor) extends Node {
/*
    var q0: Long = 0
    var q1: Long = 0
    var q2: Long = 0
    var q3: Long = 0
    var q4: Long = 0
    var q5: Long = 0
    var q6: Long = 0
    var q7: Long = 0
*/
    def data = throw Util.Impossible 
  }

  private def size = math.max(1,Chemistry.procs/2)
  private val cursors = new Array[Cursor](size)
  cursors(size-1) = new Cursor(null)
  for (i <- size-1 to 1 by -1) {
    cursors(i-1) = new Cursor(LinkNode(cursors(i)))
  }
  cursors(size-1).ref.set(LinkNode(cursors(0))) // tie the knot

  private def myStart = (Thread.currentThread.getId % size).toInt

  private var lastPut: Int = 0

//  def cursor = cursors(0)
//  val cursor = cursors(0)
//  @inline def get = cursors(myStart).get
  def cursor = cursors(myStart)

  final class Cursor private[Pool](node: Node) {
    private[Pool] val ref = new AtomicReference(node)
    @tailrec def get: Node = ref.get match {
//      case null => return null
      case null => throw Util.Impossible
      case LinkNode(next) => 
//	next.get
	if (next eq cursors(myStart)) null
	else next.get
      case n@Node(data, next) =>
	if (data.isDeleted) {
	  var leap = next.ref.get
	  @tailrec def loop: Unit = leap match {
	    case null => ()
	    case LinkNode(_) => ()
	    case Node(data, next) =>
	      if (data.isDeleted) {
		leap = next.ref.get
		loop
	      } else ()		
	  }
	  loop
	  ref.set(leap)
	  get
	} else n
    }
  }

  private final def snoop(n: Int): Boolean = cursors(n).ref.get match {
    case null => throw Util.Impossible
    case LinkNode(next) => false
    case Node(data, _) => !data.isDeleted
  }
  final def snoop: Boolean = {
    var i: Int = 0
    val start = myStart
    while (i < size) {
      if (snoop((start + i) % size)) return true
      i += 1
    }
    false
  }

  def put(a: A) {
    val start = myStart
    var i = start //0    
    while (true) {
      val oldHead = cursors(i).ref.get
      if (cursors(i).ref.compareAndSet(oldHead, 
				       InnerNode(a, new Cursor(oldHead)))) {
//        lastPut = i
	return 
      } else i = (i+1) % size
    }
  }
}

private object BoundPool {
  val capacity = 32
  val full = math.max(0, math.min(capacity, Chemistry.procs / 2) - 1)
}
final class BoundPool[A <: DeletionFlag] {
  import BoundPool._

  case class Node(data: A) //, next: Int)

  private val lock = new ReentrantLock
  @volatile private var arena = new Array[PaddedAtomicReference[Node]](capacity)
  private val max = new AtomicInteger

  def put(a: A) {
    val n = Node(a)
    var index = hashIndex
    var fails = 0

    while (fails < 50) {
      val slot = arena(index)
      if (slot == null) {
	val slot = new PaddedAtomicReference[Node](null)
	lock.lock
	if (arena(index) == null)
	  arena(index) = slot
	lock.unlock
      } else {
	val cur = slot.get
	if (cur == null || cur.data.isDeleted) {
	  if (slot.compareAndSet(cur, n)) return
	}

	fails +=1 
	if (fails >= 2) {
	  val m = max.get
	  if (fails > 3 && m < full && max.compareAndSet(m, m+1))
	    index = m + 1
	  else if (index == 0)
	    index = m
	  else index -= 1
	}
      }
    }

    throw OfferFail
  }

  @inline private final def hashIndex: Int = {
    val id = Thread.currentThread().getId()
    var hash = ((id ^ (id >>> 32)).toInt ^ 0x811c9dc5) * 0x01000193

    val m = max.get()
    val nbits = (((0xfffffc00  >> m) & 4) | // Compute ceil(log2(m+1))
                 ((0x000001f8 >>> m) & 2) | // The constants hold
                 ((0xffff00f2 >>> m) & 1))  // a lookup table

    var index = hash & ((1 << nbits) - 1)
    while (index > m) {       // May retry on
      hash = (hash >>> nbits) | (hash << (33 - nbits))    // non-power-2 m
      index = hash & ((1 << nbits) - 1)
    }
    index
  }

  def get: Node = {
    var i = 0
    val m = max.get
    while (i <= m) {
      arena(i) match {
	case null => {}
	case slot => {
	  val n = slot.get
	  if (n != null && !n.data.isDeleted) return n
	}
      }
      i += 1
    }
    return null
  }
}
