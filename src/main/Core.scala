package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

private abstract class KCAS[A] {
  def attempt(): Option[A]
}

class Reagent[A,B] private (val choices: List[Molecule[A,B]]) {
  def !(a: A): B = {
    // initial cut: nonblocking version
    def tryChoices(cs: List[Molecule[A,B]): B = cs match {
      case c :: cs1 => c.poll(a) match {
	case Some(b, kcas) => if (kcas()) b else tryChoices(cs1)
	case None => tryChoices(cs1)
      }
      case List() => {
	backoff()
	tryChoices(choices) // retry all choices
      }
    }
    tryChoices(choices)
  }

  def &>[C](r: Reagent[B,C]): Reagent[A,C]
  def <+>(r: Reagent[A,B]): Reagent[A,B]
}
object Reagent {
/*
  implicit def partialFunctionToReagent[A,B](f: PartialFunction[A,B]):
    Reagent[A,B] = new Lift(f)
  implicit def functionToReagent[A,B](f: A => B):
    Reagent[A,B] = new Lift(new PartialFunction[A,B] {
      def apply(a: A) = f(a)
      def isDefinedAt(a: A) = true
    })
  implicit def reagentToCatalyst(r: Reagent[Unit,Unit]): { def !! } = 
    new { def !! { throw new Exception("Not implemented") }}
  def catalyze(r: Reagent[Unit,Unit]) = r !!
*/
}

private abstract class Molecule[A,B] {
  def poll(a: A): Option[(B, KCAS)]
}

private class Bonded[A,B,C](val m1: Molecule[A,B], val m2: Molecule[B,C]) extends Molecule[A,C] {
  def poll(a: A): Option[(C, KCAS)] = {
    m1.poll(a) match {
      case Some(b, kcas1) => m2.poll(b) match {
	case Some(c, kcas2) => Some(c, kcas1 & kcas2)
	case None => None
      }
    }
  }
}

private abstract class Atom[A,B] extends Molecule[A,B] {
// def isDualTo(a: Atom): Boolean
}

private class Lift[A,B](f: PartialFunction[A,B]) extends Atom[A,B]

/*
private class Fst[A,B,C](a: Atom[A,B]) extends Atom[(A,C), (B,C)]
private class Snd[A,B,C](a: Atom[A,B]) extends Atom[(C,A), (C,B)]
*/

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
  def read: Reagent[Unit, A]
  def cas:  Reagent[(A,A), Unit]

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

/* */

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

/* */
