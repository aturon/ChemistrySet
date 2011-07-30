name := "ChemistrySet"

version := "0.1"

scalacOptions += "-deprecation"

//scalacOptions += "-optimize"

scalaVersion := "2.9.0-1"

// resolvers += "Sonatype OSS Repo" at "http://oss.sonatype.org/content/repositories/snapshots"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
//  "com.google.code.java-allocation-instrumenter" % "java-allocation-instrumenter" % "2.0",
//  "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT",
//  "com.google.code.gson" % "gson" % "1.7.1",
  "com.codahale" % "simplespec_2.9.0-1" % "0.3.4"
)

fork := true

javaOptions += "-server"

javaOptions += "-XX:+DoEscapeAnalysis"

javaOptions += "-Xmx1024M"

//javaOptions += "-XX:+UseSerialGC"

//javaOptions += "-XX:CompileThreshold=10"

//javaOptions += "-Xprof"

//javaOptions += "-XX:+PrintGCTimeStamps"
//javaOptions += "-XX:+PrintGC"
//javaOptions += "-XX:+PrintCompilation"



//javaOptions ++= Seq("-XX:MaxInlineSize=100000", "-XX:FreqInlineSize=100000", "-XX:LoopUnrollLimit=100000", "-XX:InlineSmallCode=100000")

//javaOptions += "-XX:+PrintInlining"

//javaOptions += "-XX:CompileThreshold=1000"

//fullClasspath in Runtime <+= (baseDirectory) map { 
//  bd => Attributed.blank(bd / "lib" / "caliper-r316.jar") 
//}


//javaOptions in run += "-cp" 
//javaOptions in run += fullClasspath.absString