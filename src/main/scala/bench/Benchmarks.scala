package chemistry.benchmarks

import annotation.tailrec
import com.google.caliper.Param
import com.google.caliper.SimpleBenchmark
import chemistry._

// a caliper benchmark is a class that extends
// com.google.caliper.Benchmark

// the SimpleScalaBenchmark trait does it and also adds some
// convenience functionality
class StackPushPop extends SimpleBenchmark {
  
  val const = new java.util.Date()

  override def setUp() {
    // set up all your benchmark data here
  }
  
  def timeArrayStack(reps: Int) {
    val s = new scala.collection.mutable.ArrayStack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const)
      s.pop()
    }
  }

  def timeJavaStack(reps: Int) {
    val s = new java.util.Stack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const)
      s.pop()
    }
  }

  def timeDirectTreiber(reps: Int) {
    val s = new Stack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const)
      s.pop()
    }
  }

  def timeReagent(reps: Int) {
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const)
      s.pop()
    }
  }
  
  override def tearDown() {
    // clean up after yourself if required
  }
  
}

