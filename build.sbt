name := "ChemistrySet"

version := "0.1"

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

scalacOptions += "-optimize"

//scalacOptions += "-Yinline"

//scalacOptions += "-Ydebug"

//scalacOptions += "-Ylog:inliner"

parallelExecution in Test := false

// disable publishing of main docs
publishArtifact in (Compile, packageDoc) := false

scalaVersion := "2.9.1"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "com.codahale" % "simplespec_2.9.0-1" % "0.4.1"
)

fork := true

javaOptions += "-server"

javaOptions += "-XX:+DoEscapeAnalysis"

javaOptions += "-Xmx2048M"