package chemistry

import java.util.concurrent.atomic._
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

private case object ShouldBlock
private case object ShouldRetry

private sealed abstract class Atom[-A,+B] extends Function2[A,Transaction,Any]
private object Atom {
  def bind[A](a: Any, f: A => Any): Any = {
    a match {
      case ShouldBlock => ShouldBlock
      case ShouldRetry => ShouldRetry
      case _ => f(a.asInstanceOf[A])
    }
  }

  sealed case class OnLeft[A,B,C](a: Atom[A,B]) extends Atom[(A,C),(B,C)] {
    @inline final def apply(data: (A,C), trans: Transaction): Any = 
      Atom.bind(a(data._1, trans), ((_:B), data._2))
  }

  sealed case class OnRight[A,B,C](a: Atom[A,B]) extends Atom[(C,A),(C,B)] {
    @inline final def apply(data: (C,A), trans: Transaction): Any = 
      Atom.bind(a(data._2, trans), (data._1, (_:B)))
  }

  sealed case class ApplyPfn[A,B](f: PartialFunction[A,B]) extends Atom[A,B] {
    @inline final def apply(data: A, trans: Transaction): Any = 
      if (f.isDefinedAt(data)) f(data) 
      else ShouldBlock
  }

  sealed case class Apply[A,B](f: Function[A,B]) extends Atom[A,B] {
    @inline final def apply(data: A, trans: Transaction): B = 
      f(data)
  }

  sealed case class Thunk[A,B](f: () => Reagent[A,B]) extends Atom[A,B] {
    @inline final def apply(data: A, trans: Transaction): Any = 
      f().choices.head(data, trans)
  }

  sealed case class Const[A,B](a: A) extends Atom[B,A] {
    @inline final def apply(data: B, trans: Transaction): A = a
  }

  case object Retry extends Atom[Any,Nothing] {
    @inline final def apply(data: Any, trans: Transaction): Any = ShouldRetry
  }

  sealed case class Read[A](r: AtomicReference[A]) extends Atom[Unit, A] {
    @inline final def apply(data: Unit, trans: Transaction): A = 
      r.get()
  }

  sealed case class CAS[A](r: AtomicReference[A]) extends Atom[(A,A), Unit] {
    @inline final def apply(data: (A,A), trans: Transaction): Unit = 
      r.compareAndSet(data._1, data._2)
  }

  sealed case class KnownCAS[A](r: AtomicReference[A], ov: A, nv: A) 
	       extends Atom[Unit, Unit] {
    @inline final def apply(data: Unit, trans: Transaction): Unit = 
      r.compareAndSet(ov, nv)
  }

  sealed case class Upd[A,B,C](r: AtomicReference[A], f: (A,B) => (A,C)) 
	       extends Atom[B,C] {
    @inline final def apply(data: B, trans: Transaction): C = {
      val ov = r.get()
      val (nv, ret) = f(ov, data)
      r.compareAndSet(ov, nv)
  //    trans.log += CASLog(r, ov, nv)
      ret
    }
  }

  sealed case class Compose[A,B,C](a: Atom[A,B], b: Atom[B,C]) 
	       extends Atom[A,C] {
    @inline def apply(data: A, trans: Transaction): Any = 
      Atom.bind(a(data, trans), b((_:B), trans))
  }
}

sealed class Reagent[-A,+B] private[chemistry] (
  private[chemistry] val choices: List[Atom[A,B]]) {
  @inline final def !(x: A): B = {
    while (true) {
      var cs = choices;
      while (!cs.isEmpty) {
//	val trans = new Transaction()
	val res = cs.head(x, null)
    	res match {
    	  case ShouldBlock => {}
    	  case _ => return res.asInstanceOf[B]
    	}
	cs = cs.tail
      }

      // backoff
    }
    Util.undef
  }

  @inline final def &>[C](next: Reagent[B,C]): Reagent[A,C] = 
    new Reagent(for {
      choice <- this.choices
      nextChoice <- next.choices
    } yield Atom.Compose(choice, nextChoice))
  @inline final def <+>[C <: A, D >: B](
    that: Reagent[C,D]): Reagent[C,D] = 
    new Reagent(this.choices ++ that.choices)
  @inline final def onLeft[C]: Reagent[(A,C), (B,C)] = 
    new Reagent(for (a <- choices) yield Atom.OnLeft[A,B,C](a))
  @inline final def onRight[C]: Reagent[(C,A), (C,B)] = 
    new Reagent(for (a <- choices) yield Atom.OnRight[A,B,C](a))

  @inline final def !?(x: A): Option[B] = 
    ((this &> Lift(Some(_): Option[B])) <+> Const(None)) ! x

  @inline final def <&[C](that: Reagent[C,A]): Reagent[C,B] = 
    that &> this
}
object Reagent {
  private[chemistry] def fromAtom[A,B](a: Atom[A,B]): Reagent[A,B] = 
    new Reagent(List(a))

  implicit def function2Reagent[A,B](f: Function[A,B]): Reagent[A,B] =
    Lift(f)
  implicit def partialFunction2Reagent[A,B](f: PartialFunction[A,B]): Reagent[A,B]
    = Lift(f)
}

object Lift {
  def apply[A,B](f: PartialFunction[A,B]): Reagent[A,B] = 
    Reagent.fromAtom(Atom.ApplyPfn(f))
  def apply[A,B](f: Function[A,B]): Reagent[A,B] = 
    Reagent.fromAtom(Atom.Apply(f))  
}

object Loop {
  def apply[A,B](f: => Reagent[A,B]): Reagent[A,B] = 
    Reagent.fromAtom(Atom.Thunk(() => f))
}

object Const {
  def apply[A,B](a: A): Reagent[B,A] = Reagent.fromAtom(Atom.Const(a))
}

object Retry extends Reagent[Any, Nothing](List(Atom.Retry))

/*
private class Endpoint[A,B] extends Atom[A,B] {
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

class Ref[A](init: A) {
  val data = new AtomicReference[A](init)

  def read: Reagent[Unit, A] = Reagent.fromAtom(Atom.Read(data))
  def cas: Reagent[(A,A), Unit] = Reagent.fromAtom(Atom.CAS(data))
  def cas(ov: A, nv: A): Reagent[Unit, Unit] = 
    Reagent.fromAtom(Atom.KnownCAS(data, ov, nv))
  def upd[B,C](f: (A,B) => (A,C)): Reagent[B, C] = 
    Reagent.fromAtom(Atom.Upd(data, f))

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
  @inline final def unapply[A](r: Ref[A]): Option[A] = Some(r.data.get()) 
}

sealed class TreiberStack[A] {
  private val head = new Ref[List[A]](List())
  // val pushRA: Reagent[A, Unit] = head upd { 
  //   (xs, x:A) => (x::xs, ()) 
  // }
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
  private case class Node(data: A, next: Ref[Node] = new Ref(null))
  private val head = new Ref(Node(null))
  private val tail = new Ref(head.read!())

  val enq: Reagent[A, Unit] = Loop {
    tail.read ! () match {
      case Node(_, ref@Ref(null)) =>
	ref.cas <& ((a: A) => (null, Node(a)))
      case ov@Node(_, Ref(nv)) => 
  	tail.cas(ov,nv) !? (); Retry
    }
  }

  val deq: Reagent[Unit, Option[A]] = head upd {
    case (Node(_, Ref(n@Node(x, _))), ()) => (n, Some(x))
    case (emp, ()) => (emp, None)
  }
}

/* 

object Examples {
  def cons[A](p:(List[A],A)) = p._2::p._1

  class TreiberStack[A] {
    private val head = new Ref[List[A]](List())
    val push = head updI {
      case (xs,x) => x::xs
    }
    val pop  = head updO {
      case x::xs => (xs, Some(x))
      case emp   => (emp,  None)
    }
  }
  class TreiberStack2[A] {
    private val head = new Ref[List[A]](List())
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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



// object Bench extends App {
//   import java.util.Date

//   val d = new Date()
//   val trials = 10
//   val iters = 100000

//   def getTime = (new Date()).getTime
//   def withTime(msg: String)(thunk: => Unit) {
//     for (i <- 1 to 3) thunk // warm up
//     print(msg)
//     var sum: Long = 0
//     for (i <- 1 to trials) {
//       System.gc()
//       val t = getTime
//       thunk
//       val t2 = getTime
//       print(".")
//       sum += (t2 - t)
//     } 
//     print("\n  ")
//     print((trials * iters) / (1000 * sum))
//     println(" iters/us")
//   }

//   withTime("ArrayQueue") {
//     val s = new scala.collection.mutable.Queue[java.util.Date]()
//     for (i <- 1 to iters) {
//       s.enqueue(d)
//       //s.pop()
//     }
//   }

//   withTime("java.util.Queue") {
//     val s = new java.util.LinkedList[java.util.Date]()
//     for (i <- 1 to iters) {
//       s.offer(d)
//     }
//   }

//   withTime("Direct") {
//     val s = new Queue[java.util.Date]()
//     for (i <- 1 to iters) {
//       s.enqueue(d)
//     }
//   }

//   withTime("Reagent-based") {
//     val s = new MSQueue[java.util.Date]()
//     for (i <- 1 to iters) {
//       s.enq ! d
//     }
//   }
// }
