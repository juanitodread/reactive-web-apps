name := "reactive-streams"

version := "1.0"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  ws,
  "com.ning" % "async-http-client" % "1.9.29"
)

routesGenerator := InjectedRoutesGenerator
