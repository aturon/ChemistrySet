# Chemistry Set

The Scala Chemistry Set is an experiment in generalizing both the join
calculus and CML into a single concurrency/parallelism library,
incorporating ideas from scalable parallel implementations of each
([CML](http://people.cs.uchicago.edu/~jhr/papers/2009/icfp-parallel-cml.pdf),
[Joins](http://www.ccs.neu.edu/home/turon/scalable-joins.pdf)).

The main library code -- such as it is -- can be found in
`src/main/scala/Core.scala`, which also includes some example uses of
the library.  The code is in substantial flux so is not
well-documented at the moment.

A disjointed log of design decisions and other thoughts can be found
in `notes/firehose.markdown`.

A more coherent layout and implementation is coming soon, after the
core implementation has settled.
