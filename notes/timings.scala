sealed class MSQueue[A >: Null] {
  private case class Node(data: A, next: Ref[Node] = new Ref(null))
  private val head = new Ref(Node(null))
  private val tail = new Ref(head.read!())

  final val constUnit = Const(())
  final val mkUnitFn: A => Unit = (a:A) => ()
  final val mkUnit: Reagent[A, Unit] = ((a:A) => ())

  final val enq1: Reagent[A, Unit] = Loop {mkUnit}	// 303,030/ms
							// 526,315/ms (with esc ana)

  final val enq2: Reagent[A, Unit] = ((a:A) => ())	// 303,030/ms

  final val enq3: Reagent[A, Unit] = Const(())		// 303,030/ms

  final val enq4: Reagent[A, Unit] = Loop {		//  90,090/ms
							// 156,250/ms (with esc ana)
    ((a:A) => ())
  } 

  final val enq5: Reagent[A, Unit] = Loop {mkUnitFn}	//  73,529/ms

  final val enq6: Reagent[A, Unit] = Loop {		//  68,493/ms
    Lift(mkUnitFn)
  } 

  // final val enq7: Reagent[A, Unit] = Loop {		//  72,764/ms
  //   tailData.get()
  //   mkUnit
  // }

  // final val enq7b: Reagent[A, Unit] = Loop {		//  75,757/ms
  //   tailData.get()
  //   constUnit
  // }

  // final val enq7c: Reagent[A, Unit] = Lift ((a:A) => {	// 104,166/ms
  //   tailData.get()
  //   ()
  // })

  // final val enq: Reagent[A, Unit] = Lift ((a:A) => {	//   6,317/ms
  //   val n = Node(a)
  //   val tailVal = tail.data.get()
  //   val ref = tailVal.next
  //   ref.data.compareAndSet(null, n)
  //   tail.data.compareAndSet(tailVal, n)
  //   ()
  // })

  // final val enq8: Reagent[A, Unit] = Loop {		//  58,139/ms
  //   tailData.get() match {
  //     case Node(_, ref@Ref(null)) =>    mkUnit
  //     case ov@Node(_, Ref(nv)) =>     mkUnit
  //   }
  // }

  final val enq9: Reagent[A, Unit] = Loop {		//   5,238/ms
    tail.get() match {
      case Node(_, ref@Ref(null)) =>    
  	Lift((a: A) => {
  	  ref.compareAndSet(null, Node(a))
  	  ()
  	})
      case ov@Node(_, Ref(nv)) => 
  	tail.compareAndSet(ov,nv); Retry
    }
  }

  final val enq10: Reagent[A, Unit] = Lift ((a:A) => {	//   8,077/ms
    val n = Node(a)
    val tailVal = tail.get()
    val ref = tailVal.next
    ref.compareAndSet(null, n)
    tail.compareAndSet(tailVal, n)
    ()
  })

  object TestFn extends Function[A,Unit] {
    @inline final def apply(a: A) {
      val n = Node(a)
      val tailVal = tail.get()
      val ref = tailVal.next
      ref.compareAndSet(null, n)
      tail.compareAndSet(tailVal, n)
    }
  }

  final val enq11: Reagent[A, Unit] = Lift(TestFn)	//   7,127/ms

  private final val nodeWrap = Lift(Node(_:A))

  final val enq12: Reagent[A, Unit] = Loop {		//  4,541/ms
    tail.get() match {
      case Node(_, ref@Ref(null)) =>
  	nodeWrap &> ref.casFrom(null)
      case ov@Node(_, Ref(nv)) => 
  	tail.compareAndSet(ov,nv); Retry
    }
  }

  // final val enq0: Reagent[A, Unit] = Loop {		//  4,159/ms
  //   tail.read ! () match {
  //     case Node(_, ref@Ref(null)) =>
  // 	nodeWrap &> ref.casFrom(null)
  //     case ov@Node(_, Ref(nv)) => 
  //   	tail.cas(ov,nv) !? (); Retry
  //   }
  // }

  final val enq13: Reagent[A, Unit] = Loop {		//  4,332/ms
    tail.read ! () match {
      case Node(_, ref@Ref(null)) =>
	ref.cas((_) => null, Node(_:A))
      case ov@Node(_, Ref(nv)) => 
    	tail.compareAndSet(ov,nv); Retry
//    	tail.cas(ov,nv) !? (); Retry
    }
  }

  final val enq: Reagent[A, Unit] = Loop {		//  4,332/ms
    tail.read ! () match {
      case Node(_, ref@Ref(null)) =>
	ref.cas((_) => null, Node(_:A))
      case ov@Node(_, Ref(nv)) => 
    	tail.compareAndSet(ov,nv); Retry
//    	tail.cas(ov,nv) !? (); Retry
    }
  }

  val deq: Reagent[Unit, Option[A]] = head upd {
    case (Node(_, Ref(n@Node(x, _))), ()) => (n, Some(x))
    case (emp, ()) => (emp, None)
  }
}
