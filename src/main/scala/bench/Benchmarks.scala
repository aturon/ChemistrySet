package chemistry.benchmarks

import annotation.tailrec
import com.google.caliper.Param
import com.google.caliper.SimpleBenchmark
import chemistry._

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

class StackPush extends SimpleBenchmark {
  
  val const = new java.util.Date()
  val divBy = 1000

  override def setUp() {
    // set up all your benchmark data here
  }
  
  def timeArrayStack(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new scala.collection.mutable.ArrayStack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const)
    }    
  }

  def timeJavaStack(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new java.util.Stack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const)
    }    
  }

  def timeDirectTreiber(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new Stack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const)
    }    
  }

  def timeReagent(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const)
    }    
  }
  
  override def tearDown() {
    // clean up after yourself if required
  }
  
}

