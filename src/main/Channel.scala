import java.util.concurrent.atomic._
import scala.annotation.tailrec

abstract class Claim {
  def commit
  def rollback
}

trait Reagent[A,B] {
  type Cursor
  def poll: Option[Cursor]
  def tryClaim(c: Cursor): Option[Claim]
}

class Channel {
  
}
