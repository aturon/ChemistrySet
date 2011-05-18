# Software transactional memory '77 - '06

I've spent the day perusing the first edition of
[Transactional Memory](http://www.morganclaypool.com/doi/abs/10.2200/S00070ED1V01Y200611CAC002)
trying to get to grips with the space of
[STM](http://en.wikipedia.org/wiki/Software_transactional_memory)
implementations.  Below I trace some of the important developments.
All implementations are free from deadlock and are linearizable:
transactions appear to take place atomically.  However, we can ask for
stronger liveness and safety properties.

### Liveness

An implementation is *obstruction-free* if a (finite) transaction is
guaranteed to succeed when executed in isolation, regardless of the
ambient state of memory.  In other words, a transaction can only fail
due to active interference from other threads.  It cannot be passively
obstructed by another thread that happens to hold a lock.  Thus a
thread descheduled by a page fault or preemption cannot block other
transactions.

An implementation is *lock-free* if *some* (finite) transaction is
always guaranteed to succeed, even if the scheduler is unfair.

A *blocking* implementation guarantees, at best, livelock freedom
given a fair scheduler.

### Safety

An additional safety property is *consistency*, meaning that the reads
performed within a transaction could, at all times, form an *atomic
snapshot* of memory.  This must always be true by the time a
transaction commits (to ensure transactional atomicity), but a
"doomed" transaction that has seen inconsistent values might keep
running for a time before being aborted.  If an implementation does
not guarantee consistency, transactional code cannot rely on
invariants that relate different words of memory to hold.  This
possibility raises the specter of transactional code spuriously
throwing an exception or going into a loop.

Each implementation below is classified according to its progress guarantee
(blocking, obstruction-free, lock-free) and its consistency guarantee.

<!--more-->

*Lomet '77* (blocking, consistent)

- proposes atomic procedures, along with an `await` clause for
conditional blocking
- sketches two-phase locking implementation.
 
*Herlihy, Moss ISCA '93*

- proposes hardware-based transactional memory
 
*Shavit, Touitou, PODC '95* (lock-free, consistent)

- coins "software transactional memory" 
- programmer must declare relevant memory addresses in advance
- uses pessimistic locking
- uses helping to achieve lock-freedom: aborted transactions "help"
  the conflicting transaction run to compleion
- doubles space usage: every word has a companion word tracking its
  owning transaction
- updates to memory are performed directly (since the memory is locked)

DSTM: *Herlihy, Luchangco, Moir, and Scherer, PODC '03*  (obstruction-free, consistent) <br/>
"dynamic" STM

- first "dynamic" STM (don't have to declare locations up front)
- uses deferred update (aka a "redo" log): updates take place on
  thread-local data and are installed only when the transaction
  commits
- object-level granularity
- two levels of indirection per object: `TMObject[A]` points to an
  immutable `Locator[A]` which contains `oldValue: A` and `newValue: A`, 
  along with a pointer to the owning transaction
- when acquiring an object for writing, clones object into `newValue`,
  which can then be modified freely
- transactions committed/aborted via CAS on a status flag
- offers modular contention management (determining which transaction
  to abort in a conflict)
- uses read validation for consistency: every additional read
  revalidates all prior reads
- no read/read conflicts
- space cost: a few words per transactional object, plus object clones
- time cost: *O((R+W)R)* where *R*, *W* are the read/write counts,
  plus time to clone objects

WSTM: *Harris and Fraser, OOPSLA '03* (obstruction-free, inconsistent) <br/>
"word" STM

- dynamic
- word-level granularity
- maintains fixed-size hashtable with ownership record for every
  touched word
- ownership record: either a transaction or a version number
- version numbers used to decrease chances of inconsistency, and in
  principle could be used to ensure consistency at similar cost to DSTM
- gain ownership by CASing record from version to transaction
- includes guards: call to `STMWait` aborts the transaction, but
  registers it in all touched words so that any change can reawaken it

OSTM: *Fraser, PhD '03* (lock-free, consistent) <br/>
"object" STM

- largely similar to DSTM, but uses helping to guarantee lock-freedom
- only one level of indirection

SXM: *Guerraoui, Herlihy, and Pochon, DISC '05* (obstruction-free, inconsistent)

- based on DSTM
- dynamic "polymorphic" choice of contention management

ASTM: *Marathe, Scherer, and Scott, DISC '05* (obstruction-free, consistent) <br/>
"adaptive" STM

- based on DSTM
- adaptively employs variety of conflict detection/resolution mechanisms
- only one level of indirection

RSTM:*"Marathe, Spear, Heriot, Acharya, Eisenstat, Scherer, Scott,
TRANSACT '06* (obstruction-free, consistent)<br/> 
"Rochester" STM

- based on DSTM
- one layer of indirection, uses tag bits
- manually manages memory
- fixed size "visible reader list" to cut down on validation costs
- visible readers do not need to revalidate: they will be aborted by
  any write, since they are visible to writers

TL: *Dice and Shavit, TRANSACT '06* (blocking, inconsistent) <br/>
"Transaction locking" STM

- combines locking and deferred-update: locks on commit (optimistic)
- granularity is flexible, but object-level seems to perform best
- deadlock avoidance using timeout (could also sort by address)

TL2: *Dice, Shalev, Shavit, DISC '06* (blocking, consistent) <br/>

- a global clock is used to track the linearization order of
  transactions
- adds versioned locking: an unlocked object contains the
  linearization number of the last transaction that updated it
- cheap read validation via version checking
- low cost read-only transactions (no logging, no synchronization)
- used by the Multiverse Java-STM library prior to 0.7 (as used in Scala/Akka)

McRT-STM: *Saha, Adl-Tabatabai, Hudson, Minh, Hertzberg, PPoPP '06* (blocking, inconsistent) <br/>
"Multicore runtime" STM

- optimistically modifies actual data, keeping an undo log
- uses versioned locking
- readers do not lock
- writers lock immediately
- thread scheduling done by runtime system, which is transaction-aware
- similar systems include Ennals’ STM system and Hindman and Grossman’s AtomicJava

BSTM: *Harris, Plesko, Shinnar, Tarditi, PLDI '06* (blocking, inconsistent) <br/>
"Bartok" STM

- similar to McRT-STM
- aggressively reduces the size of logs; integrates with garbage
  collection and specialized compiler support
