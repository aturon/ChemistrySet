package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection.mutable._

private object Util {
  def undef[A]: A = throw new Exception()
}

sealed private class Transaction {
  private case class CASLog(ov: Any, nv: Any)
  private val redoLog = HashMap.empty[AtomicReference[Any], CASLog]

  def read[A](r: Ref[A]): A = redoLog.getOrElseUpdate(r.data, {
    val cur = r.data.get()
    CASLog(cur, cur)
  }).nv.asInstanceOf[A]

  def cas[A](r: Ref[A], ov: A, nv: A) = Util.undef

  def attempt: Boolean = Util.undef
}

class Reagent[A,B] private (private val choices: List[List[Atom]]) {
  private abstract class Outcome
  private case object ShouldBlock extends Outcome
  private case object ShouldRetry extends Outcome
  private case class Success(a: Any) extends Outcome

  private def attempt(m: List[Atom], a: Any): Outcome = {
    val istack = ArrayStack(m)   // note: these should be re-used at
    val dstack = ArrayStack(a)   //       least across choices/retries
    var trans  = new Transaction

    while (!istack.isEmpty) istack.top match {
      case List() => 
	istack.pop
      case atom :: rest => {
	istack.update(0,rest)	// consume the instruction
	atom match {		// interpret the instruction
	  case Map(f) => {
	    val data = dstack.pop
	    if (f.isDefinedAt(data)) 
	      dstack.push(f(data))
	    else return ShouldBlock
	  }
	  case Thunk(f) => 
	    istack.push(f().choices.head)
	  case Split => {
	    val (x,y) = dstack.pop
	    dstack.push(y)
	    dstack.push(x)
	  }
	  case Swap => {
	    val x = dstack.pop
	    val y = dstack.pop
	    dstack.push(x)
	    dstack.push(y)
	  }
	  case Merge => {
	    val x = dstack.pop
	    val y = dstack.pop
	    dstack.push((x,y))
	  }
	  case Read(r) => {
	    dstack.update(0, trans.read(r))
	  }
	  case CAS(r) => {
	    val (ov,nv) = dstack.pop
	    dstack.push(trans.cas(r, ov, nv))
	  }
	}
      }
    }

    // assert(dstack.size eq 1)

    if (trans.attempt) Success(dstack.pop) else ShouldRetry
  }

  def !(a: A): B = {
    // initial cut: nonblocking version
    @tailrec def tryChoices(cs: List[List[Atom]]): B = cs match {
      case c :: cs1 => attempt(c,a) match {
	case Success(b) => b.asInstanceOf[B]
	case _ => tryChoices(cs1)
      }
      case List() => {
	// backoff()
	tryChoices(choices) // retry all choices
      }
    }
    tryChoices(choices)
  }

  def &>[C](next: Reagent[B,C]): Reagent[A,C] = new Reagent(for {
    choice <- this.choices
    nextChoice <- next.choices
  } yield choice ++ nextChoice)
  def <+>(that: Reagent[A,B]): Reagent[A,B] = new Reagent(this.choices ++ that.choices)

  def onLeft[C]: Reagent[(A,C), (B,C)] = 
    new Reagent(for (c <- choices) yield Split +: c :+ Merge)
  def onRight[C]: Reagent[(C,A), (C,B)] = 
    new Reagent(for (c <- choices) yield Split +: Swap +: c :+ Swap :+ Merge)
}
private object Reagent {
  def fromAtom[A,B](a: Atom): Reagent[A,B] = new Reagent(List(List(a)))
}

private sealed abstract class Atom
private case class Map[A,B](f: PartialFunction[A,B]) extends Atom
private case class Thunk[A,B](f: () => Reagent[A,B])  extends Atom
private case object Split extends Atom
private case object Swap extends Atom
private case object Merge extends Atom
private case class Read[A](r: Ref[A]) extends Atom
private case class CAS[A](r: Ref[A]) extends Atom

object Lift {
  def apply[A,B](f: PartialFunction[A,B]) = Reagent.fromAtom(new Map(f))
}

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
  private[chemistry] val data = new AtomicReference[Any](init)

  def read: Reagent[Unit, A]     = Reagent.fromAtom(new Read(this))
  def cas:  Reagent[(A,A), Unit] = Reagent.fromAtom(new CAS(this))

  // interface using separate reads/writes
/*  
  def rd: Reagent[Unit,A] = new Atom[Unit,A] {}
  def wr: Reagent[A,Unit] = new Atom[A,Unit] {}
*/

  // interface using atomic update

/*
  def upd(f: PartialFunction[A,A]): Reagent[Unit,Unit] = 
    new Atom[Unit,Unit] {}
  def updO[B](f: PartialFunction[A, (A,B)]): Reagent[Unit,B] = 
    new Atom[Unit,B] {}
  def updI[B](f: PartialFunction[(A,B), A]): Reagent[B,Unit] = 
    new Atom[B,Unit] {}
  def updIO[B,C](f: PartialFunction[(A,B), (A,C)]): Reagent[B,C] = 
    new Atom[B,C] {}
*/
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
