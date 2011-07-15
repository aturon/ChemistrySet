package chemistry

import java.util.concurrent.atomic._
import java.util.concurrent.locks._
import scala.annotation.tailrec
import scala.collection.mutable._

private object Util {
  def undef[A]: A = throw new Exception()
}

sealed private abstract class LogEntry
private case class CASLog[A](r: AtomicReference[A], ov: A, nv: A) 
	     extends LogEntry

sealed private class Transaction {
//   private case class CASLog(ov: Any, nv: Any)
//   private val redoLog = HashMap.empty[AtomicReference[Any], CASLog]

//   def read[A](r: Ref[A]): A = redoLog.getOrElseUpdate(r.data, {
//     val cur = r.data.get()
//     CASLog(cur, cur)
//   }).nv.asInstanceOf[A]

//   def cas[A](r: Ref[A], ov: A, nv: A) = Util.undef

//   def attempt: Boolean = Util.undef

  var shouldBlock = false
//  var log = ArrayBuffer[LogEntry]()
  //val log = ArrayStack[Unit](())
  var log: List[LogEntry] = List()
}

private abstract class WaiterStatus
private case object Catalyst extends WaiterStatus
private case object Waiting  extends WaiterStatus
private case object Finished extends WaiterStatus

sealed private abstract class AbsWaiter
sealed private case class Waiter[A,B](
  r: Reagent[A, B], arg: A, var answer: AnyRef,
  status: Ref[WaiterStatus], 
  thread: Thread
) extends AbsWaiter

private abstract class Failed
private case object ShouldBlock extends Failed
private case object ShouldRetry extends Failed

sealed abstract class Reagent[-A,+B] {
  // "doFn" in the CML implementation
  protected def tryReact(data: A, trans: Transaction): Any 
  // "blockFn" in the CML implementation
  protected def logWait(w: AbsWaiter): Unit

  final def !(a: A): B = {
    def slowPath: B = {
      val status = Ref[WaiterStatus](Waiting)
      val recheck: Reagent[Unit, B] = 
	Const(Waiting, Finished) &> status.cas &> (Const(a) &> this)
      val waiter = Waiter(this, a, null, status, Thread.currentThread())
      @tailrec def recheckThenBlock: B = status.get() match {
	case Finished => waiter.answer.asInstanceOf[B]
	case _ => recheck.tryReact((), null) match {
	  case ShouldRetry => recheckThenBlock // should backoff
	  case ShouldBlock => LockSupport.park(waiter); recheckThenBlock
	  case result => result.asInstanceOf[B] 
	}
      }
      logWait(waiter)
      recheckThenBlock
    }

    // first try "fast path": react without creating/enqueuing a waiter
    tryReact(a, null) match {
      case (_ : Failed) => slowPath
      case result => result.asInstanceOf[B] 
    }
  }

  @inline final def !?(a: A): Option[B] = {
    tryReact(a, null) match {
      case ShouldRetry => None	// should we actually retry here?  if
				// we do, more informative: a failed
				// attempt entails a linearization
				// where no match was possible.  but
				// could loop!
      case ShouldBlock => None
      case result => Some(result.asInstanceOf[B])
    }
  }
  // @inline final def !?(x: A): Option[B] = 
  //   ((this &> Lift(Some(_): Option[B])) <+> Const(None)) ! x

  @inline final def &>[C](next: Reagent[B,C]): Reagent[A,C] = 
    Reagent.Compose(this, next)
  // @inline final def <+>[C <: A, D >: B](
  //   that: Reagent[C,D]): Reagent[C,D] = 
  //   new Reagent(this.choices ++ that.choices)
  @inline final def onLeft[C]: Reagent[(A,C), (B,C)] = 
    Reagent.OnLeft[A,B,C](this)
  @inline final def onRight[C]: Reagent[(C,A), (C,B)] = 
    Reagent.OnRight[A,B,C](this)

  @inline final def <&[C](that: Reagent[C,A]): Reagent[C,B] = 
    that &> this
}
object Reagent {
  implicit def function2Reagent[A,B](f: Function[A,B]): Reagent[A,B] =
    Lift(f)
  implicit def partialFunction2Reagent[A,B](f: PartialFunction[A,B]): Reagent[A,B]
    = Lift(f)

  private def bind[A](a: Any, f: A => Any): Any = {
    a match {
      case ShouldBlock => ShouldBlock
      case ShouldRetry => ShouldRetry
      case _ => f(a.asInstanceOf[A])
    }
  }

  sealed case class OnLeft[A,B,C](a: Reagent[A,B]) extends Reagent[(A,C),(B,C)] {
    @inline final def tryReact(data: (A,C), trans: Transaction): Any = 
      bind(a.tryReact(data._1, trans), ((_:B), data._2))
    @inline final def logWait(w: AbsWaiter) = a.logWait(w)
  }

  sealed case class OnRight[A,B,C](a: Reagent[A,B]) extends Reagent[(C,A),(C,B)] {
    @inline final def tryReact(data: (C,A), trans: Transaction): Any = 
      bind(a.tryReact(data._2, trans), (data._1, (_:B)))
    @inline final def logWait(w: AbsWaiter) = a.logWait(w)
  }

  sealed case class ApplyPfn[A,B](f: PartialFunction[A,B]) extends Reagent[A,B] {
    @inline final def tryReact(data: A, trans: Transaction): Any = 
      if (f.isDefinedAt(data)) f(data) 
      else ShouldBlock
    @inline final def logWait(w: AbsWaiter) {}
  }

  sealed case class Apply[A,B](f: Function[A,B]) extends Reagent[A,B] {
    @inline final def tryReact(data: A, trans: Transaction): B = f(data)
    @inline final def logWait(w: AbsWaiter) {}
  }

  sealed case class Thunk[A,B](f: () => Reagent[A,B]) extends Reagent[A,B] {
    @inline final def tryReact(data: A, trans: Transaction): Any = 
      f().tryReact(data, trans)
    @inline final def logWait(w: AbsWaiter) {} // "nonblocking" treatment
  }

  sealed case class Const[A,B](a: A) extends Reagent[B,A] {
    @inline final def tryReact(data: B, trans: Transaction): A = a
    @inline final def logWait(w: AbsWaiter) {}
  }

  case object Retry extends Reagent[Any,Nothing] {
    @inline final def tryReact(data: Any, trans: Transaction): Any = ShouldRetry
    @inline final def logWait(w: AbsWaiter) {}
  }

  sealed case class Compose[A,B,C](a: Reagent[A,B], b: Reagent[B,C]) 
	       extends Reagent[A,C] {
    @inline def tryReact(data: A, trans: Transaction): Any = 
      bind(a.tryReact(data, trans), b.tryReact(_: B, trans))
    @inline final def logWait(w: AbsWaiter) {
      a.logWait(w)
      b.logWait(w)
    }
  }
}

object Lift {
  def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B] = Reagent.ApplyPfn(f)
  def apply[A,B](f: Function[A,B]): Reagent[A,B] = Reagent.Apply(f)
}

object Loop {
  def apply[A,B](f: => Reagent[A,B]): Reagent[A,B] = Reagent.Thunk(() => f)
}

object Const {
  def apply[A,B](a: A): Reagent[B,A] = Reagent.Const(a)
}

import Reagent.Retry

/*
private class Endpoint[A,B] extends Reagent[A,B] {
  var dual: Endpoint[B,A] = null
}
object SwapChan {
  def apply[A,B]: (Reagent[A,B], Reagent[B,A]) = {
    val c1 = new Endpoint[A,B]
    val c2 = new Endpoint[B,A]
    c1.dual = c2; c2.dual = c1
    (c1, c2)
  }
}
*/

class Ref[A](init: A) extends AtomicReference[A](init) {
//  private final var data = new AtomicReference[A](init)

//  private val waiters = new MSQueue[]()

  case object read extends Reagent[Unit, A] {
    @inline final def tryReact(data: Unit, trans: Transaction): A = get()
    @inline final def logWait(w: AbsWaiter) {
    }
  }

  case object cas extends Reagent[(A,A), Unit] {
    @inline final def tryReact(data: (A,A), trans: Transaction): Unit = 
      compareAndSet(data._1, data._2)
    @inline final def logWait(w: AbsWaiter) {}
  }

  // sealed case class KnownCAS[A,B](r: AtomicReference[A], ov: B => A, nv: B => A) 
  // 	       extends Reagent[B, Unit] {
  //   @inline final def tryReact(data: B, trans: Transaction): Unit = 
  //     r.compareAndSet(ov(data), nv(data))
  // }

  // sealed case class CASFrom[A](r: AtomicReference[A], ov: A) 
  // 	       extends Reagent[A, Unit] {
  //   @inline final def tryReact(data: A, trans: Transaction): Unit = 
  //     r.compareAndSet(ov, data)
  // }

  sealed case class upd[B,C](f: (A,B) => (A,C)) extends Reagent[B,C] {
    @inline final def tryReact(data: B, trans: Transaction): C = {
      val ov = get()
      val (nv, ret) = f(ov, data)
      compareAndSet(ov, nv)
      ret
    }
    @inline final def logWait(w: AbsWaiter) {}
  }

  //*** NOTE: upd can be defined in terms of read and cas as follows.
  //*** It's slower, though.

  // def upd[B,C](f: (A,B) => (A,C)): Reagent[B,C] = (
  //   Lift((x:B) => (x, ()))
  //   &> read.onRight[B]
  //   &> Lift((pair: (B,A)) => {
  //        val (arg, ov) = pair
  //        val (nv, ret) = f(ov, arg)
  //        ((ov, nv), ret)
  //      })
  //   &> cas.onLeft[C]
  //   &> Lift((pair: (Unit, C)) => pair._2)
  // )
}
object Ref {
  @inline final def apply[A](init: A): Ref[A] = Ref(init)
  @inline final def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}

sealed class TreiberStack[A] {
  private val head = Ref[List[A]](List())
  val pushRA: Reagent[A, Unit] = head upd { 
    (xs, x:A) => (x::xs, ())
  }
  val popRA:  Reagent[Unit,Option[A]] = head upd {
    case (x::xs, ()) => (xs,  Some(x))
    case (emp,   ()) => (emp, None)
  }

  def push(x: A) { pushRA ! x }
  def pop(): Option[A] = popRA ! ()
}

sealed class MSQueue[A >: Null] {
  private case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
  private val tail = Ref(head.read!())
  final val enq: Reagent[A, Unit] = Loop {
    tail.read ! () match {
      case Node(_, ref@Ref(null)) =>
	ref.cas <& ((a:A) => (null, Node(a)))
      case ov@Node(_, Ref(nv)) => 
    	tail.cas <& Reagent.Const((ov,nv)) !? (); Retry
    }
  }
  val deq: Reagent[Unit, Option[A]] = head upd {
    case (Node(_, Ref(n@Node(x, _))), ()) => (n, Some(x))
    case (emp, ()) => (emp, None)
  }
}

// invariant: if a node n was ever reachable from head, and a node m
// is reachable from n, then all keys >= m are reachable from n.
sealed class Set[A] {
  private abstract class Node 
  private abstract class PNode extends Node {
    def next: Ref[Node]
  } 
  private object PNode {
    def unapply(pn: PNode): Option[Ref[Node]] = Some(pn.next)
  }
  private case class Head(next: Ref[Node] = Ref(Tail)) extends PNode 
  private case class INode(
    next: Ref[Node], 
    data: A, 
    deleted: Ref[Boolean] = Ref(false)
  ) extends PNode 
  private case object Tail extends Node
  
  private val list: Ref[PNode] = Ref(Head())

  private abstract class FindResult
  private case class Found(pred: PNode, node: INode)   extends FindResult
  private case class NotFound(pred: PNode, succ: Node) extends FindResult

  private @inline final def find(key: Int): FindResult = {
    @tailrec def walk(c: PNode): (PNode, Node) = c match {
      case PNode(Ref(Tail)) => (c, Tail)
      case PNode(r@Ref(n@INode(Ref(m), _, Ref(true)))) => {
	r.cas <& Reagent.Const((n, m)) ! ()
	walk(c)
      }
      case PNode(Ref(n@INode(_, data, Ref(false)))) =>	
	if (key == data.hashCode())     Found(c, n) 
	else if (key < data.hashCode()) NotFound(c, n)
	else walk(n)
    }
    walk(list.read ! ())
  }

  def add: Reagent[A, Boolean] = Loop {
    find(item.hashCode()) match {
      case Found(_, _) => Const(false)	// blocking would be *here*
      case NotFound(pred, succ) => 
	(pred.next.cas <& Const(succ, INode(Ref(succ), item)) <;>
	 pred.deleted.cas <& Const(false, false) <;>
	 Const(true))
    }
  }

  def remove: Reagent[A, Boolean] = Loop {
    find(item.hashCode()) match {
      case NotFound(_, _) => Const(false)  // blocking would be *here*
      case Found(pred, node) => 
	(node.deleted.cas <& (false, true) <;>
	 Const(true) commitThen
	 pred.next.cas <& Const(node, node.next.get))
    }
  }

  def contains: Reagent[A, Boolean]
}

/* 

object Examples {
  def cons[A](p:(List[A],A)) = p._2::p._1

  class TreiberStack[A] {
    private val head = Ref[List[A]](List())
    val push = head updI {
      case (xs,x) => x::xs
    }
    val pop  = head updO {
      case x::xs => (xs, Some(x))
      case emp   => (emp,  None)
    }
  }
  class TreiberStack2[A] {
    private val head = Ref[List[A]](List())
    val push = guard (x => 
      val n = List(x)
      loop { head upd (xs => n.tail = xs; n) })
    val pop  = head updO {
      case x::xs => (xs, Some(x))
      case emp   => (emp,  None)
    }
  }
  class BlockingStack[A] {
    private val (sPush, rPush) = SwapChan[A, Unit]
    private val (sPop,  rPop)  = SwapChan[Unit, A]
    private val stack = new TreiberStack[A]

    rPush &> stack.push !! ;
    stack.pop &> { case Some(x) => x } &> rPop !! 
      
    val push = sPush
    val pop  = sPop
  }
  class BlockingElimStack[A] {
    private val (elimPush, elimPop) = SwapChan[A, Unit]
    private val stack = new TreiberStack[A]
    val push = elimPush <+> stack.push
    val pop  = elimPop  <+> (stack.pop &> { case Some(x) => x })
  }
  class EliminationBackoffStack[A] {
    val (push, pop) = {
      val (sPush, rPush) = SwapChan[A, Unit]
      val (sPop,  rPop)  = SwapChan[Unit, A]
      val stack = new TreiberStack[A]

      rPush &> stack.push !! ;
      stack.pop &> rPop !! ;
      rPush &> Some(_) &> rPop !!
      
      (sPush, sPop)
    }
  }
  class EliminationBackoffStack2[A] {
    private val (elimPush, elimPop) = SwapChan[A, Unit]
    private val stack = new TreiberStack[A]
    val push = elimPush <+> stack.push
    val pop  = (elimPop &> Some(_)) <+> stack.pop
  }
  class DCASQueue [A >: Null] {
    class Node(val a: A) {
      val next = Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (Ref(sentinel), Ref(sentinel))
    }
    val enq = guard (x: A) => for {
      oldTail @ Node(_, tailNext) <- tail.read
      n = new Node(x)
      tailNext.cas(null, n) & tail.cas(oldTail, n)
    }
    val deq = head updO {
      case Node(_, Ref(n @ Node(x, _))) => (n, Some(x))
      case emp => (emp, None)
    }              
  }
  class MSQueue[A >: Null] {
    class Node(val a: A) {
      val next = Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (Ref(sentinel), Ref(sentinel))
    }
    val enq = guard (x: A) => tail.read match {
      case n@Node(_, Ref(nt@Node(_, _))) => (tail.cas(n, nt) <+> always) >> enq(x)
      case   Node(_, r)                  => r.cas(null, new Node(x))
    }
    val deq = head updO {
      case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
      case emp                        => (emp, None)
    }
  }
  class MSQueue2[A >: Null] {
    class Node(val a: A) {
      val next = Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (Ref(sentinel), Ref(sentinel))
    }
    val enq = guard (x => 
      val node = new Node(x)
      loop { tail.read >>= {
	case n@Node(_, Ref(nt@Node(_, _))) => tail.cas(n, nt).attempt; retry
	case   Node(_, r)                  => r.cas(null, node)
      }})
    val deq = head updO {
      case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
      case emp                        => (emp, None)
    }
  }
}

*/



object Bench extends App {
  import java.util.Date

  val d = new Date()
  val trials = 100
  val iters = 100000

// System.currentTimeMillis

  def getTime = (new Date()).getTime
  def withTime(msg: String)(thunk: => Unit) {
    for (i <- 1 to 3) thunk // warm up
    print(msg)
    var sum: Long = 0
    for (i <- 1 to trials) {
      System.gc()
      val t = getTime
      thunk
      val t2 = getTime
      print(".")
      sum += (t2 - t)
    } 
    print("\n  ")
    print((trials * iters) / (1 * sum))  // 1000 * sum for us
    println(" iters/ms")
  }

  def doStacks {
    println("Stacks")

    withTime("ArrayStack") {
      val s = new scala.collection.mutable.ArrayStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }

    withTime("java.util.Stack") {
      val s = new java.util.Stack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }

    withTime("Direct") {
      val s = new Stack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }

    withTime("Reagent-based") {
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }
  }

  def doQueues {
    println("Queues")

    // withTime("ArrayQueue") {
    //   val s = new scala.collection.mutable.Queue[java.util.Date]()
    //   for (i <- 1 to iters) {
    // 	s.enqueue(d)
    //   }
    // }

    // withTime("java.util.Queue") {
    //   val s = new java.util.LinkedList[java.util.Date]()
    //   for (i <- 1 to iters) {
    // 	s.offer(d)
    //   }
    // }

    // withTime("Direct") {				// 7,142/ms
    //   val s = new HandQueue[java.util.Date]()
    //   for (i <- 1 to iters) {
    // 	s.enqueue(d)
    //   }
    // }

    withTime("Reagent-based") {				// 4,334/ms
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
	s.enq ! d
	//s.TestFn(d)
	//s.TestDef(d)
	//s.enqueue(d)
      }
    }
  }

  doQueues
}
