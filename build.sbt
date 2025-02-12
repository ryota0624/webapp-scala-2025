ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

lazy val root = (project in file("."))
  .settings(
    name := "webapp-scala",
    libraryDependencies += "dev.zio" %% "zio-http" % "3.0.1"
  )

