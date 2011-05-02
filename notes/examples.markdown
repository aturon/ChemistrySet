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
        
