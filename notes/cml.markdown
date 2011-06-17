<quote>
There is a fundamental conflict between selective communication and
abstraction. -- John Reppy
</quote>

<!-- more -->

Concurrent ML (CML) was introduced to resolve an apparent tension:  

- On the one hand, we want to program with synchronous message passing
protocols as abstract, first-class values.
- On the other hand, if we do this in the obvious way--by representing
them as functions--we have lost too much information.

In particular, if I give you two thunks `f` and `g` that
each internally execute a message-passing protocol, there is no
apparent way to form the *selective choice* `f + g` that offers both
protocols but executes only one of them.

CML's answer is to provide an abstract data type `event` representing
a synchronous protocol whose internal representation is effectively a
log of message-passing operations, interleaved with .

The obvious assumption to question here is the representation: we can
offer an abstract data type of synchronous protocols without using
functions as the internal representation.  




    channel : unit -> 'a chan

    choose : 'a event list -> 'a event

Selective communication.

    guard : (unit -> 'a event) -> 'a event

Given an event thunk, treat the computed event as the commit point in
selective communication, even if the thunk undergoes additional
communication.  

    wrap : ('a event * ('a -> 'b)) -> 'b event

Map a function over the result of an event.  The commit point is still
the original event, even if the mapped function performs additional
communication.

    wrapAbort : ('a event * (unit -> unit)) -> 'a event

Registers an appropriate side-effect to be performed when
synchronization was begun on an event, but did not reach the commit
point -- i.e., a compensating actions.

    sync : 'a event -> 'a

Synchronize on an event, yielding its result.

    always : 'a -> 'a event

    receive : 'a chan -> 'a event
    transmit : ('a chan * 'a) -> unit event
