package chemistry

import scala.collection.immutable._

final class SimpleQueue[A] {
  private val head = Ref(Queue.empty[A])

  private val enqForComp: Reagent[A,Unit] = head.upd[A,Unit] { 
    case (xs,x) => (xs.enqueue(x), ())
  }

  private val tryDeqForComp: Reagent[Unit,Option[A]] = head.upd[Option[A]] {
    case xs if (xs.isEmpty) => (xs, None) 
    case xs => xs.dequeue match { 
      case (x, rest) => (rest, Some(x)) 
    }
  }

  private val deqForComp: Reagent[Unit,A] = head.upd[A](
    new PartialFunction[Queue[A], (Queue[A], A)] {
      def apply(xs: Queue[A]) = xs.dequeue match { case (x, rest) => (rest, x) }
      def isDefinedAt(xs: Queue[A]) = !xs.isEmpty
    }
  )

  object enq extends Reagent[A,Unit] {
    def tryReact(x:A, rx: Reaction, offer: Offer[Unit]): Any = {
      val cur = head.data.get
      if (cur eq null) 
	Retry
      else {
	val upd = cur.enqueue(x)
	if (head.data.compareAndSet(cur,upd)) () 
	else Retry
      }
    }
    def composeI[B](next: Reagent[Unit,B]) = enqForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: A) = false
  }

  object tryDeq extends Reagent[Unit,Option[A]] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[Option[A]]): Any = {
      val cur = head.data.get
      if (cur eq null)
	Retry
      else if (cur.isEmpty)
	None
      else {
	val (x, rest) = cur.dequeue
	if (head.data.compareAndSet(cur,rest)) Some(x) else Retry
      }
    }
    def composeI[B](next: Reagent[Option[A],B]) = tryDeqForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }

  object deq extends Reagent[Unit,A] {
    @inline def tryReact(u:Unit, rx: Reaction, offer: Offer[A]): Any = {
      val cur = head.data.get
      if (cur eq null)
	Retry
      else if (cur.isEmpty)
	Retry //nonblocking version
      else {
	val (x, rest) = cur.dequeue
	if (head.data.compareAndSet(cur,rest)) x else Retry
      }
    }
    def composeI[B](next: Reagent[A,B]) = deqForComp >=> next
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }
}
