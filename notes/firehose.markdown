Need to read:

 - asynchronous events
 - more on lightweight threading (good blog fodder)
 - F# async workflows

To try coding:

 - stack/queue tests with concurrent dequeuing
 - kcas
 - flat combiners

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

For push:

    

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
    
