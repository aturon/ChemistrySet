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

sealed private class Transaction {}

private abstract class WaiterStatus
private case object Catalyst extends WaiterStatus
private case object Waiting  extends WaiterStatus
private case object Finished extends WaiterStatus

sealed private abstract class AbsWaiter
sealed private case class Waiter[A](
  r: Reagent[A], var answer: AnyRef,
  status: Ref[WaiterStatus], 
  thread: Thread
) extends AbsWaiter

private abstract class Failed
private case object ShouldBlock extends Failed
private case object ShouldRetry extends Failed

sealed abstract class Reagent[+A] {
  // "doFn" in the CML implementation
  private[chemistry] def tryReact(trans: Transaction): Any 
  // "blockFn" in the CML implementation
  private[chemistry] def logWait(w: AbsWaiter): Unit

  final def ! : A = {
    def slowPath: A = {
      val status = Ref[WaiterStatus](Waiting)
      val recheck: Reagent[A] = for {
	_ <- status.cas(Waiting, Finished)
	r <- this
      } yield r
      val waiter = Waiter(this, null, status, Thread.currentThread())
      @tailrec def recheckThenBlock: A = status.get() match {
	case Finished => waiter.answer.asInstanceOf[A]
	case _ => recheck.tryReact(null) match {
	  case ShouldRetry => recheckThenBlock // should backoff
	  case ShouldBlock => LockSupport.park(waiter); recheckThenBlock
	  case result => result.asInstanceOf[A] 
	}
      }
      logWait(waiter)
      recheckThenBlock
    }

    // first try "fast path": react without creating/enqueuing a waiter
    tryReact(null) match {
      case (_ : Failed) => slowPath
      case result => result.asInstanceOf[A] 
    }
  }

  @inline final def !? : Option[A] = {
    tryReact(null) match {
      case ShouldRetry => None	// should we actually retry here?  if
				// we do, more informative: a failed
				// attempt entails a linearization
				// where no match was possible.  but
				// could loop!
      case ShouldBlock => None
      case result => Some(result.asInstanceOf[A])
    }
  }

  @inline final def flatMap[B](k: A => Reagent[B]): Reagent[B] = RBind(this, k)
  @inline final def map[B](f: A => B): Reagent[B] = RBind(this, (x: A) => RUnit(f(x)))

  // @inline final def <+>[C <: A, D >: B](
  //   that: Reagent[C,D]): Reagent[C,D] = 
  //   new Reagent(this.choices ++ that.choices)

}

private sealed case class RBind[A,B](c: Reagent[A], k: A => Reagent[B]) extends Reagent[B] {
  @inline final def tryReact(trans: Transaction): Any = 
    c.tryReact(trans) match {
      case ShouldBlock => ShouldBlock
      case ShouldRetry => ShouldRetry
      case res => k(res.asInstanceOf[A]).tryReact(trans)
    }

  @inline final def logWait(w: AbsWaiter) {
//      a.logWait(w)
//      b.logWait(w)
  }
}

private sealed case class RUnit[A](pure: A) extends Reagent[A] {
  @inline final def tryReact(trans: Transaction): Any = pure
  @inline final def logWait(w: AbsWaiter) {}
}

object Retry extends Reagent[Nothing] {
  @inline final def tryReact(trans: Transaction): Any = ShouldRetry
  @inline final def logWait(w: AbsWaiter) {}
}

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

  case object read extends Reagent[A] {
    @inline final def tryReact(trans: Transaction): A = get()
    @inline final def logWait(w: AbsWaiter) {}
  }

  sealed case class cas(expect: A, update: A) extends Reagent[Unit] {
    @inline final def tryReact(trans: Transaction): Unit = 
      compareAndSet(expect, update)
    @inline final def logWait(w: AbsWaiter) {}
  }
  def mkcas(ov:A,nv:A) = cas(ov,nv)  // deal with weird compiler bug

  sealed case class upd[B](f: A => (A,B)) extends Reagent[B] {
    @inline final def tryReact(trans: Transaction): B = {
      val ov = get()
      val (nv, ret) = f(ov)
      compareAndSet(ov, nv)
      ret
    }
    @inline final def logWait(w: AbsWaiter) {}
  }
}
object Ref {
  final def apply[A](init: A): Ref[A] = new Ref(init)
  final def unapply[A](r: Ref[A]): Option[A] = Some(r.get()) 
}

sealed class TreiberStack[A] {
  private val head = Ref[List[A]](List())

  final def push(x:A): Reagent[Unit] = head upd { 
    xs => (x::xs, ())
  }
  final val pop: Reagent[Option[A]] = head upd {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }
}

sealed class MSQueue[A >: Null] {
  private case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
  private val tail = Ref(head.read !)

  final def enq(x:A): Reagent[Unit] = tail.read ! match {
    case    Node(_, r@Ref(null)) => r.mkcas(null, Node(x))
    case ov@Node(_, Ref(nv))     => tail.cas(ov,nv) !?; Retry
  }
  final val deq: Reagent[Option[A]] = head upd {
    case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
    case emp => (emp, None)
  }
}

/*

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
      case PNode(Ref(Tail)) => 
	(c, Tail)
      case PNode(r@Ref(n@INode(Ref(m), _, Ref(true)))) => 
	r.cas(n, m) !?; walk(c)
      case PNode(Ref(n@INode(_, data, Ref(false)))) =>	
	if (key == data.hashCode())     Found(c, n) 
	else if (key < data.hashCode()) NotFound(c, n)
	else walk(n)
    }
    walk(list.read !)
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

//  def contains: Reagent[A, Boolean]
}
*/

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
  val trials = 10
  val iters = 1000000

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

/*
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

*/

    withTime("Direct") {
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d)
      }
    }

    withTime("Reagent-based") {		// 36101 baseline on Dell
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to iters) {
	s.push(d) !
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

    withTime("Direct") {				// 7,142/ms
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enqueue(d)
      }
    }

    withTime("Reagent-based") {				// 4,334/ms
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to iters) {
    	s.enq(d) ! 
      }
    }
  }

  doQueues
  doStacks
}


