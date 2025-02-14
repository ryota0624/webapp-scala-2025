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
      "dev.zio" %% "zio" % "2.1.15",
      "dev.zio" %% "zio-http" % "3.0.1",
      "org.postgresql" % "postgresql" % "42.7.5",
      "org.testcontainers" % "postgresql" % "1.20.4",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.8",
      "dev.zio" %% "zio-schema" % "1.6.1",
      "dev.zio" %% "zio-schema-json" % "1.6.1",
      "dev.zio" %% "zio-schema-derivation" % "1.6.1",
      "dev.zio" %% "zio-logging" % "2.4.0",
      "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.4.0",
      "dev.zio" %% "zio-opentelemetry" % "3.1.1",
      "dev.zio" %% "zio-opentelemetry-zio-logging" % "3.1.1",
      "io.opentelemetry" % "opentelemetry-api" % "1.47.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.47.0",
      "io.opentelemetry" % "opentelemetry-semconv" % "1.30.1-alpha",
      "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % "1.47.0",
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.8" % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.41.8" % Test
    )
  )

enablePlugins(ZioSbtCiPlugin)
