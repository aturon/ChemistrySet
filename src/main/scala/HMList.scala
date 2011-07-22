// invariant: if a node n was ever reachable from head, and a node m
// is reachable from n, then all keys >= m are reachable from n.
sealed class Set[A] {
  private abstract class Node 
  private abstract class PNode extends Node {
    def next: Ref[Node]
    def retryIfDel: Reagent[()]
  } 
  private object PNode {
    def unapply(pn: PNode): Option[Ref[Node]] = Some(pn.next)
  }
  private case object Tail extends Node
  private case object Head extends PNode {
    val next: Ref[Node] = Ref(Tail)
    val retryIfDel: Reagent[()] = Return(())
  }
  private case class INode(
    next: Ref[Node], 
    data: A, 
    deleted: Ref[Boolean] = Ref(false)
  ) extends PNode {
    val retryIfDel: Reagent[()] = deleted.cas(false, false)
  }

  private abstract class FindResult
  private case class Found(pred: PNode, node: INode)   extends FindResult
  private case class NotFound(pred: PNode, succ: Node) extends FindResult

  private @inline final def find(key: Int): FindResult = {
    @tailrec def walk(c: PNode): (PNode, Node) = c match {
      case PNode(Ref(Tail)) => 
	(c, Tail)
      case PNode(r@Ref(n@INode(Ref(m), _, Ref(true)))) => 
	r.cas(n, m) !?; walk(c)
      case PNode(Ref(n@INode(_, data, Ref(false)))) =>	
	if (key == data.hashCode())     Found(c, n) 
	else if (key < data.hashCode()) NotFound(c, n)
	else walk(n)
    }
    walk(Head)
  }

  final def add(item: A): Reagent[Boolean] = Loop {
    find(item.hashCode()) match {
      case Found(_, node) =>  // blocking would be *here*
	node.retryIfDel >> Return(false)
      case NotFound(pred, succ) => 
	(pred.next.cas(succ, INode(Ref(succ), item)) >>
	 pred.retryIfDel >> Return(true))
    }
  }

  final def remove(item: A): Reagent[Boolean] = Loop {
    find(item.hashCode()) match {
      case NotFound(pred, _) => // blocking would be *here*
	pred.retryIfDel >> Return(false)
      case Found(pred, node) => 
	(node.deleted.cas(false, true) >> Return(true)) commitThen
        pred.next.cas(node, node.next.get) !?
    }
  }

//  def contains: Reagent[A, Boolean]
}
