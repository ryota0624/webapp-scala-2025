import com.typesafe.sbt.SbtNativePackager.autoImport.packageName
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbtassembly.AssemblyPlugin.autoImport.assembly

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

// the bash scripts classpath only needs the fat jar

def commands(jarPath: String) = Seq(
  "mkdir classfiles",
  "chmod -R u+x,g+x classfiles",
  s"java -Xshare:off -XX:DumpLoadedClassList=classfiles/mn.lst -jar $jarPath warmup",
  s"java -Xshare:dump -XX:SharedClassListFile=classfiles/mn.lst -XX:SharedArchiveFile=classfiles/mn13.jsa -jar $jarPath"
//  s"java -XX:SharedArchiveFile=mn13.jsa -jar $jarPath" :: Nil
).map(cmd => ExecCmd("RUN", cmd.split(" ").toSeq *))

val assemblyJarPath = taskKey[String]("assemblyJarPath")

assemblyJarPath := {
  "lib/" + (assembly / assemblyJarName).value + ".jar"
}

lazy val root = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .settings(
    name := "webapp-scala",
    nativeImageGraalHome := file(
      "/Users/ryota.suzuki/.sdkman/candidates/java/21.0.6-graal"
    ).toPath,
    nativeImageCommand := s"/Users/ryota.suzuki/.sdkman/candidates/java/21.0.6-graal/bin/native-image" :: Nil,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.15",
      "org.postgresql" % "postgresql" % "42.7.5",
      "org.testcontainers" % "postgresql" % "1.20.4",
      "com.dimafeng" %% "testcontainers-scala" % "0.41.8",
      "dev.zio" %% "zio-http" % "3.0.1",
      "dev.zio" %% "zio-schema" % "1.6.1",
      "dev.zio" %% "zio-schema-json" % "1.6.1",
      "dev.zio" %% "zio-schema-derivation" % "1.6.1",
      "dev.zio" %% "zio-logging" % "2.4.0",
//      "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.4.0",
      "dev.zio" %% "zio-opentelemetry" % "3.1.1",
      "dev.zio" %% "zio-opentelemetry-zio-logging" % "3.1.1",
      "io.opentelemetry" % "opentelemetry-api" % "1.47.0",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.47.0",
      "io.opentelemetry" % "opentelemetry-semconv" % "1.30.1-alpha",
      "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % "1.47.0"
    ),
    Compile / mainClass := Some("com.example.authors.AuthorServer"),
    jibBaseImage := "ghcr.io/graalvm/graalvm-community:23.0.2",
    jibEnvironment := imageEnv,
    jibUseCurrentTimestamp := true,
    nativeImageOptions := Seq(
      "-Djdk.http.auth.tunneling.disabledSchemes=",
      "--install-exit-handlers",
      "--verbose",
      "--diagnostics-mode",
      "-O0",
      "--no-fallback",
      "--enable-http",
      "--enable-url-protocols=http,https"
    ),
    dockerEnvVars := imageEnv,
    dockerExposedPorts := Seq(8080),
    Docker / packageName := packageName.value + "-sbt-native-packager",
    dockerCommands := dockerCommands.value.filterNot {
      case ExecCmd("ENTRYPOINT" | "CMD", _*) =>
        true
      case cmd => false
    } ++
      Seq(
//        Cmd("ENV", "PATH=/opt/docker/jre/bin:${PATH}"),
        Cmd("USER", "root")
      ) ++
      commands(
        assemblyJarPath.value
      ) ++
      Seq(
        Cmd("USER", "1001:0"),
        ExecCmd(
          "ENTRYPOINT",
          jlinkBundledJvmLocation.value + "/bin/java"
        ),
        ExecCmd(
          "CMD",
          "-XX:SharedArchiveFile=classfiles/mn13.jsa",
          "-jar",
          assemblyJarPath.value
        )
      ),
    dockerBaseImage := "ghcr.io/graalvm/graalvm-community:23.0.2",
    Compile / compile := ((Compile / compile) dependsOn sqlcGenerate).value,
    assembly / assemblyJarName := packageName.value,
//    jlinkBuildImage := "ghcr.io/graalvm/graalvm-ce:21.0.0-java11",

    // removes all jar mappings in universal and appends the fat jar
    Universal / mappings := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (Universal / mappings).value
      val fatJar = (Compile / assembly).value
      // removing means filtering
      val filtered = universalMappings filter { case (file, name) =>
        !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName + ".jar"))
    },
    // the bash scripts classpath only needs the fat jar
    scriptClasspath := Seq(
      assemblyJarPath.value
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".properties" =>
        MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".xml"   => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".types" => MergeStrategy.first
      case PathList(ps @ _*) if ps.last endsWith ".class" => MergeStrategy.first
      case "reference.conf" => MergeStrategy.concat
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(JlinkPlugin)

val imageEnv = Map(
  "JAVA_TOOL_OPTIONS" -> "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k",
  "OTEL_EXPORTER_OTLP_ENDPOINT" -> "http://localhost:4317"
)
enablePlugins(ZioSbtCiPlugin)
