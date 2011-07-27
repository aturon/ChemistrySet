Need to read:

 - asynchronous events
 - more on lightweight threading (good blog fodder)
 - F# async workflows

To try coding:

 - stack/queue tests with concurrent dequeuing
 - additional stack/queue operations a la JUC
 - flat combiner version of stack/queue
 - other classic join calculus examples
 - classic CML examples
 - hand-over-hand set
 
Todo:

 - kcas
 - lazy set
 ~ channels 
 - blocking
 x elimination backoff stack
 ~ synchronization examples from Scalable Joins
 x choice
 x guards/never
 - bags
 - skiplist
 - kcss
 - skiplist-based map
 - hashtable
 
## 7/27/2011

Join calculus does not, per se, require the dissolve primitive.  Need
to find good justification for it (and for traditional join patterns).
Best example so far is n-way exchange (need to code that up).

Actually, though, any kind of message-passing "service" -- whether
joining multiple channels or not -- will benefit from `dissolve`.  The
only requirement is that the service doesn't internally require mutual
exclusion.  cf. `loop` construct in Scala actors.

Condition variables are rather hard to fit.  Should be able to compose
`await` calls on multiple conditions.  Need to be able to efficiently
implement *both* `signal` and `signalAll`, which ideally means:
quietly move waiter(s) from condition queue to "lock" waiter queue,
without actually waking them.

Don't need a separate mechanism for asynchronous channels: already
have them via queues/stacks/bags/...
 
## 7/25/2011

Tempting to remove general sending from reagents, opting for a more
asymmetric, joins style setup.  However, there's an easy motivation
for including full expressiveness: linking two elimination-backoff
stacks.

Tied to this: would be very good to show that elimination backoff can
be generalized to other data structures, using reagents.
 
## 7/23/2011

Need to collect examples of interaction between share-state and
message-passing reagents.

Need to think *very* carefully about the interaction of unrecorded IO
operations and STM-like semantics for sequenced reagents.  E.g.,
imagine composing two stack pushes, or two set insertions.  Could be a
good argument in favor of composing on disjoint state only.  Still get
something more expressive than JUC library, but less composable than
full STM.
 
Interesting note about Transactional Events: the "completeness"
property effectively forces unordered message delivery -- this likely
means that the NP-hardness doesn't depend essentially on CML's choice
construct.

Exponential search is not really "pay as you go": any *sequence* of
sends can, in general, lead to a search.  Use of a "parallel"
composition (to avoid dependency) cuts down to linear.  This is part
of what makes the join encoding efficient.
 
## 7/22/2011

Another example to check: Herlihy&Shavit, the chapter on hashing,
talks about the difficulty in atomically moving elements between
buckets.

Very hard to see a way to avoid constant closure and operation
allocation in the monadic API.
 
## 7/19/2011

Should give more thought to incorporating (coarse-grained) locking
into the framework.  Could provide a "scripts to programs" evolution
(using reagent abstraction).  Also could improve on locking, by
offering direct composition mechanism that automatically avoids
deadlock.
 
## 7/17/2011

Additional examples to explore:

- n-way rendezvous.  Does this require "parallel join"?
- phasers (requires extensions to joins) 
- locks, etc with associated *condition variables* (or join-style equivalent)

## 7/16/2011

In the model, leaving out IO wrapping in bind will definitely simplify
atomicity.  It may also simplify blocking.

## 7/15/2011

Consider an exchange join pattern:

    send1 >> send2
    
enrolled as a catalyst.  Due to the monadic structure, the catalyst is
only enrolled on the `send1` endpoint.

Now add reagent `~send1` to the solution.  Since there is not yet a
message on `~send2`, this reagent must block.  *Somehow*, a waiter
must be enrolled on the `~send2` channel.  However, the `~send1`
reagent must also be enrolled on the `~send1` channel, to account for
additional `send1` messages.

In fact, it doesn't seem to matter that `send1 >> send2` was enrolled
as a catalyst: any partial reaction that winds up blocking at a new
location must be enrolled, to avoid lost wakeups.

Complicated, but extremely expressive...

## 7/14/2011

For semantic presentation, go with consistent STM for simplicity; it's
an orthogonal implementation choice anyway.

Monadic dependency also makes catalysts harder to understand: the
channels waited on can depend on current Ref cell contents, etc.

Confusing point: the channel blocking enrollment of a reagent may
depend on the state of reference cells, which may be altered by the
reagent that is attempting to send a message along that channel.
E.g.,

    c <- r.read
    c.send(d)
    
    x <- ~c.send(())
    r.write(x)
    
Monadic dependency will no doubt "hide" potential blocking (since, in
fact, it is unpredictable without the monadically-supplied data).  Put
differently, ignoring choice, a reagent can block on only one channel
at a time.

Note: joining two reagents via a channel communication is *not* akin
to sequencing one within the other.  To ensure isolation on shared
state, must keep the reagents separately and track conflicts.

This last point has interesting ramifications for catalysts -- in
particular, from join calculus examples like Barrier.  However, if
necessary a separate combinator could be introduced that, essentially,
relinquishes the possibility of data dependency and thereby allows
simultaneous blocking.  This is how join patterns would be
interpreted.

It might be possible to interpret such an operator as the way that
"parallel" (communicating) reagents interact.

## 7/13/2011

Imagine:

    x <- r.read
    r.write(x+1)
    y <- chan.recv
    r.write(x)
    return y
    
in parallel with

    x <- r.read
    chan.send(x)
    
Can the first reagent return `x+1`?

In the paper *Transaction Communicators*, this situation is
essentially forbidden:

> a transaction that sees the effects of another transaction on a
> non-communicator object must be ordered strictly after the other
> transaction. Thus, mutually dependent transactions that make
> conflicting accesses on non-communicator objects cannot be
> committed.

In *Communicating Memory Transactions*, a similar requirements seems
to be used: later transactions in a cluster must be "right movers" wrt
earlier transactions, in order for the cluster to be committed.

## 6/29/2011

Guarded choice effectively entails the same implementation challenges
as free guards.  E.g.

    messagePassingReagent &>
        choice(
    	  p => someReagent
    	  else => otherReagent)

at linearization of `otherReagent`, no output of
`messagePassingReagent` *satisfying `p`* can be available.

Problem: does biased choice really work correctly with a *thunked*
reagent?  E.g., a reagent for telling whether an element is contained
in a linked-list-set.  Perhaps this is OK: need to make *very* clear
that a computed reagent must contain enough memory manipulation to
work correctly with compositions.  In fact, essentially the same
concerns occur with standard joins on thunked reagents.

Note: if lifted partial functions are not allowed, `Never` is no
longer expressible (though `Always` is: as `Const` or `Lift`).

Reagent constructions:

    ref.read
    ref.cas
    chan.send
    &>
    <+>	(external choice)
    +>	(biased external choice)	
    lift(function)
    lift(partial function)
    thunk
    retry
    never (?)
    
This code

    bq.enq = rDeq +> nbq.enq
    bq.deq = nbq.deq &> 
        {case Some(x) => x} <+>
	Const(()) &> sDeq
	
is problematic: the choice to block on `sDeq` depends on the outcome
of a previous reagent, `nbq.deq`, which is subject to change.

Also, the combination of biased choice, messages, and guards could
create substantial difficultly: the linearization point for overcoming
the bias could require examining data of arbitrary complexity.  This
is not a problem in general for reagents, because the standard
blocking code will first enqueue an appropriate message, and then
double-check (just once).  This is, in effect, a visible read!

## 7/7/2011

Localized channels for blocking (e.g. per node for linked-list-set)
are, if anything, probably cheaper than implicit Ref blocking, since
the latter allocs a blocking queue for *every* Ref cell.

May need to start laying out semantics to clarify blocking issues.

Does channel-only blocking alleviate monad problems?  Probably not.

Linked-list-set has problems for Loop/Thunk construct: needs access to
the input value to find the right nodes to edit.  True for any data
structure where the location of change depends on input.  Raises the
same problems as monadic bind: 

    Const(()) &> chan.send &> set.add
    
where the set is supposed to block when trying to add an
already-contained item.  A reasonable semantics is that we "search"
the channel for any message such that `set.add` can work.
    
## 6/28/2011

For sets, could make sense to have a blocking `add` operation: waits
until item is not currently in the set.

"How to use arrays of `BlockingCollection`s in a pipeline":
<http://msdn.microsoft.com/en-us/library/dd460715.aspx>

`BlockingCollection` can be used in *bounded* mode, wherein `add` can
block.

Monadic code over *nonblocking* reagents might be viable.

Blocking stack:

    bstack.push = rPop +> nbstack.push
    bstack.pop  = sPop <+> (nbstack.pop &> {case Some(x) => x})
    
Nonblocking, elimination stack:

    bstack.push = rPop <+> nbstack.push
    bstack.pop  = (sPop &> Some(-)) <+> nbstack.pop
    
Blocking queue, attempt 1:

    bq.enq = rDeq +> nbq.enq
    bq.deq = nbq.deq &> 
        {case Some(x) => x} <+>
	Const(()) &> sDeq
	
This is not as efficient as the dual data structure version.  Is that
version expressible?

Another question to keep in mind: what do you get with the original
idea of blockable `Ref` operations?  Most likely that's also not quite
as efficient as the dual data structure.

Good to remember: an uncontended CAS is (soon to be) inexpensive.

The design decision to *not* associate a waiter queue with every `Ref`
cell is motivated by the fact that, in anything more complex than a
queue or stack, this is generally insufficient (but users could easily
be misled into thinking otherwise).  We need adequate tools to deal
with cases like sets/bags.

Trying to avoid (via types) any situation where (1) without external
influence, a reagent is not enabled and (2) the reagent has no means
of visibly blocking until it becomes enabled.

## 6/27/2011

Reagent constructions:

    ref.read
    ref.cas
    chan.send
    &>
    <+>		(external choice)
    <?>		(internal choice)
    lift(function)
    lift(partial function)
    thunk
    retry
    
Nonblocking constructions:

    ref.read
    ref.cas
    lift(function)

Blocking constructions:

    chan.send
    lift(partial function)
    
    
Note that `lift(NeverDefined) ! x` *should* deadlock a thread.  While
a type system could potentially rule this out, it's probably not worth
it, and the semantics is quite natural; so call this a "blocking
construction".

Only allow catalyzing blocking constructions?

Important question: does "nonblocking" mean that *no* blocking code
need ever be provided (which might rule out the simple blocking
Treiber example)?  This goes back to the idea of forcing blocking to
go through channels.

---

The published dual data structures -- I think just stacks and queues
-- both have a special characteristic.  The blocking operation blocks
exactly when the data structure is "empty".  This means the required
consensus is very easy to encode: essentially swing the entire data
structure between empty and nonempty states.  Need to examine whether
the stack data structure uses stack ordering for waiting pops -- since
that would be pure coincidence, and in principle a dual data structure
can use any reasonable container for requests.

A challenge for the Chemistry Set: can we incorporate
sufficiently-expressive blocking constructs as to allow, for example,
a very simple treatment of sets-via-linked-lists where you can block
trying to remove a particular element, even though the set is
otherwise nonempty?  This is probably tied up with design questions
about blocking on `Ref` cells versus channels only.

Initial idea: add a true biased choice.  It would not be race-free,
but would guarantee that *at the linearization point* of the
right-sided choice, the left-sided choice was disabled.

## 6/25/2011

Could roll up the use of `Loop` in MSQueue into a version of `upd`.
But that's probably ugly.  Anyway, need to collect more examples
before creating new abstractions.

Would be nice to say: never blocks on a reagent yielded by a thunk.
There are a few problems with that: yielding a message send could not
be allowed, for example.  Might still be possible to set out type for
nonblocking reagents.  Raises interesting possibilities: a choice
between nonblocking and blocking reagents should not, in practice
block; should it's type be nonblocking?

One thing to watch out for: if "nonblocking" is taken in the
implementation as "do not install waiters", will be problematic if
message sending can, in any way, be considered nonblocking (e.g. as an
element of a choice as just suggested).

The other thing to recall: lifting nonblocking -> blocking is
problematic if (1) data flows from nonblocking -> blocking and (2)
blocking uses a partial map to filter this data.

It would be nice to have a good story here, even if a bit complicated.
A type system that ensured (1) correct-by-construction blocking logic
and (2) guaranteed nonblocking progress in some cases would be very
nice indeed.

From Fraser and Harris:

> Finally, notice that algorithms built over MCAS will not meet the
> goal of read-parallelism from Section 1.1. This is because MCAS must
> still perform CAS operations on addresses for which identical old
> and new values are supplied: These CAS operations force the
> address’s cache line to be held in exclusive mode on the processor
> executing the MCAS.
    
This doesn't *per se* apply to reagents: reads that truly don't need
to be atomic wrt any writes can be treated by thunking.

This strategy is related to the "early release" mechanism suggested by
Herlihy.

## 6/21/2011

Some STMs try to make pure reads essentially free as they occur.
However, must validate read at least by the commit point.  Moreover,
all writes must happen atomically.

For reagent version of MSQueue, for example, we can traverse list
tail, CASing the tail pointer, without any of it being logged, but
with the whole thing composable.

## 6/13/2011

Don't worry about allocation: very conservatively, given exponential
backoff, will retry less than 32 times/s.  Even if allocating 100
objects (at 16b/object, see <http://norvig.com/java-iaq.html>), that's
~50k for the first second, and <1.6k/s after that (but probably
would've gone into sleep(1) loop by then).

## 6/11/2011

Implementation concerns:

 - want to work with reagents in canonicalized form (DNF)
 - need to keep track of use of `retryLoop`
 - note, `retryLoop` is likely to cause per-retry allocation
   regardless
 - may be possible to re-use canonicalized representation
 
May not be reasonable semantics for combination of: `retryLoop`,
catalysis, blocking.  In particular, the reagent produced by the
loop's thunk may differ over time, so no single place to enroll as a
catalyst -- especially given that the memory read in computing the
reagent is inaccessible to the library.

## 6/10/2011

Allowing STM-like treatment of reference cells (so multiple
reads/write permitted) is probably not too costly: have to have "redo"
logs anyway, to track the kCAS clauses.  Can avoid allocation by
keeping thread-local space sufficient to hold these.

Reasonable Ref operations: `read`, `cas`.  Drop `write` in favor of
`cas`.

OTOH, have a nice defn of `upd`:

    r.upd(f) = arrowDo x -> {
      oldVal <- r.read <- ()
      (newVal, output) <- f <- (oldVal, x)
      () <- r.write <- newVal
      output
    }
    
(but `write` is easily definable with `read` and `cas`)

Difference between monadic bind and CML-style `guard`: with bind you
can write

    rChan >>= (someRef => someRef.read &> lift (ensure(p)))
    
where `p` is a predicate.  This means that, when blocking, must enroll
as blocker in *every* ref-cell currently visible in `rChan` messages,
as well as a blocker for `rChan` itself.

With guards, you can at best write

    rChan &> guard {
      val someRef = otherRef.read!
      someRef.read &> lift(ensure(p))
    }
    
but the point is that the computation of a reagent within the guard
cannot in any way depend on *which* value was read off the channel.
Note: this depends on giving guard type

    guard: (=> Reagent[A,B]) => Reagent[A,B]
    
Guards are also useful even if general monadic bind is provided: they
offer a way to say "I don't care about an atomicity guarantee for this
computation *of* a reagent", which is a key point for many nonblocking
algorithms.  For example, the Michael-Scott queue, when enqueuing,
first must discover the tail node.  This list traversal doesn't
require, and shouldn't be given, any atomicity guarantees by the
library.  But the result of the traversal is a computed reagent (to
put the new node in place).

## 6/9/2011

If a retryLoop is included in `upd` and `send` operations, it will not
be possible to, say, read a channel from some reference cell, attempt
to send on the channel, and on failure to CAS a message retry the
whole thing.  But, actually, the more interesting case there is
dealing with a *blocking* failure; and `upd` is a derived construct
anyway, so there's no real limitation there.

The problem with this definition of `upd`

    r.upd(f) = retryLoop (guard x => for {
      oldVal <- r.read 
      y <- f(oldVal, x) &> left r.cas(oldVal) &> pi2
    } yield y
    
is that the blocking caused by partiality of `f` does not connect to
the read of `r`.  In fact, if the initial read does not satisfy `f`,
this would block forever.

Better would be:

    r.upd(f) = retryLoop (arrowDo x -> {
      oldVal <- r.read <- ()
      newVal <- f <- (oldVal, x)
      r.cas <- (oldVal, newVal)
    })

This requires adding `read` as a reagent constructor.  Should be OK:
inconsistent reads, or reads that don't agree with a CAS attempt,
could be retried.

Alternatively, could do away with CAS, and just have `read` and
`write` constructs.  How would they layer?  Think of fan-out for
reads, fan-in for writes: each must be guaranteed to give the same
value, but reads are the system's responsibility, whereas writes are
the programmer's.  I.e., guarantee read consistency by retrying,
guarantee write consistency by throwing exception when violated.

Still need to think about:

 - blocking on a CAS (with the above, it would actually be blocking on
   read)
 - catalyzing `retryLoop` 
 
An interesting example, where both endpoints of a channel are used:

    // Counter-based semaphore in join calculus
    acq & avail(n) => n match { case S(S(m)) => avail(S(m))
                              | case S(Z) => empty }
    rel & avail(n) => avail(S(n))
    rel & empty    => avail(S(z))
    
    // As reagent
    rAcq &> rAvail &> { case S(S(m)) => S(m) } &> sAvail
    rAcq &> rAvail &> { case S(z) => () } &> sEmpty
    rRel &> rAvail &> S(_) &> sAvail
    rRel &> rEmpty &> ((_) => S(z)) &> sAvail

## 6/8/2011

Refine implementation of react:

 - take flattened choice
 - attempt doFn for each choice branch
 - for each doFn that does NOT yield a kCAS (i.e., polling fails),
   enroll reagent as blocker
 - if ALL choices have enrolled, after a final doFn attempt, block

Notice that blocking is prevented in any situation where a branch
repeatedly yields a viable kCAS.

Take CAS as primitive, upd as derived.  Introduce `loop` construct as
a way of handling failed CASes only (a partial function failure will
escape the loop).  Feels a bit like exception handling...

*Sidenote*: should think about exception handling in reagents, as
 well.
 
 ---
 
Two kinds of "failures" for reagents:
 
 - blocking, as caused by a guard or send
 - retryable, as caused by a failing CAS
 
`cas` succeeds or raises a retryable failure:
 
    Ref[A].cas: A => Reagent[A, ()]
    
`retryLoop` "catches" retryable failures and retries reagent at that
point; blocking failures pass through.  *NB*: retryable failures are
not re-raiseable, so never escape:
    
    retryLoop: Reagent[A,B] => Reagent[A,B]

We can encode `upd` using something like the following:
    
    r.upd(f) = retryLoop (guard x => for {
      oldVal <- r.read 
      y <- f(oldVal, x) &> left r.cas(oldVal) &> pi2
    } yield y

Note that the blocking behavior of `upd` now falls out of the
partiality semantics on lifted functions.

Some conjectured laws:

    retryLoop o retryLoop = retryLoop
    retryLoop a &> retryLoop b = retryLoop (a &> b)
    retryLoop a <+> retryLoop b = retryLoop (a <+> b)
    
An "unhandled" retryable failure could be understood to retry the
entire reagent.  I.e., there is an implicit `retryLoop` placed around
a reagent when it's asked to react.

How do we understand

    r.cas(...) <+> retryLoop a
    r.cas(...) &> retryLoop a
    
May be reasonable to either (1) statically distinguish between
pre-reagents (which have no retry) and reagents (which do) or (2)
implicitly add a retryLoop for choices.

Two variants of choice: one explores alternatives upon retryable
failure, the other only on blocking failure.
 
## 6/7/2011

Should be able to block on a conjunction of a `send` and an `upd`.
With looping definition of `upd`, this may not be possible.

"loop { ... }" is basically "nonBlocking { ... }"

## 6/5/2011

    r.upd(f) = r.read >>= ((dup &> left(f) &> r.cas) + r.upd(f))

    r.upd(f) = for {
      oldVal <- r.read 
      newVal = f(oldVal)
      r.cas(oldVal, newVal) + r.upd(f)
    }

Basic question here: what is the semantics of failure?  Failure can
happen through

 - a lifted partial function
 - a failing CAS (if CAS is exposed directly)
 - an empty dual endpoint
 
To handle the general optimistic concurrency pattern of
read-compute-CAS, we (1) need to support CAS directly and (2) need a
failing CAS to be able to retry the entire sequence.  On the other
hand, in some cases it's useful to perform a precomputation only once,
e.g. for allocation.

A related problem is blocking.  If arbitrary partial functions are
allowed, will be difficult or impossible to tell which reference cells
need to be monitored.  Even worse: monadic sequences could completely
obscure the reads/computation that led to a blocking situation.

What if all blocking were mediated through channels?  Seems to imply
reading choice as partially internal, rather than fully external.

*Try to make more use of choice*.

Looping retry is expressible through a combination of choice and CML
guards -- and this retains the CML guard semantics.  Since the idiom
can be neatly packaged as a higher-order combinator, there seems to be
little downside.

Another nice thing about loops-as-infinite-choice: nonblocking
algorithms needn't be explicitly distinguished.  They simply avoid the
blocking stage forever.  This does prevent any type-based
"nonblocking" assertion, but that was probably gone with guards/cas
loops anyway.

Need to think about interaction between recursive use of choice and
reagent flattening.  Consider: looping implementation of upd,
surrounded by use of &>.

## 6/3/2011

How is the transition from "doFn" to "blockFn" handled?  E.g. in the
elimination stack example?

Major problem with extra per-retry allocation: causes per-react data
(which will be freed on completion) to be promoted, effectively
leading to a (temporary) memory leak.

## 6/1/2011

Post-sync actions are definitely important for catalysts, but may also
be important for reagents when you want to expose a reagent with an
abstract post action.

Using a chain reaction as a catalyst...?

## 5/31/2011

CML-style doFn, blockFn internal interface could be a nice design for
incorporating blocking (and therefore catalysts).

Tension between CML-style guards (which fire once, ever) and guards
that fire on every retry.  The former can be used for allocation, the
latter for nonblocking search algorithms, etc.  Perhaps allow
programming both within the "chain reaction" monad?  Is this getting
too complicated?

The monadic interface is attractive since it makes explicit the
desired side effects of CML guards.  But how to specify the
linearization point?  One simple option: as the last computation in a
bind.    

Also, will this whole strategy work with "helping" algorithms?

Implicit coercions to emulate overloading on function types?

## 5/23/2011

Think of catalysts as blocked reagents which, when awoken, leave
behind another blocked copy.  So implementing catalysts ~ implementing
blocked reagents.

Signaling invariant on blocked reagents: always signaled when
consumed by nonowning thread, never signaled otherwise.

For guard, could take reagent-producing reagent (rather than
reagent-producing thunk).  Still ask for "pure" code.

## 5/20/2011

Reagents should implement apply, and subclass Function1.  That
recovers join-calculus style reading as function calls.  Is there a
reasonable unapply (perhaps for reagents of type Unit -> a)?

Could imagine a tighter library of nonblocking, noncatalyzing
reagents, which can then be used to implement the full library.  Is
this a useful separation for the API?  e.g. any nonblocking reagent is
guaranteed to be formally nonblocking?

## 5/19/2011

May be able to finesse the MSQueue by incorporating ideas from guard
and wrap in CML: allow pre and post synchronization.  "Chain
reactions"

Do channels need to be built in?  With conjunction, disjunction and
kCAS may have enough to build up channel actions as derived reagents.
One obstacle would be the blocking logic.  Another would be the
"fastpath" version.  And what about catalysts?  Finally, the
interaction with guards is wrong: the library needs to explore all
possible message combinations, looking for any possibility of
successful matching.

## 5/16/2011

Handling the MSQueue seems to require monadic code: must read from one
ref cell to determine another one to read.  Might be possible to
introduce direct mechanism for this, however.

Is monadic style compatible with catalysts?  Or with deadlock
avoidance?

    do x <- swap chan
       y <- read x
       guard y
       
Consider: chan contains message pointing to v, and *v = false.
Reactant then blocks on the queue for chan -- but suppose it logs
nothing regarding v.  If later *v = true without any change to chan, 
there is a lost wakeup.

An obvious solution is to log blocking calls on all possible
interacted-with objects.  This could run into problems with
consistency.  It is probably safe to log spurious blockers, but must
also ensure all nonspurious blocking is logged.

Could kCSS help?

Maybe there's a way to get a kind of monadic style, while limiting
information flow enough that logging can be avoided.

    hoUpd: ((A,B) => A) => Reagent[(Ref[A],B),Unit]
    
Dynamic synchronization is OK, as long as it can never be the cause of
blocking -- so asynchronous sends and unguarded updates are OK, but in
the latter case anything read from the Ref cell cannot be used later
in a guard.

This is fine for a nonblocking MSQueue, but if we want to block when
dequeuing from empty we're in trouble.  Does this obstacle represent
the need for a genuine insight (in the Scalable synchronous queue
paper, perhaps?) or is it incidental to the framework here?

The limitation to static topology seems to rule out any data structure
where, for example, unbounded search is needed to locate a reference
to CAS.  E.g. hand-over-hand sets, skiplists/maps, ...

## 5/13/2011

Arrow-like information flow seems to be at odds with a canonicalized,
flattened representation.

Nice example of (1) conjuction on send and (2) reactants: suppose you
have two nonblocking collections, each exposing an add and remove
reagent.  You can transfer from one to the other using a single
conjoined send reactant.

## 5/12/2011

There are STM-like consistency issues in conjoining state updates: two
disparate refs may be read at logically-distinct times, yielding an
inconsistent snapshot.  This should coincide with a failing kCAS
(modulo ABA), but could still pose a problem for guards, sequences,
etc.  In the worst case, could this result in deadlock?

This will depend on how blocking on a Ref is handled.

Another issue: must ensure every possible combination of messages is
tried (for handling guards).  When looking at messages from the same
channel, must try every permutation.

## 5/9/2011

What is the appropriate type discipline for reagents?  In the join
calculus, synchronous channels have two types: the type of values
carried over the channel, and the type of bodies of all chords
involving the channel.

Need sophisticated backtracking search to maximize concurrency: need
to track the provenance of each desired atom, so that when claiming
fails, can do partial rollback.  

Algebra of reagents:

    (a | b) & c  =  (a & c) | (b & c)
    (a & b) | c !=  (a | c) & (b | c)
    1 & a = a
    0 & a = 0
    0 | a = a
    1 | a != 1
    
Relation to linear logic?

What are the tradeoffs for using kCAS in implementing message
consumption protocol?  Would still need to provide backtracking
support, but could be simplified if no longer important to claim
messages in a particular order.  A benefit is lock-freedom of the
entire system.  In the Harris-Fraser-Pratt paper, the performance of
kCAS versus fine-grained locking on a simple benchmark was comparable
-- but only the latter gives nonblocking guarantees.

Inert versus ionized

## 5/5/2011

Liveness concerns: nonlinear channel use already poses a problem for
the scalable joins algorithm: order messages are grabbed is
potentially arbitrary.

A simple strategy for tie-breaking in this situation: thread local
counter + thread id.  Easy way to globally order messages within a
queue/bag/... without unduly synchronizing over it.  Order doesn't
match order of insertion but that's unproblematic.  Could even be
reasonable to *only* use this strategy, rather than assigning IDs to
channels.  Downside is that accessing such thread-local data is
probably slow.  (Could look into fiber-local data strategy as in
Manticore's runtime).  Another alternative -- use the memory address
of the reagent (can we get at this in the JVM?)

Polling/claiming is complicated by the fact that matching reagents may
themselves contain additional matching work.  One idea, for the sake
of efficiency, is to actually *publicize* within the stored reagent
the current claims (for the purpose of reagent helping).  That would
probably complicate the protocol, but could even provide nonblocking
guarantees.

Think of swap channels as fundamentally synchronous, with an option to
abandon a registered reagent's result for asynchrony.  That means that
a swap channel reagent cannot react without a symmetric reagent.
Therefore, invoking a swap channel causes a search for dual reagents,
which to react may require additional reagents.  

The situation feels a bit different for state.  Conceptually, there is
an implicit catalyst

    current(x)
    
which is updateable

    current(x) & update(x,y) => current(y)

State cells (need a better name) still require a bag to manage
registered update reagents, which may need to be woken.  

A way to bootstrap: have a notion of a one-off "base" reagent (need a
term for that, too).  That is, you register a fixed collection of
catalysts for it, and that's it.  This means no dynamic, threadsafe
bag/queue/... structure needed.
    
Need to think about nonlinear uses of a *state cell* in a reagent.



(Interesting tangential thought: rather than a no-op backoff, do
limited GC during backoff?)

## 5/3/2011

The need to have code attached to reagents stems primarily from their
use as catalysts.

something like

    reactant { myChan(3) }
    
is probably too verbose for message sending.

Another option: myChan(3) ! (for using as reactant) and myChan(3) !!
(for catalyst)

Then can treat myChan ! 3 as shorthand for myChan(3) !, which has a
nice connection to the actor syntax...

Can introduce traits to recover send-as-method structure of the join
calculus -- could be pretty nice.  (Actually, this would require
trait-based metaprogramming)

Is there actually any need for unbacked channels?  In fact, does the
Treiber stack need to be written in traditional join pattern style
(i.e. with catalysts)?

Trimmed code for push:

    def push(x: A) {
      val n = new Node(x, null)
      while (true) {
	n.next = head.get
	if (head compareAndSet (n.next, n)) return
      } 
    }
    
Using a reactant:

    def push(x: A) {
      val n = new Node(x, null)
      head.update(h => { n.next = h; n })
    }
    
Or, with less concern for over-allocation:

    def push(x: A) {
      head.update(x::_)
    }
    
For queue, could be interesting to compare M&S queue to one based on
2CAS.  Also, don't forget about flat combining option.

Order of claiming becomes much more complicated when *reagents* are
being claimed.  It's no longer enough to order the channels.

Liveness reasoning went: if I fail to claim a message, some other
thread must've succeeded -- so, globally, the system has progressed.
Try to find a similar scheme for reagents.

## 5/2/2011

Is choice commutative?  Is join?

Duality: send/recv, put/get.  Every reagent has a dual?  Note: the
dual of & is *not* |

When is coupling between communication and synchronization desirable?
Is it easier to think of coupling as the default, with asynchrony as
an option, or the other way around?

Could forget about receiving altogether: do everything in terms of
sync sending, with option to do async sending, and use join to match
up "real" sends with "requests" to receive.  This might yield PCML's
two queue representation.

*In particular, this could consolidate logic about looking for matching 
(dual) events.*

Must such choices be made up front?

Actually, the above doesn't quite make sense: it assumes the "join
patterns" are somehow of opposite polarity to message sending, which
is tantamount to having receives.  The "join" between two sides of a
channel must probably be left implicit.  However, the code for dealing
with senders and receivers can be made essentially symmetric.

The idea of a "nonbacked" channel fits in here somewhere: perhaps it's
missing one of the relevant queues?  Even nonbacked channels need a
place to store joins.  This includes state cells.

NB, need to ensure there is some way to guarantee FIFO ordering when
desired.

Handling nonlinear patterns could be problematic: need to communicate
global context (e.g., already have message n on this channel) to local
matching (e.g., looking for (another) message).

Could be OK for value sent on a channel to depend on values from other
channels -- just not *whether* we're sending on a channel.

With double queues per channel, in slow path where both sides are
reagents must CLAIM/CONSUME both a send and a receive event.

Relative to the joins implementation, supporting e.g. disjunctions
requires an additional layer of indirection between messages and their
statuses (because you want the status of the entire reagent).

Could CPS the matching process to assist stack allocation and type
safety?

## 4/28/2011

Think in CML terms about *composable* concurrency.  This entails, in
particular, exposing synchronous calls as reagents rather than
methods.  Perhaps can have the best of both worlds, if reactants can
be invoked via apply.

One of the unique aspects of the join calculus is the unification of
synchronous sends with function/method calls -- supporting, for
example, recursion.  Want to retain this?

Proper garbage collection on channels/reagents difficult without
runtime support.

Async attempt:

    reactant { someSync(x) -> SOME _ | always NONE }

Cancellable lock acquisitions:

    catalyst { acq & rel => . }
    reactant { acq | cancel => . }    // cancel is an arbitrary channel
    
Enforced lock protocol?

    catalyst { acq & status(0) => status(Thread.id)
               rel & status(tid) if tid == Thread.id => status(0)
	       rel & status(tid) if tid != Thread.id => status(tid); throw new Exception() }


 
## 4/27/2011

Every reagent is a potential participant in a reaction.  For channels,
the reaction depends on a duality between senders and receivers.  For
state, can think of the duality as between putters and getters.  In
either case, either the reaction is currently possible, or the
reactant must wait (either by spinning or by enrolling itself as a
blocked offer).

While this is like dual data structures, the representation isn't
quite as tight in general: the dual queue, for example, stores deq
reservation nodes and enq'ed nodes in the same linked list (though the
list will contain items of only one type at a time).  

Should we provide the analogous guarantee: messages of opposite
polarity may not exist in their respective queues simultaneously?  Do
we use a joint queue or separate queues?

Matching is definitely NP-hard for reagents: take formula in DNF,
combine with 

 (send(A) | recv(A)) & (send(B) | recv(B)) & ...

What does it mean to catalyze a state change?

## 4/26/2011

"reagent, reactant, catalyst" -- the Scala Chemistry Set

Condition variables -- can code auto/manual-reset-event.  What is the
wakeup process for setting a state?

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

If want combining two nonbacked messages to be a static error, need to
make a static distinction.

Distinguish between base "items" and combinators?  Make the former
extensible, the latter fixed?  But what about conjunctions, which (it
seems) need to be known to the queues into which they're inserted?

Possible "items" (thinking about names):

 - sending a synchronous message 
 - sending an asynchronous message
 - receiving a message
 - updating state

What does it mean to conjoin two asynchronous sends?  Presumably that
they can only be consumed jointly--possibly by a conjunction of
receives, possibly by separate receives.

A send is observed/consumed when it is received.

Need to watch out for items that e.g. contain a send and receive on
the same channel.

Message bags in scalable joins represent collection of sends, while
chord lists represent collection of receives.  Generalize, symmetrize
both.  How does that interact with unbacked channels?  What about
state cells?

The fact that state cells must track the patterns they're involved in,
but don't have a distinction between sending and receiving, suggests
that splitting between sending and receiving items could be a good
idea.  

Global ordering requires global knowledge -- but this is fine if we
treat & and | as known operators.

What about a doFn versus blockFn distinction?  Also, need a
distinction between `CLAIM`/`COMMIT` protocols and (_k_)`CAS`
protocols.  `retry` flag needs to fit in somewhere.

Cannot combine (_k_)`CAS` reagents willy-nilly: must be able to
transform all of them in an atomic step.  Could use implicits to
"find" right way to combine, a la typeclasses.  

There is also a `CLAIM`/`COMMIT` variant of state cells: use a lock.
Could this yield hand-over-hand locking?  Or at least help with
fine-grained locking?

Maybe it *does* make sense to allow multiple, unbacked channels in a
reagent, given that conjoined sending is also possible...  This is
connected to another interesting issue: when invoking a compound
reagent, the fast-path can actually avoid queuing *multiple* messages.

What happens when two dual catalysts are installed?  Infinite loop?
Is this OK?  Doesn't mix well with "sending-driven" model of scalable
joins (which here translates to "reactant-driven model") -- though
this may be OK, since we need to treat a catalyst like a reactant when
it is first installed.

Do we care about the distinction the join calculus makes between sync
and async *chords*?  I.e., can an async caller run the body?  If we
own the threading model as well, could view this as preemption.

What happens when an exception gets thrown?

## 4/25/2011

"Atomic reactors"?

Should synchrony be encoded?  What is the interaction between
(a)synchrony and "dual" data structures?

Can asynchronous messages be thought of as void-returning calls?  Or
is there a deeper difference?  If you think of the chord body as the
body of the method, then "async calls" aren't guaranteed to actually
run the method.

Should the code be driven in a functional style (i.e., closed world of
event combinators, easily allowing for normalization) or o-o style
(i.e., extensible combinator set, each combinator gets only a local
view)?

Conjoined sending could be one step toward STM-like atomicity: e.g.,
could possibly solve bank transfer problem this way.

Axes of variation

 - backing storage
 - synchrony
 - possible bodies -- e.g. for state cells, need to provide update

Intuitions

 - at most one item in pattern can avoid having a backing store
 - sync/async distinction makes sense even w/o backing store:
   either implementation works by repeated polling, but only 
   the former returns the result of the pattern body.
 - but sync/async distinction doesn't make sense for state cells.
   perhaps that's ok: treat them uniformly as async?

Joins has a deep asymmetry between sending and receiving.  The former
is "active" (and drives everything), the latter is purely reactive.
Join patterns are therefore specifications of atomic reactions.

CML has (more, but not perfect) symmetry between sending and
receiving.  Is this due purely to the synchronous sending model?  No,
because joins has both async and sync sending.

Allowing sending to be composed is important, esp when (1) sending is
synchronous and (2) disjunction is allowed, since that can encode
cancellable sending.

Can think of type indexes of join patterns as giving "provides" and
"requires".  Installable when provides <: requires.  

Treiber's:

    Push(x) & State(s) => State(x:s) 
    Pop() & State(x:s) => State(s);   SOME x
    Pop() & State(nil) => State(nil); NONE

With blocking Pop:

    Push(x) & Pop() => { return x; }

# General notes

Reevaluate

 - central location for receiving
 - tying together channel defns and patterns
 - semistatic patterns only?

Keep in mind

 - stealing is important (but may not be for lightweight threading)
 - lazy queueing is important
 - spinning is important, and probably needs to be dynamic

Is there a more general way to understand message resolution?

What liveness guarantees do you get?

 - especially for "special" patterns

Is there use for "single-shot" join patterns?

What about disjunctions of events?

Need to study all available STM implementations

Incorporate cancellation and timeouts

Need to go well beyond stacks and queues for this to be compelling
 - ideally, develop new scalable algorithms

Possible downside of non-Racket implementation: specifying pattern
matching on states/messages.  But Scala has extensible pattern
matching.

## LOCK-FREE EXCHANGER

Only a single thread can ever be blocked on the channel, because 
if multiple threads arrive, they will successfully exchange.

## PARALLEL CML

Is this dual to the join calculus?  E.g. the "wrap" operator is a bit
like setting up a chord body.  

I don't think it's really dual: hard to find a convincing "and" for
CML.  But certainly, they're related.  Esp. the parallel
implementation of CML -- can this be "reduced" to use of joins?
Almost certainly requires ability to add new join patterns to existing
channels.

One difference is the send/recv asymmetry in joins, which isn't
present in CML.

There's interesting sharing structure: a single event gets added to
multiple queues.  Some connection to the distinction between
single-shot and replicated join patterns?

Interesting optimization -- don't CLAIM last message -- CONSUME it.

TRANSACTIONAL EVENTS

Turn CML into a full-blown monad.  In particular, >>= allows
sequencing of events, with expected atomicity guarantees.  Joins are
likely expressible, but not in an efficient way.

Our joins implementation is not far away from message transactions.
Should this be incorporated?  If include "state channels" then have
full STM...

## ASYNCHRONOUS EVENTS

Introduces asynchrony to CML.

## COMBINING CML AND JOINS

type 'a event

    // SHARED events
    val always : 'a -> 'a event 
    val never  : 'a event
    val send   : 'a chan -> 'a -> () event
    val recv   : 'a chan -> 'a event

    // CML events
    val or  : 'a event -> 'b event -> ('a + 'b) event

    // Join events
    val and : 'a event -> 'b event -> ('a * 'b) event

    // Using events
    val sync  : 'a event -> 'a
    val serve : 'a event -> ()

## THE STACK EXAMPLE

Treiber's:

    Push(x) & State(s) => State(x:s) 
    Pop() & State(x:s) => State(s);   SOME x
    Pop() & State(nil) => State(nil); NONE

With blocking Pop:

    Push(x) & Pop() => { return x; }

What kinds of channels are Push and Pop?

 - clearly Pop must be sync
 - Push feels like it should be async

However, if Push can exit after *enqueuing* in the Push channel,
the stack is not linearizable!

Remember, it's elimination *backoff*.  So need to express that Push
request is only temporarily offered--and then withdrawn.  (Also raises
the question: for elimination backoff, is it better to use a bag or an
array?)

The more worrying question is: how can we tell from looking at the
join patterns whether we've written a linearizable implementation?  If
that's not easy to see, it's not clear we're accomplishing much.
Though, on the other hand, it may be possible to show that it still
eases verification.

Possible principle: linearizable methods should correspond to
synchronous channels?  Then returning entails firing of *some*
pattern, which means that the effect of the call cannot be to simply
enqueue a message...  NEED MORE EXAMPLES

Lurking here is the question: what responsibilities does the State(*)
component have wrt firing chords enabled by a state change?

Perhaps can finesse backoff part through whatever we use to specify
timeouts.

## LOCK-FREE QUEUE

    Deq() & Tail(xs) => Tail(xs); NONE
      when Head = xs and snd xs = null
    Deq() & Tail(x:xs) => Tail(xs); Deq()
      when Head = xs
    Deq() & Head(x:xs)

    Deq() & Head(x:nil) => Head(x:nil); NONE
    Deq() & Head(xs) & Tail(xs) 
    
