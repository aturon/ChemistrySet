Lightweight threads are a nice way to expose parallelism, especially
when it's unclear how to equally divide a problem in advance.  If
threads are cheap, programs can spawn thousands of them, corresponding
to small chunks of work.  The runtime system balances this work
between the available processors, using for example
[work-stealing deques](http://en.wikipedia.org/wiki/Cilk#Work-stealing).
Lightweight threads enable a number of fine-grained parallel
programming models, with
[futures](http://en.wikipedia.org/wiki/MultiLisp) being the most
prominent example.

What, in turn, enables lightweight threading?  Generally not the
kernel: trapping into kernel mode, dealing with full-blown OS
scheduling, allocating a per-thread stack _etc_ is too heavyweight.
User-mode threading can do much better.  User-mode thread creation and
synchronization is an order of magnitude faster than the kernel
equivalent, or at least was in
[1992](http://www.cs.washington.edu/homes/bershad/Papers/p53-anderson.pdf)
-- I don't have a more recent comparison.

Mitch Wand's famous paper,
[_Continuation-based multiprocessing_](ftp://ftp.ccs.neu.edu/pub/people/wand/papers/hosc-99.ps),
shows how to implement _kernel_ threads using call/cc.  But the same
technique can (and has, many times) been used for user-mode threading.

There's a catch, though.  If you want to spread your _N_ user threads
over _M_ cores, you'll need to multiplex them onto _M_ kernel threads
(i.e., [hybrid threading](http://goo.gl/TzeWK)).  If one of those user
threads calls a blocking operation, its supporting kernel thread is
blocked.  And if one of the kernel threads is preempted, so are all
the user threads associated with it.  Without additional kernel
support, user-mode threading libraries must employ resizable kernel
thread pools, and waste cycles managing them.  Unfortunately, kernel
support a la
[scheduler activations](http://www.cs.washington.edu/homes/bershad/Papers/p53-anderson.pdf)
appears to be DOA.
