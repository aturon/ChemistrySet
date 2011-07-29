package chemistry

import scala.concurrent._
import scala.concurrent.ops._

object Threads {
  // launch a list of threads in parallel, and wait till they all finish
  def spawnAndJoin(bodies: Seq[() => Unit]): Unit = 
    (for (body <- bodies;
	  done = new SyncVar[Unit];
	  _ = spawn { body(); done set () }) 
     yield done) foreach (_.get)
}
