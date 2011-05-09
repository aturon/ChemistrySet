package chemistry

import java.util.concurrent.atomic._
import scala.annotation.tailrec

abstract class Claim {
  def commit
  def rollback
}

abstract class Atom {
  def dualTo(a: Atom): Boolean
}
object Atom {
  implicit def singletonMolecule(a: Atom): Molecule = Molecule(a)
  implicit def singletonReagent(a: Atom): Reagent = Reagent(a)
}

class SwapChannel {
  
}


// guards, wrapped functions?


// a molecule is conjunction of atoms
class Molecule(private[chemistry] val allOf: Seq[Atom]) {
  def and(m: Molecule) = new Molecule(allOf ++ m.allOf)

  private class Worklist {
    
  }

//  def catalyze
  def tryReact {
    // need to ensure a molecule never reacts with itself (if it
    // contains dual atoms)
    val work = new Worklist
  }
}

object Molecule {
  implicit def singletonReagent(m: Molecule): Reagent = Reagent(m)
  def apply(allOf: Atom*) = new Molecule(allOf)
}

// a reagent is a disjunction of molecules
class Reagent(private[chemistry] val oneOf: Seq[Molecule]) {
  def and(r: Reagent) = new Reagent(
    for (m1 <- oneOf; m2 <- r.oneOf) yield m1 and m2)
  def or(r: Reagent) = new Reagent(oneOf ++ r.oneOf)

//  def catalyze
  def react {
    // oneOf foreach 
    // need "worklist" of atoms

    // fast path: claim duals, then consume them
  }
}

object Reagent {
  def apply(oneOf: Molecule*) = new Reagent(oneOf)
}

// trait Reagent[A,B] {
//   type Cursor
//   def poll: Option[Cursor]
//   def tryClaim(c: Cursor): Option[Claim]
// }

