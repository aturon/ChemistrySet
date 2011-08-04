# The quantum mechanics of parallelism

"Quantum mechanics differs significantly from classical mechanics in
its predictions when the scale of observations becomes comparable to
the atomic and sub-atomic scale, the so-called quantum realm."
([Wikipedia](http://en.wikipedia.org/wiki/Quantum_mechanics))

And so it is with thread-level parallelism.  Every thread performs
some local work before needing to coordinate with other threads.  When
the scale of that work shrinks to the scale of coordination
primitives&mdash;call it the quantum
realm&mdash;[Amdahl's law](http://en.wikipedia.org/wiki/Amdahl's_law) no
longer readily applies.

Unlike physical quantum phenomena, it's easy enough to observe this
breakdown in practice.

<!-- more -->

First, we need a way to simulate local
work.  Here's Scala code to generate _w_ random numbers, starting
from a fixed seed, using
[Marsaglia's _xorshift_ technique](http://www.jstatsoft.org/v08/i14/):

<pre class="brush: scala">
def noop(w: Int = 1) {
  var seed = 1;
  for (_ &lt;- 1 to w) {
    seed = seed ^ (seed &lt;&lt; 1)
    seed = seed ^ (seed &gt;&gt;&gt; 3)
    seed = seed ^ (seed &lt;&lt; 10)
  }
  seed
}
</pre>

<br/>
Whereas the [Hotspot JVM](http://en.wikipedia.org/wiki/HotSpot) would
optimize away an empty loop, it will dutifully execute this code,
generating a random number that we will then throw away.

Between our _w_ iterations of local work, we simulate coordination
between threads in a very simple way: by incrementing or decrementing
a counter, protected by a lock.  That is, every thread will execute
the following code:

<pre class="brush: scala">
// run 10,000,000 total iterations to shake out noise
// in practice, we run a long JVM warmup first
for (_ &lt;- 1 to 10000000 / numThreads) {
  lock
  count += 1
  unlock
  
  noop(w)
  
  lock
  count -= 1
  unlock 
  
  noop(w)
}
</pre>

<br/>
Amdahl's law predicts parallel speedup based on the percentage of work
that must be done sequentially.  The only work our simulation seems to
require to be done sequentially is the critical sections, which
consist of a read, an add, and a write&mdash;about as short as it
gets.

We can _estimate_ the percentage by running the code on just one thread,
both with and without the locking code.  Of course, this will vary
with _w_.  Running on an 8 core, 3.46Ghz Intel, we get

- 0.6% when _w_ is 1000
- 2.2% when _w_ is 250

So, when running sequentially, very little time is spent on the
lock/counter/unlock code.  As we decrease the amount _w_ of local work
from 1000 to 250, the proportion of time running coordination code
increases, but even at 2.2% Amdahl's law predicts good parallel
speedup at 8 cores.  Here's what we see in practice:

<img src="http://www.ccs.neu.edu/home/turon/w1000.png">

For _w_ = 1000, speedup is almost as predicted from Amdahl's law.  The
gap widens after 4 threads, which is easy to explain: the machine has
two physical processors with 4 cores each.  Communication between the
4 internal cores is cheaper (done via lower levels of cache) than
across the processors.  Thus the locking code is a bit more expensive
for 5 threads than for 4.

As we approach the "quantum realm", things get more complicated:

<img src="http://www.ccs.neu.edu/home/turon/w250.png">

At _w_ = 250, the falloff at 5 threads is much more pronounced, but
even at two threads performance significantly lags the prediction.
What gives?

Amdahl's law is all about sequential dependencies, where one step
really must finish before another begins.  In the limit, with infinite
parallelism _and no coordination cost_, the program will take as long
as its longest chain of sequential dependencies.  For our example,
that would be the time needed to execute 10,000,000 lock/count/unlock
pairs.

The problem is that, as the amount of work being done in parallel
enters the quantum realm, you can no longer ignore the cost of the
coordination primitives.  As mentioned above, cores communicate
through shared caches, or worse, main memory.  Memory bandwidth is
extremely limited.  Thus, as the number of active threads increase,
and the time between communications decreases, the effective cost of
communication substantially grows.  Here, lock overhead grows with lock contention.

Of course, this isn't a refutation of Amdahl's law.  It's just that
the notion of a "sequential dependency" becomes much more complicated
in the quantum realm: it can vary with the amount of parallelism!

Cache is king in the quantum realm, but having more of it won't save
us; we need to use it less.  Every atomic read or write to shared memory is a
precious use of bandwidth.  That's a big part of the reason that
practical
[lock-free algorithms](http://en.wikipedia.org/wiki/Non-blocking_algorithm)
scale so well.  Replacing our lock/counter/unlock code with

<pre class="brush: scala">
var current: Int
do {
  current = counter
} while (!counter.compareAndSet(current, current+1))
</pre>

<br/>
which requires only one successful
[atomic communication](http://en.wikipedia.org/wiki/Compare-and-swap)
yields much better results:

<img src="http://www.ccs.neu.edu/home/turon/w250b.png">

The estimated percentage of sequential work&mdash;and hence a naive
Amdahl-style prediction&mdash;is the same as before.  Only by
reasoning at the "quantum" level&mdash;cache coherence costs&mdash;can
we explain these observations.

For reference, the lock used was a test-and-set spinlock with
exponential backoff, which performed better than MCS, CLH, or
java.util.concurrent locks for this experiment.  More details to come
in a later post; stay tuned!
