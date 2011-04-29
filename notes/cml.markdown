    spawn : (unit -> unit) -> thread_id

    channel : unit -> 'a chan

    sameThread : (thread_id * thread_id) -> bool
    sameChannel : (channel * channel) -> bool

    accept : 'a chan -> a
    send : ('a chan * 'a) -> unit

"There is a fundamental conflict between selective communication and
abstraction."

"If we make the operation abstract, we lose the flexibility of
selective communication; but if we expose the protocol to allow
selective communication, we lose the safety and ease of maintenance
provided by abstraction.  To resolve this conflict requires
introducing a new abstraction mechanism that preserves the synchronous
nature of the abstraction.  First-class synchronous operations provide
this new abstraction mechanism."

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

    wrapHandler : ('a event * (exn -> 'a)) -> 'a event

Similar to wrap, but adds an exception handler.

    wrapAbort : ('a event * (unit -> unit)) -> 'a event

Registers an appropriate side-effect to be performed when
synchronization was begun on an event, but did not reach the commit
point -- i.e., a compensating actions.

    sync : 'a event -> 'a

Synchronize on an event, yielding its result.

    select : 'a event list -> 'a

Just sync o choose.

    poll : 'a event -> 'a option

Nonblocking synchronization attempt.  (Note: seems to have semantics
like "if event is available, performs it"; see 7.5.5 of the diss)

    always : 'a -> 'a event

    receive : 'a chan -> 'a event
    transmit : ('a chan * 'a) -> unit event

    wait : thread_it -> unit event

Wait until given thread dies.

    waitUntil : time -> unit event
    timeOut : time -> unit event

Synchronize with a clock, either absolutely or relatively.

    syncOnInput : int -> unit event
    syncOnOutput : int -> unit event

Synchronize on the status of file descriptors.
