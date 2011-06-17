import com.google.caliper.Param

// a caliper benchmark is a class that extends com.google.caliper.Benchmark
// the SimpleScalaBenchmark trait does it and also adds some convenience functionality
class BenchQueue extends SimpleScalaBenchmark {
  @Param(Array("1","2"))
  val threads: Int = 0

  
}
