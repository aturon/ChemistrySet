// classes to store and tabulate benchmarking results

package chemistry.bench

case class Measurement(
  meanMillis: Double,
  coeffOfVar: Double,
  throughput: Double,
  rawThroughput: Double,
  iters: Int,
  trials: Int,
  cores: Int
) {
  def format = " %8.2f |".format(throughput)
  def formatR = " %8.2f |".format(rawThroughput)
  def formatN(compTo: Double) = 
    " %8.2f |".format(throughput / compTo)
}

case class EntryResult(name: String, ms: Seq[Measurement]) {
  def format =
    "%16.16s |".format(name) ++
    ms.map(_.format).mkString ++
    "\n"

  def formatR =
    "%16.16s |".format(name ++ "(R)") ++
    ms.map(_.formatR).mkString ++
    "\n"

  def formatN(compTo: Double) = 
    "%16.16s |".format(name ++ "(N)") ++
    ms.map(_.formatN(compTo)).mkString ++
    "  max cov: %5.2f  avg cov: %5.2f\n".format(
      ms.map(_.coeffOfVar).max,
      ms.map(_.coeffOfVar).sum / ms.length
    )
}

case class BenchResult(name: String, work: Int, es: Seq[EntryResult]) {
  import config._
  import java.io.FileWriter
  import java.io.PrintWriter

  private def hrule {
    println("-" * (18 + maxCores * 11))
  }
  def display {
    // header
    hrule
    print("%10.10s %5d |".format(name, work))
    (1 to maxCores).map(i => print(" %8d |".format(i)))
    println("")    
    hrule

    // throughput results
    es.map(_.format).foreach(print(_))
    hrule

    // raw throughput results
    es.map(_.formatR).foreach(print(_))
    hrule

    // normalized results
    es.map(_.formatN(es(0).ms(0).throughput)).foreach(print(_))
    hrule   

    println("")
    println("")
  }
  def reportTP(fname: String) = {
    val o = new PrintWriter(new FileWriter(
      "reports/tp." ++ name ++ "-" ++ work.toString ++ "." ++ fname))
    for (e <- es) 
      o.println(e.name ++ "," ++ e.ms.map(_.throughput).mkString(","))
    o.close
  }
  def reportRTP(fname: String) {
    val o = new PrintWriter(new FileWriter(
      "reports/rtp." ++ name ++ "-" ++ work.toString ++ "." ++ fname))
    for (e <- es) 
      o.println(e.name ++ "," ++ e.ms.map(_.rawThroughput).mkString(","))
    o.close
  }
}
