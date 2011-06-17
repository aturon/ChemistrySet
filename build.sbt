name := "ChemistrySet"

version := "0.1"

scalacOptions += "-deprecation"

scalacOptions += "-optimize"

scalaVersion := "2.9.0-1"

// resolvers += Resolver.url(
//   "Sonatype OSS Repo",
//   url("http://oss.sonatype.org/content/repositories/snapshots"))

resolvers += "Sonatype OSS Repo" at "http://oss.sonatype.org/content/repositories/snapshots"

resolvers += "repo.codahale.com" at "http://repo.codahale.com"

libraryDependencies ++= Seq(
  "com.google.code.java-allocation-instrumenter" % "java-allocation-instrumenter" % "2.0",
  "com.google.code.caliper" % "caliper" % "1.0-SNAPSHOT",
  "com.google.code.gson" % "gson" % "1.7.1",
  "com.codahale" % "simplespec_2.9.0-1" % "0.3.4"
)

fork in run := true

