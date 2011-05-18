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

  def <&>[C](r: Reagent[A,C]): Reagent[A, (B,C)] = {
    throw new Exception("Not implemented")
  }

  def <|>(r: Reagent[A,B]): Reagent[A,B] = {
    throw new Exception("Not implemented")
  }

  def map[C](f: B => C): Reagent[A,C] = &>(f)
//  def filter[B](f: B => Boolean): Reagent[A,B] = &>({case x:B if f(x) => x})
}
object Reagent {
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
  
  // interface using separate reads/writes
  
  def rd: Reagent[Unit,A] = new Atom[Unit,A] {}
  def wr: Reagent[A,Unit] = new Atom[A,Unit] {}

  // interface using atomic update

  def upd(f: PartialFunction[A,A]): Reagent[Unit,Unit] = 
    new Atom[Unit,Unit] {}
  def updO[B](f: PartialFunction[A, (A,B)]): Reagent[Unit,B] = 
    new Atom[Unit,B] {}
  def updI[B](f: PartialFunction[(A,B), A]): Reagent[B,Unit] = 
    new Atom[B,Unit] {}
  def updIO[B,C](f: PartialFunction[(A,B), (A,C)]): Reagent[B,C] = 
    new Atom[B,C] {}
}
object Ref {
  def dynUpd[A](f: A => A): Reagent[Ref[A],Unit] = 
    new Atom[Ref[A],Unit] {}
}

object Examples {
  def cons[A](p:(List[A],A)) = p._2::p._1

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
class EliminationBackoffStackUsingRW[A] {
    val (push, pop) = {
      val (sPush, rPush) = SwapChan[A, Unit]
      val (sPop,  rPop)  = SwapChan[Unit, A]
      val head = new Ref[List[A]](List())

      head.rd <&> rPush &> (cons[A]_) &> head.wr !! ;
      head.rd &> ::.unapply(_).lift

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

//      head.updO {
//      	case Node(x, n) => 
//      } &> rDeq !!

      (sEnq, sDeq)
    }
  }
}

