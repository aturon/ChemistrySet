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
  def format = " %4.1f |".format(throughput)
  def formatR = " %4.1f |".format(rawThroughput)
  def formatN(compTo: Double) = 
    " %4.1f |".format(rawThroughput / compTo)
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
    " mcov: %5.2f\n".format(
      ms.map(_.coeffOfVar).max
//      ms.map(_.coeffOfVar).sum / ms.length
    )
}

case class BenchResult(name: String, work: Int, es: Seq[EntryResult]) {
  import config._
  import java.io.FileWriter
  import java.io.PrintWriter

  val columns = es(0).ms.length

  private def hrule {
    println("-" * (18 + columns * 7))
  }
  def display {
    // header
    hrule
    print("%10.10s %5d |".format(name, work))
    (1 to columns).map(i => print(" %4d |".format(i)))
    println("")    
    hrule

    if (work > 0 && config.doTP) {
      // throughput results
      es.map(_.format).foreach(print(_))
      hrule
    }

    // raw throughput results
    es.map(_.formatR).foreach(print(_))
    hrule

    // normalized results
    es.map(_.formatN(es(0).ms(0).rawThroughput)).foreach(print(_))
    hrule   

    println("")
    println("")
  }

  private def reportTP(fname: String) = {
    val o = new PrintWriter(new FileWriter(
      "reports/tp." ++ name ++ "-" ++ work.toString ++ "." ++ fname))
    for (e <- es) 
      o.println(e.name ++ "," ++ e.ms.map(_.throughput).mkString(","))
    o.close
  }
  private def reportRTP(fname: String) {
    val o = new PrintWriter(new FileWriter(
      "reports/rtp." ++ name ++ "-" ++ work.toString ++ "." ++ fname))
    for (e <- es) 
      o.println(e.name ++ "," ++ e.ms.map(_.rawThroughput).mkString(","))
    o.close
  }

  def report(fname: String) {
    if (work > 0 && config.doTP) reportTP(fname)
    reportRTP(fname)
  }
}
