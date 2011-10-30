package chemistry

import scala.collection.immutable._

final class SimpleQueue[A] {
  private val head = Ref(Queue.empty[A])
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
    def composeI[B](next: Reagent[Unit,B]) = throw Util.Impossible
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
    def composeI[B](next: Reagent[Option[A],B]) = throw Util.Impossible
    def maySync = false
    def alwaysCommits = false
    def snoop(a: Unit) = false
  }
}
