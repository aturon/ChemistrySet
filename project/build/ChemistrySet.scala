import sbt._
import Process._

class ChemistrySet(info: ProjectInfo) extends DefaultProject(info) {
  override def compileOptions = super.compileOptions ++ Seq(Optimize)

  //managed dependencies from built-in repos
  val instrumenter =
    "com.google.code.java-allocation-instrumenter" %
    "java-allocation-instrumenter" % "2.0"

  //managed dependencies from external repos
  val SonatypeSnapshotRepo =
    MavenRepository("Sonatype OSS Repo",
                    "http://oss.sonatype.org/content/repositories/snapshots")
  val caliper = "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT"

  override def fork =
    forkRun("-cp" :: (runClasspath +++ buildLibraryJar).absString :: Nil)
}
