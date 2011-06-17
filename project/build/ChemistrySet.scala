import sbt._

class ChemistrySet(info: ProjectInfo) extends DefaultProject(info) {
  override def compileOptions = super.compileOptions ++ Seq(Optimize)
}
