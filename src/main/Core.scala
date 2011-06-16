import java.util.concurrent.atomic._
import scala.annotation.tailrec
import scala.collection.mutable._

private object Util {
  def undef[A]: A = throw new Exception()
}

// sealed private class Transaction {
//   private case class CASLog(ov: Any, nv: Any)
//   private val redoLog = HashMap.empty[AtomicReference[Any], CASLog]

//   def read[A](r: Ref[A]): A = redoLog.getOrElseUpdate(r.data, {
//     val cur = r.data.get()
//     CASLog(cur, cur)
//   }).nv.asInstanceOf[A]

//   def cas[A](r: Ref[A], ov: A, nv: A) = Util.undef

//   def attempt: Boolean = Util.undef
// }

private sealed abstract class Atom
private case object Dup extends Atom
private case object Split extends Atom
private case object Swap extends Atom
private case object Merge extends Atom
private case object PushToAux extends Atom
private case object PopFromAux extends Atom
private case object DupToAux extends Atom
private case class Push[A](a: A) extends Atom
private case object Pop extends Atom
private case class Apply[A,B](f: PartialFunction[A,B]) extends Atom
private case class Thunk[A,B](f: () => Reagent[A,B])  extends Atom
private case class ThunkPush[A](a: () => A) extends Atom
private case class Read[A](r: Ref[A]) extends Atom
private case class CAS[A](r: Ref[A]) extends Atom
private case class CASAux[A](r: Ref[A]) extends Atom

private case class Upd[A,B](r: Ref[A], f: (A,B) => A) extends Atom

private case class TLData(istack: ArrayStack[List[Atom]],
			  dstack: ArrayStack[Any],
			  astack: ArrayStack[Any])

private object ThreadLocalStorage {  
  val data = new ThreadLocal[TLData]() {
    override def initialValue() =
      TLData(ArrayStack(List()), ArrayStack(()), ArrayStack(()))
  }
}

class Reagent[A,B] private (private val choices: List[List[Atom]]) {
  private abstract class Outcome
  private case object ShouldBlock extends Outcome
  private case object ShouldRetry extends Outcome
  private case class Success(a: Any) extends Outcome

  // private val istack = ArrayStack[List[Atom]](List())
  // private val dstack = ArrayStack[Any](())
  // private val astack = ArrayStack[Any](())

  private def attempt(m: List[Atom], a: Any): Outcome = {
    val TLData(istack, dstack, astack) = ThreadLocalStorage.data.get()

    istack.drain((_) => {})
    istack.push(m)

    dstack.drain((_) => {})
    dstack.push(a)

    astack.drain((_) => {})
    astack.push(())

    // val istack = ArrayStack(m)   // note: these should be re-used at
    // val dstack = ArrayStack(a)   //       least across choices/retries
    // val astack = ArrayStack[Any](())

//    var trans  = new Transaction

    while (!istack.isEmpty) {
      // print("dstack: ")
      // println(dstack)
      // print("astack: ")
      // println(astack)

      // istack.top match {
      // 	case atom :: _ => {
      // 	  println("")
      // 	  print("> ")
      // 	  println(atom)
      // 	  println("")
      // 	}
      // 	case _ => {}
      // }      

      istack.top match {
	case List() => 
	  istack.pop
	case Push(_) :: Pop :: rest => istack.update(0,rest)
	case Merge :: Split :: rest => istack.update(0,rest)
	case Split :: Merge :: rest => istack.update(0,rest)
	case atom :: rest => {
	  istack.update(0,rest)	// consume the instruction
	  atom match {		// interpret the instruction
	    case Dup => 
	      dstack.dup()
	    case Split => {
	      val (x,y) = dstack.pop()
	      dstack.push(x)
	      dstack.push(y)
	    }
	    case Swap => {
	      val x = dstack.pop()
	      val y = dstack.pop()
	      dstack.push(x)
	      dstack.push(y)
	    }
	    case Merge => {
	      val y = dstack.pop()
	      val x = dstack.pop()
	      dstack.push((x,y))
	    }
	    case PushToAux => 
	      astack.push(dstack.pop())
	    case PopFromAux =>
	      dstack.push(astack.pop())
	    case DupToAux =>
	      astack.push(dstack.top)
	    case Push(a) => 
	      dstack.push(a)
	    case Pop =>
	      dstack.pop
	    case Apply(f) => {
	      val data = dstack.pop()
	      if (f.isDefinedAt(data)) 
		dstack.push(f(data))
	      else return ShouldBlock
	    }
	    case Thunk(f) =>
	      istack.push(f().choices.head)
	    case ThunkPush(f) =>
	      dstack.push(f())
	    case Read(r) => {
  //	    dstack.update(0, trans.read(r))
	      dstack.push(r.data.get())
	    }
	    case CAS(r) => {
	      val nv = dstack.pop()
	      val ov = dstack.pop()
	      r.data.compareAndSet(ov, nv)
	    }
	    case CASAux(r) => {
	      val nv = dstack.pop()
	      val ov = astack.pop()
	      r.data.compareAndSet(ov, nv)
	    }
	    case Upd(r, f) => {
	      val ov = r.data.get()
	      r.data.compareAndSet(ov, f(ov, dstack.pop()))
	      dstack.push(())
	    }
	  }
	}
      }
    }

    // assert(dstack.size eq 1)

    //if (trans.attempt) Success(dstack.pop) else ShouldRetry
    Success(dstack.pop)
  }

  def !(a: A): B = {
    // initial cut: nonblocking version
    @tailrec def tryChoices(cs: List[List[Atom]]): B = cs match {
      case c :: cs1 => attempt(c,a) match {
	case Success(b) => b.asInstanceOf[B]
	case _ => tryChoices(cs1)
      }
      case List() => {
	// backoff()
	tryChoices(choices) // retry all choices
      }
    }
    tryChoices(choices)
  }

  def &>[C](next: Reagent[B,C]): Reagent[A,C] = new Reagent(for {
    choice <- this.choices
    nextChoice <- next.choices
  } yield choice ++ nextChoice)
  def <+>(that: Reagent[A,B]): Reagent[A,B] = new Reagent(this.choices ++ that.choices)

  def onLeft[C]: Reagent[(A,C), (B,C)] = 
    new Reagent(for (c <- choices) yield Split +: c :+ Merge)
  def onRight[C]: Reagent[(C,A), (C,B)] = 
    new Reagent(for (c <- choices) yield Split +: Swap +: c :+ Swap :+ Merge)
}
private object Reagent {
  def fromAtoms[A,B](as: Atom*): Reagent[A,B] = new Reagent(List(as.toList))
}

object Lift {
  def apply[A,B](f: PartialFunction[A,B]) = Reagent.fromAtoms(Apply(f))
}

/*
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
*/

class Ref[A](init: A) {
//  private[chemistry] val data = new AtomicReference[Any](init)
  val data = new AtomicReference[Any](init)

  def read: Reagent[Unit, A] = 
    Reagent.fromAtoms(	// ()
      Pop,		// 
      Read(this)	// A
    )
  def cas:  Reagent[(A,A), Unit] = 
    Reagent.fromAtoms(	// (A<ov>,A<nv>)
      Split,		// A<nv>,A<ov>
      Swap,		// A<ov>,A<nv>
      CAS(this),	// 
      Push(())		// ()
    )

  // interface using atomic update

  def upd(f: PartialFunction[A,A]): Reagent[Unit,Unit] = 
    Reagent.fromAtoms(	// ()
      Read(this),	// (),A
      Dup,		// (),A,A
      Apply(f),		// (),A,A
      CAS(this)		// ()
    )
  def updO[B](f: PartialFunction[A, (A,B)]): Reagent[Unit,B] = 
    Reagent.fromAtoms(	// ()
      Pop,		//
      Read(this),	// A
      Dup,		// A,A
      Apply(f),		// A,(A,B)
      Split,		// A,A,B
      PushToAux,	// A,A		B
      CAS(this),	//		B
      PopFromAux	// B
    )
  def updI[B](f: PartialFunction[(A,B), A]): Reagent[B,Unit] = 
    Reagent.fromAtoms(	// B
      Read(this),	// B,A
      DupToAux,		// B,A		A
      Swap,		// A,B		A
      Merge,		// (A,B)	A
      Apply(f),		// A		A
      CASAux(this),	// 
      Push(())		// ()
    )
  def updIO[B,C](f: PartialFunction[(A,B), (A,C)]): Reagent[B,C] = 
    Reagent.fromAtoms(	// B		
      Read(this),	// B,A
      DupToAux,		// B,A		A
      Swap,		// A,B		A
      Merge,		// (A,B)	A
      Apply(f),		// (A,C)	A
      Split,		// A,C		A
      Swap,		// C,A		A
      CASAux(this)	// C
    )
}

class TreiberStack[A] {
  private val head = new Ref[List[A]](List())
  // val pushReagent = head.updI[A]({
  //   case (xs,x) => x::xs
  // })
  val pushReagent = Reagent.fromAtoms[A,Unit](Upd[List[A],A](head,(xs,x) => x::xs))
  val popReagent = head updO {
    case x::xs => (xs, Some(x))
    case emp   => (emp,  None)
  }

  def push(x:A): Unit = pushReagent ! x
  def pop(): Option[A] = popReagent ! ()
}

class Stack[A >: Null] {
  class Node(val data: A, var next: Node) 

  // head always points to top of stack,
  //   from which the rest of the stack is reachable
  private val head = new AtomicReference[Node](null)

  def push(x: A) {
    val n = new Node(x, null)
    while (true) {
      n.next = head.get
      if (head compareAndSet (n.next, n)) return
    } 
  }

  def pop: Option[A] = {
    while (true) {
      val h = head.get
      if (h eq null) 
	return None
      else if (head compareAndSet (h, h.next)) 
	return Some(h.data) 
    }
    throw new Exception("Impossible")
  } 

  // definitely possible to do this all at once with a CAS on head
  def popAll: List[A] = {
    @tailrec def grabAll(acc: List[A]): List[A] = 
      pop match {
	case None => acc
	case Some(x) => grabAll(x :: acc)
      }
    grabAll(List()).reverse
  }
}


// object Test extends Application {
//   val s1 = new TreiberStack[Integer]()
//   s1.pop() match {
//     case None => println("Test 1: pass")
//     case _    => println("Test 1: fail")
//   }

//   val s2 = new TreiberStack[Integer]()
//   s2.push(1)
//   s2.pop() match{
//     case None => println("Test 2: fail")
//     case _    => println("Test 2: pass")
//   }

//   val s3 = new TreiberStack[Integer]()
//   s3.push(1)
//   s3.pop()
//   s3.pop() match {
//     case None => println("Test 3: pass")
//     case _    => println("Test 3: fail")
//   }

//   val s4 = new TreiberStack[Integer]()
//   s4.push(1)
//   s4.push(2)
//   (s4.pop(), s4.pop()) match {
//     case (Some(x), Some(y)) => 
//       if (x.intValue == 2 && y.intValue == 1)
// 	println("Test 4: pass")
//       else println("Test 4: fail")
//     case _    => println("Test 4: fail")
//   }
// }

object Bench extends Application {
  import java.util.Date

  val d = new Date()
  val trials = 50

  def getTime = (new Date()).getTime
  def withTime(msg: String)(thunk: => Unit) {
    thunk // warm up
    println(msg)
    var sum: Long = 0
    for (i <- 1 to trials) {
      val t = getTime
      thunk
      val t2 = getTime
      println(t2 - t)
      sum += (t2 - t)
    } 
    print("avg: ")
    println(sum / trials)
  }

  withTime("Reagent-based") {
    val ts = new TreiberStack[java.util.Date]()
    for (i <- 1 to 100000)
      ts.push(d)
  }

  withTime("Direct") {
    val ts = new Stack[java.util.Date]()
    for (i <- 1 to 100000)
      ts.push(d)
  }
}


/* 

object Examples {
  def cons[A](p:(List[A],A)) = p._2::p._1

  class TreiberStack[A] {
    private val head = new Ref[List[A]](List())
    val push = head updI {
      case (xs,x) => x::xs
    }
    val pop  = head updO {
      case x::xs => (xs, Some(x))
      case emp   => (emp,  None)
    }
  }
  class TreiberStack2[A] {
    private val head = new Ref[List[A]](List())
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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
      val next = new Ref[Node](null)
    }
    object Node {
      def unapply(n: Node): Option[(A, Ref[Node])] = Some((n.a, n.next))
    }
    private val (head, tail) = {
      val sentinel = new Node(null)
      (new Ref(sentinel), new Ref(sentinel))
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

*/
