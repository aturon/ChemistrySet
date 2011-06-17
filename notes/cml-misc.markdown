Some additional CML operators are as follows.

    wait : thread_id -> unit event

Wait until given thread dies.

    waitUntil : time -> unit event
    timeOut : time -> unit event

Synchronize with a clock, either absolutely or relatively.

    syncOnInput : int -> unit event
    syncOnOutput : int -> unit event

Synchronize on the status of file descriptors.

    poll : 'a event -> 'a option

Nonblocking synchronization attempt.  (Note: seems to have semantics
like "if event is available, performs it"; see 7.5.5 of the diss)    

    select : 'a event list -> 'a

Just sync o choose.

    wrapHandler : ('a event * (exn -> 'a)) -> 'a event

Similar to wrap, but adds an exception handler.

    spawn : (unit -> unit) -> thread_id

    sameThread : (thread_id * thread_id) -> bool
    sameChannel : (channel * channel) -> bool

    accept : 'a chan -> a
    send : ('a chan * 'a) -> unit
