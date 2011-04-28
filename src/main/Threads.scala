import scala.concurrent._
import scala.concurrent.ops._

object Threads {
  // launch a list of threads in parallel, and wait till they all finish
  def spawnAndJoin(bodies: List[() => Unit]) {
    val syncVars = 
      bodies map ((body) => {
	val done = new SyncVar[Unit]()
	spawn { body(); done set () }
	done})
    syncVars foreach ((_).get)
  }
}
