The Evt monad:

    data Evt a

Synchronization:

    sync : Evt a -> IO a

Synchronous channels:

    data SChan a
    newSchan : Evt (SChan a)
    sendEvt : SChan a -> a -> Evt ()
    recvEvt : SChan a -> Evt a

Combinators:

    thenEvt : Evt a -> (a -> Evt b) -> Evt b
    alwaysEvt : a -> Evt a
    chooseEvt : Evt a -> Evt a -> Evt a
    neverEvt : Evt a

Thread id:

    myThreadIdEvt : Evt ThreadID
    
Exceptions:

    throwEvt : Exn -> Evt a
    catchEvt : Evt a -> (Exn -> Evt a) -> Evt a
    


Note that SAT is encodable in transactional events: communication
matching is NP-hard.
