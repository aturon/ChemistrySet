import scala.concurrent.ops._
import scala.concurrent._

object TestUtil {
  // launch a list of threads in parallel, and wait till they all finish
  def spawnAndJoin(bodies: Seq[() => Unit]): Unit = 
    (for (body <- bodies;
	  done = new SyncVar[Unit];
	  _ = spawn { body(); done set () }) 
     yield done) foreach (_.get)  
}
