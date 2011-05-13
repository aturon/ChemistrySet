package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

class Reagent[A,B](molecules: Molecule[A,B]*)
abstract class Molecule[A,B]
case class Bonded[A,B,C](m1: Molecule[A,B], m2: Molecule[B,C]) 
     extends Molecule[A,C]
abstract class Atom[A,B] extends Molecule[A,B] {
  // def isDualTo(a: Atom): Boolean
}

private class Pure[A,B](f: A => B) extends Atom[A,B]

private class First[A,B,C](a: Atom[A,B]) extends Atom[(A,C), (B,C)]

private class Guard[A](p: A => Boolean) extends Atom[A,A]

// val Always = new Guard(_ => false)

private class SwapChannel[A,B](var dual: SwapChannel[B,A]) extends Atom[A,B] {
  
}

class Ref[A](init: A) {
  def update(f: A => A) = new Atom[Unit,A] {
    
  }
  def update[B](f: (A,B) => A) = new Atom[B,A] {
    
  }
}
