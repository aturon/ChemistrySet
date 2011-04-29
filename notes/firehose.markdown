Need to read:

 - joins via pattern matching in scala
 - kcas
 - transactional events
 - asynchronous events
 - more of CML
 - more STM
 - more on lightweight threading (good blog fodder)
 - F# async workflows

To try coding:

 - stack/queue tests with concurrent dequeuing
 - kcas
 - joins+cml
 - flat combiners
 
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
    
