// An implementation of the classic Treiber stack via reagents

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._
import scala.collection.immutable._

final class TreiberStack[A >: Null] {
/*
    new Reagent[B,C] {
      private val k = Commit[C]()
      def tryReact(b: B, rx: Reaction, offer: Offer[C]): Any = {
	if (rx.canCASImmediate(k, offer)) {
	  // no need to store offer here, as we will either succeed or retry
	  // (never block)

	  val ov = r.getI
	  val (nv, retVal) = f(ov, b)
	  if (r.casI(ov, nv)) retVal
	  else Retry
	} else {
//	  if (offer != null) offers.put(offer) 

	  val ov = r.getI
	  val (nv, retVal) = f(ov, b)
	  k.tryReact(retVal, rx.withCAS(r, ov, nv), offer)
	}
      }
      def composeI[D](next: Reagent[C,D]) = throw Util.Impossible/*
	new r.InnerUpd[B,C,D](next) {
	  @inline def newValue(a: A, b: B): A = f(a,b)._1
	  @inline def retValue(a: A, b: B): C = f(a,b)._2
	}*/
      def maySync = false
      def alwaysCommits = false
      def snoop(b: B) = false
    }
    */

/*
  val push: Reagent[A,Unit] = head.fastUpd[A,Unit](
    { (xs,x) => x::xs },
    { (_, _) => () }
  )

  val tryPop: Reagent[Unit,Option[A]] = head.fastUpd[Unit,Option[A]](
    { case (x::xs, _) => xs
      case (emp,   _) => emp },
    { case (x::xs, _) => Some(x)
      case (emp,   _) => None }
  )
*/

/*
  private val head = Ref[List[A]](List())

  val push: Reagent[A,Unit] = upd.fast[List[A],A,Unit](head, { 
    (xs,x) => (x::xs, ())
  })

  val tryPop: Reagent[Unit,Option[A]] = upd.fast[List[A],Unit,Option[A]](head, {
    case ((x::xs),()) => (xs,  Some(x))
    case (Nil, ())    => (Nil, None)
  })

  val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  }
*/

/*
  private case class Node(val data: A, val next: Node) 
  private val head = Ref[Node](null)

  val push: Reagent[A,Unit] = new head.Upd[A,Unit] {
    @inline final def newValue(cur: Node, a: A): Node = 
      Node(a, cur)
    @inline final def retValue(cur: Node, a: A): Unit = ()
  }

  val tryPop: Reagent[Unit,Option[A]] = new head.Upd[Unit,Option[A]] {
    @inline final def newValue(cur: Node, u: Unit): Node = cur match { 
      case null   => null
      case Node(x,xs) => xs
    }
    @inline final def retValue(cur: Node, u: Unit): Option[A] = cur match {
      case null   => None
      case Node(x,xs) => Some(x)
    }
  }

  def pop: Reagent[Unit,A] = head.upd[A] {
    case Node(x,xs) => (xs, x)
  }
*/

  private val head = new Ref[List[A]](Nil)

  val push: Reagent[A,Unit] = head.upd[A,Unit] { 
    case (xs,x) => (x::xs, ())
  }

  val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case x::xs => (xs,  Some(x))
    case Nil   => (Nil, None)
  }

  val pop: Reagent[Unit,A] = head.upd[A] {
    case x::xs => (xs, x)
  }

/*
  private val pushForComp: Reagent[A,Unit] = head.upd[A,Unit] { 
    case (xs,x) => (x::xs, ())
  }

  private val tryPopForComp: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case x::xs => (xs,  Some(x))
    case Nil   => (Nil, None)
  }

  private val popForComp: Reagent[Unit,A] = head.upd[A] {
    case x::xs => (xs, x)
  }
*/

/*
  object push extends Reagent[A,Unit] {
    def tryReact(x:A, rx: Reaction, offer: Offer[Unit]): Any = {
      val cur = head.data.get
      if (cur eq null) 
	Retry
      else {
	val upd = x +: cur
	if (head.data.compareAndSet(cur,upd))
	  () 
	else 
	  Retry
      }
    }
    def composeI[B](next: Reagent[Unit,B]) = pushForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: A) = false
  }

  object tryPop extends Reagent[Unit,Option[A]] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[Option[A]]): Any = {
      val cur = head.data.get
      if (cur eq null) 
	Retry
      else if (cur.isEmpty)
	None
      else if (head.data.compareAndSet(cur,cur.tail)) 
	Some(cur.head) 
      else 
	Retry 
    }
    def composeI[B](next: Reagent[Option[A],B]) = tryPopForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }

  object pop extends Reagent[Unit,A] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[A]): Any = {
      val cur = head.data.get
      if (cur eq null) 
	Retry
      else if (cur.isEmpty)
	Retry //nonblocking version
      else if (head.data.compareAndSet(cur,cur.tail)) 
	cur.head
      else 
	Retry 
    }
    def composeI[B](next: Reagent[A,B]) = popForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }
*/

/*
  object pop extends Reagent[Unit,A] {
    @tailrec def tryReact(u:Unit, rx: Reaction): Any = headX.get match {
      case (ov@(x::xs)) => 
	if (headX.compareAndSet(ov,xs)) x else ShouldRetry
      case emp     => tryReact(u, rx)
    }
    def makeOfferI(u:Unit, offer: Offer[A]) {}
    def composeI[B](next: Reagent[A,B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
  }
*/

/*
  private val headX = Ref[List[A]](List())

  val dpush: Reagent[A,Int] = head.upd { 
    (xs,x) => (x::xs, 0)
  }

  val dtryPop: Reagent[Unit,Int] = head.upd[Int] {
    case (x::xs) => (xs,  0)
    case emp     => (emp, 0)
  }
*/ 
}
