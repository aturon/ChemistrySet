java.util.concurrent.locks.LockSupport

Wraps unsafe.park and unpark.  Can think of this as a condition
variable associated with each thread.  Park waits, unpark signals.
