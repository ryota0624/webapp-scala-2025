ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

lazy val sqlcGenerate = taskKey[Unit]("run sqlc generate")
sqlcGenerate := {
  import scala.sys.process.Process

  val command = Seq("sqlc")
  val exitCode = Process("sqlc", "generate" :: Nil).!
  if (exitCode != 0) {
    throw new IllegalStateException(
      s"Command '${command.mkString(" ")}' failed with exit code $exitCode"
    )
  } else {
    println("sqlc generate completed successfully!")
  }
}

Compile / compile := ((Compile / compile) dependsOn sqlcGenerate).value
lazy val root = (project in file("."))
  .settings(
    name := "webapp-scala",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-http" % "3.0.1",
      "org.postgresql" % "postgresql" % "42.7.5",
      "org.testcontainers" % "postgresql" % "1.20.4",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.8",
      "dev.zio" %% "zio-schema" % "1.6.1",
      "dev.zio" %% "zio-schema-json" % "1.6.1",
      "dev.zio" %% "zio-schema-derivation" % "1.6.1",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.8" % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.8" % Test
    )
  )
