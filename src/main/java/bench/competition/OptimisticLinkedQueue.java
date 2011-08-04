package chemistry.bench.competition;

import java.util.AbstractQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// implementation from the paper 
//   "An optimistic approach to lock-free FIFO queues"
// by Edya Ladan-Mozes and Nir Shavit

public class OptimisticLinkedQueue<E> {
    private static class Node<E> {
	private volatile E item;
	private volatile Node<E> next;
	private volatile Node<E> prev;
	Node(E x) { item = x; next = null; prev = null; }
	Node(E x, Node<E> n) { item = x; next = n; prev = null; }
	E getItem() {
	    return item;
	}
	void setItem(E val) {
	    this.item = val;
	}
	Node<E> getNext() {
	    return next;
	}
	void setNext(Node<E> val) {
	    next = val;
	}
	Node<E> getPrev() {
	    return prev;
	}
	void setPrev(Node<E> val) {
	    prev = val;
	}
    }
    private static final
	AtomicReferenceFieldUpdater<OptimisticLinkedQueue, Node>
	tailUpdater =
	AtomicReferenceFieldUpdater.newUpdater
	(OptimisticLinkedQueue.class, Node.class, "tail");
    private static final
	AtomicReferenceFieldUpdater<OptimisticLinkedQueue, Node>
	headUpdater =
	AtomicReferenceFieldUpdater.newUpdater
	(OptimisticLinkedQueue.class, Node.class, "head");
    private boolean casTail(Node<E> cmp, Node<E> val) {
	return tailUpdater.compareAndSet(this, cmp, val);
    }
    private boolean casHead(Node<E> cmp, Node<E> val) {
	return headUpdater.compareAndSet(this, cmp, val);
    }
    /**
     * Pointer to the head node, initialized to a dummy node. The first
     * actual node is at head.getPrev().
     */
    private transient volatile Node<E> head = new Node<E>(null, null);
    /** Pointer to last node on list **/
    private transient volatile Node<E> tail = head;
    /**
     * Creates a <tt>ConcurrentLinkedQueue</tt> that is initially empty.
     */
    public OptimisticLinkedQueue() {}
    /**
     * Enqueues the specified element at the tail of this queue.
     */
    public boolean offer(E e) {
	if (e == null) throw new NullPointerException();
	Node<E> n = new Node<E>(e, null);
	for (;;) {
	    Node<E> t = tail;
	    n.setNext(t);
	    if (casTail(t, n)) {
		t.setPrev(n);
		return true;
	    }
	}
    }
    /**
     * Dequeues an element from the queue. After a successful
     casHead, the prev and next pointers of the dequeued node are
     set to null to allow garbage collection.
    */
    public E poll() {
	for (;;) {
	    Node<E> h = head;
	    Node<E> t = tail;
	    Node<E> first = h.getPrev();
	    if (h == head) {
		if (h != t) {
		    if (first == null){
			fixList(t,h);
			continue;
		    }
		    E item = first.getItem();
		    if (casHead(h,first)) {
			h.setNext(null);
			h.setPrev(null);
			return item;
		    }
		}
		else
		    return null;
	    }
	}
    }
    /**
     * Fixing the backwords pointers when needed
     */
    private void fixList(Node<E> t, Node<E> h){
	Node<E> curNodeNext;
	Node<E> curNode = t;
	while (h == this.head && curNode != h){
	    curNodeNext = curNode.getNext();
	    if (curNodeNext != null) {
		curNodeNext.setPrev(curNode);
		curNode = curNode.getNext();
	    }
	}
    }
}