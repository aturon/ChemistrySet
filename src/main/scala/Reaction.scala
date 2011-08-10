// Internal representation of queued up actions making up a potential
// reaction.

package chemistry

/*
sealed private abstract class LogEntry
private case class CASLog[A](r: AtomicReference[A], ov: A, nv: A) 
	     extends LogEntry
*/

private sealed class Reaction(private val postCommits: List[Unit => Unit]) {
  def tryCommit: Boolean = {
    // eventually will do kCAS here
    postCommits.foreach(f => f())
    true
  }

  def +:(postCommit: => Unit): Reaction =
    new Reaction(((u: Unit) => postCommit) +: postCommits)
}

private object Inert extends Reaction(List())
