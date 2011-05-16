package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

sealed abstract class Reagent[A,B] {
  def !(a: A): B = {
    throw new Exception("Not implemented")
  }

  def &>[C](r: Reagent[B,C]): Reagent[A,C] = {
    throw new Exception("Not implemented")
  }

  def <|>(r: Reagent[A,B]): Reagent[A,B] = {
    throw new Exception("Not implemented")
  }
}
object Reagent {
  implicit def partialFunctionToReagent[A,B](f: PartialFunction[A,B]):
    Reagent[A,B] = new Lift(f)
  implicit def reagentToCatalyst(r: Reagent[Unit,Unit]): { def !! } = 
    new { def !! { throw new Exception("Not implemented") }}
  def catalyze(r: Reagent[Unit,Unit]) = r !!
}

private abstract case class Choice[A,B](between: Molecule[A,B]*) 
		      extends Reagent[A,B]

private abstract class Molecule[A,B] extends Reagent[A,B]

private case class Bonded[A,B,C](m1: Molecule[A,B], m2: Molecule[B,C]) 
	     extends Molecule[A,C]

private abstract class Atom[A,B] extends Molecule[A,B] {
  // def isDualTo(a: Atom): Boolean
}

private class Lift[A,B](f: PartialFunction[A,B]) extends Atom[A,B]

private class Fst[A,B,C](a: Atom[A,B]) extends Atom[(A,C), (B,C)]
private class Snd[A,B,C](a: Atom[A,B]) extends Atom[(C,A), (C,B)]

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

class Ref[A](init: A) {
  def upd(f: PartialFunction[A,A]): Reagent[Unit,Unit] = 
    new Atom[Unit,Unit] {}
  def updO[B](f: PartialFunction[A, (A,B)]): Reagent[Unit,B] = 
    new Atom[Unit,B] {}
  def updI[B](f: PartialFunction[(A,B), A]): Reagent[B,Unit] = 
    new Atom[B,Unit] {}
  def updIO[B,C](f: PartialFunction[(A,B), (A,C)]): Reagent[B,C] = 
    new Atom[B,C] {}
}

object Examples {
  class TreiberStack[A] {
    val (push, pop) = {
      val (sPush, rPush) = SwapChan[A, Unit]
      val (sPop,  rPop)  = SwapChan[Unit, Option[A]]
      val head = new Ref[List[A]](List())

      rPush &> head.updI {case (xs,x) => x::xs} !! ;
      head.updO {
	case x::xs => (xs, Some(x))
	case emp   => (emp,  None)
      } &> rPop !!
      
      (sPush, sPop)
    }
  }
  class BlockingStack[A] {
    val (push, pop) = {
      val (sPush, rPush) = SwapChan[A, Unit]
      val (sPop,  rPop)  = SwapChan[Unit, A]
      val head = new Ref[List[A]](List())

      rPush &> head.updI {case (xs,x) => x::xs} !! ;
      head.updO {case x::xs => (xs, x)} &> rPop !!
      
      (sPush, sPop)
    }
  }
  class EliminationBackoffStack[A] {
    val (push, pop) = {
      val (sPush, rPush) = SwapChan[A, Unit]
      val (sPop,  rPop)  = SwapChan[Unit, A]
      val head = new Ref[List[A]](List())

      rPush &> head.updI {case (xs,x) => x::xs} !! ;
      head.updO {case x::xs => (xs, x)} &> rPop !! ;
      rPush &> rPop !!
      
      (sPush, sPop)
    }
  }
  class MSQueue[A >: Null] {
    val (enq, deq) = {
      class Node(val a: A) {
	val next = new Ref[Node](null)
      }
      object Node {
	def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
      }

      val (sEnq, rEnq) = SwapChan[A, Unit]
      val (sDeq, rDeq) = SwapChan[Unit, Option[A]]
      val sentinel = new Node(null)
      val head = new Ref(sentinel)
      val tail = new Ref(sentinel)

      // stuck...
      // head.updO {
      // 	case Node(x, n) => 
      // } &> rDeq !!

      (sEnq, sDeq)
    }
  }
}
