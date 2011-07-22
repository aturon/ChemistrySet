Wide range of (sketchy) examples:

    object Examples {
      def cons[A](p:(List[A],A)) = p._2::p._1

      class TreiberStack[A] {
	private val head = Ref[List[A]](List())
	val push = head updI {
	  case (xs,x) => x::xs
	}
	val pop  = head updO {
	  case x::xs => (xs, Some(x))
	  case emp   => (emp,  None)
	}
      }
      class TreiberStack2[A] {
	private val head = Ref[List[A]](List())
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
	  val next = Ref[Node](null)
	}
	object Node {
	  def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
	}
	private val (head, tail) = {
	  val sentinel = new Node(null)
	  (Ref(sentinel), Ref(sentinel))
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
	  val next = Ref[Node](null)
	}
	object Node {
	  def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
	}
	private val (head, tail) = {
	  val sentinel = new Node(null)
	  (Ref(sentinel), Ref(sentinel))
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
	  val next = Ref[Node](null)
	}
	object Node {
	  def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
	}
	private val (head, tail) = {
	  val sentinel = new Node(null)
	  (Ref(sentinel), Ref(sentinel))
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

Async attempt:

    reactant { someSync(x) -> SOME _ | always NONE }

Cancellable lock acquisitions:

    catalyst { acq & rel => . }
    reactant { acq | cancel => . }    // cancel is an arbitrary channel
    
Enforced lock protocol?

    catalyst { acq & status(0) => status(Thread.id)
               rel & status(tid) if tid == Thread.id => status(0)
	       rel & status(tid) if tid != Thread.id => status(tid); throw new Exception() }

Auto-reset:

    wait() & triggered(true) => triggered(false)
    set() & triggered(false) => triggered(true)

this is the same as a unit p/c buffer:

    wait() & set() => .

Could also imagine setting this up as a one-off pattern -- which would
allow arbitrary choice of wait channel, or potentially more complex
patterns: myWait() & set1() & set2() => .

Manual-reset, i.e. broadcast:

    wait() & triggered(true) => triggered(true)
    set() & triggered(false) => triggered(true)
    reset() & triggered(_)   => triggered(false)
    
Treiber's:

    Push(x) & State(s) => State(x:s) 
    Pop() & State(x:s) => State(s);   SOME x
    Pop() & State(nil) => State(nil); NONE

With blocking Pop:

    Push(x) & Pop() => { return x; }
    
Lock-free queue (unfinished):    
    
    Deq() & Tail(xs) => Tail(xs); NONE
      when Head = xs and snd xs = null
    Deq() & Tail(x:xs) => Tail(xs); Deq()
      when Head = xs
    Deq() & Head(x:xs)

    Deq() & Head(x:nil) => Head(x:nil); NONE
    Deq() & Head(xs) & Tail(xs) 
        
