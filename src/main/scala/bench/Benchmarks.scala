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
    val s = new HandStack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const)
      s.pop()
    }
  }

  def timeReagent(reps: Int) {
    val s = new TreiberStack[java.util.Date]()
    for (i <- 1 to reps) {
      s.push(const) ! ;
      s.pop !
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
      val s = new HandStack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const)
    }    
  }

  def timeReagentTreiber(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new TreiberStack[java.util.Date]()
      for (i <- 1 to divBy) s.push(const) !
    }    
  }
  
  override def tearDown() {
    // clean up after yourself if required
  }
  
}

class EnqDeq extends SimpleBenchmark {
  
  val const = new java.util.Date()

  override def setUp() {
    // set up all your benchmark data here
  }
  
  def timeScalaQueue(reps: Int) {
    val s = new scala.collection.mutable.Queue[java.util.Date]()
    for (i <- 1 to reps) {
      s.enqueue(const)
      s.dequeue()
    }
  }

  def timeJavaQueue(reps: Int) {
    val s = new java.util.LinkedList[java.util.Date]()
    for (i <- 1 to reps) {
      s.addFirst(const)
      s.removeFirst()
    }
  }

  def timeDirectMS(reps: Int) {
    val s = new HandQueue[java.util.Date]()
    for (i <- 1 to reps) {
      s.enqueue(const)
      s.dequeue
    }
  }

  def timeReagentMS(reps: Int) {
    val s = new MSQueue[java.util.Date]()
    for (i <- 1 to reps) {
      s.enq(const) ! ;
      s.deq !
    }
  }
  
  override def tearDown() {
    // clean up after yourself if required
  }
  
}

class Enq extends SimpleBenchmark {
  
  val const = new java.util.Date()
  val divBy = 1000

  override def setUp() {
    // set up all your benchmark data here
  }
  
  def timeScalaQueue(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new scala.collection.mutable.Queue[java.util.Date]()
      for (i <- 1 to divBy) s.enqueue(const)
    }    
  }

  def timeJavaQueue(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new java.util.LinkedList[java.util.Date]()
      for (i <- 1 to divBy) s.addFirst(const)
    }    
  }

  def timeDirectMS(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new HandQueue[java.util.Date]()
      for (i <- 1 to divBy) s.enqueue(const)
    }    
  }

  def timeReagentMS(reps: Int) {
    for (i <- 1 to reps / divBy) {
      val s = new MSQueue[java.util.Date]()
      for (i <- 1 to divBy) s.enq(const) !
    }    
  }
  
  override def tearDown() {
    // clean up after yourself if required
  }
  
}

