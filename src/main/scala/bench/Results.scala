// classes to store and tabulate benchmarking results

package chemistry

private case class Measurement(
  meanMillis: Double,
  coeffOfVar: Double,
  throughput: Double,
  iters: Int,
  trials: Int,
  cores: Int
) {
  def format = " %8.2f |".format(throughput)
  def formatN(compTo: Double) = 
    " %8.2f |".format(throughput / compTo)
}

private case class EntryResult(name: String, ms: Seq[Measurement]) {
  def format =
    "%16.16s |".format(name) ++
    ms.map(_.format).mkString ++
    "\n"

  def formatN(compTo: Double) = 
    "%16.16s |".format(name) ++
    ms.map(_.formatN(compTo)).mkString ++
    "  max cov: %5.2f  avg cov: %5.2f\n".format(
      ms.map(_.coeffOfVar).max,
      ms.map(_.coeffOfVar).sum / ms.length
    )
}

private case class BenchResult(name: String, work: Int, es: Seq[EntryResult]) {
  import config._

  private def hrule {
    println("-" * (18 + maxCores * 11))
  }
  def display {
    // raw throughput results
    hrule

    print("%10.10s %5d |".format(name, work))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")
    
    hrule
    es.map(_.format).foreach(print(_))

    // normalized results
    hrule
    es.map(_.formatN(es(0).ms(0).throughput)).foreach(print(_))
    hrule   

    println("")
    println("")
  }
}
