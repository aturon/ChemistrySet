package chemistry

sealed class MSQueue[A >: Null] {
  private case class Node(data: A, next: Ref[Node] = Ref(null))
  private val head = Ref(Node(null))
  private val tail = Ref(head.read !)

  final def enq(x:A): Reagent[Unit] = Loop {
    tail.read ! match {
      case    Node(_, r@Ref(null)) => r.mkcas(null, Node(x))
      case ov@Node(_, Ref(nv))     => tail.cas(ov,nv) !?; Retry
    }
  }
  final val deq: Reagent[Option[A]] = head upd {
    case Node(_, Ref(n@Node(x, _))) => (n, Some(x))
    case emp => (emp, None)
  }
}
