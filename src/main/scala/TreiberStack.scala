// An implementation of the classic Treiber stack via reagents

package chemistry

import scala.annotation.tailrec
import java.util.concurrent.atomic._

final class TreiberStack[A >: Null] {

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

  val push: Reagent[A,Unit] = head.updIn { 
    (xs,x) => x::xs
  }

  val tryPop: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case (x::xs) => (xs,  Some(x))
    case emp     => (emp, None)
  }

  val pop: Reagent[Unit,A] = head.upd[A] {
    case (x::xs) => (xs, x)
  }
*/


  private case class Node(var data: A, var next: Node) 
  private val head = Ref[Node](null)

  val push: Reagent[A,Unit] = new head.Upd[A,Unit] {
    @inline final def newValue(cur: Node, a: A): Node = 
      Node(null, null)
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

/*
  case class Node(var data: A, var next: Node) extends Retry
  val head = new AtomicReference[Node](null)

  object push extends Reagent[A,Unit] {
    type Cache = Node
    def useCache = true
    def tryReact(x:A, rx: Reaction, cache: Node): Any = {
      val cached: Node = if (cache == null) Node(x, null) else cache
      cached.next = head.get
      if (head.compareAndSet(cached.next,cached)) () 
      else cached
    }
    def makeOfferI(x:A, offer: Offer[Unit]) {}
    def composeI[B](next: Reagent[Unit,B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
    @inline def fastReact(x: A): Unit = {
      val cached = Node(x, null)
      while (true) {
	cached.next = head.get
	if (head.compareAndSet(cached.next, cached)) return ()
      }
      throw Util.Impossible      
    }
    def snoop(a: A) = false
  }

  object tryPop extends Reagent[Unit,Option[A]] {
    type Cache = Retry
    def useCache = false
    @inline def tryReact(u:Unit, rx: Reaction, cache: Retry): Any = 
      head.get match {
	case null => None
	case n@Node(x, xs) =>
	  if (head.compareAndSet(n,xs)) Some(x) else RetryUncached
	case emp     => None
      }
    @inline def fastReact(u: Unit): Option[A] = {
      while (true) {
	tryReact(u, Inert, null) match {
	  case RetryUncached => {}
	  case ans => return ans.asInstanceOf[Option[A]]
	}
      }
      throw Util.Impossible
    }
    def makeOfferI(u:Unit, offer: Offer[Option[A]]) {}
    def composeI[B](next: Reagent[Option[A],B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }

  def pop: Reagent[Unit,A] = throw Util.Impossible
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
