import com.typesafe.sbt.SbtNativePackager.autoImport.packageName
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import de.gccc.jib.JibPlugin

// docker run -v $PWD:/work -v $HOME/.ivy2:/root/.ivy2  -v /var/run/docker.sock:/var/run/docker.sock  -it webapp-scala-sbt:3 bash

ThisBuild / version := "0.1.1-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"
resolvers += "jitpack" at "https://jitpack.io"

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

def directoryMap(f: File): Seq[(File, String)] = {
  def fileListInDir(parentPath: String, dir: File): Seq[(File, String)] = {
    dir
      .listFiles()
      .foldLeft[Seq[(File, String)]](Nil)((files, f) => {
        if (f.isDirectory) {
          files ++ fileListInDir(parentPath + "/" + f.getName, f)
        } else {
          files ++ (f -> (parentPath + "/" + f.getName) :: Nil)
        }
      })
  }

  if (f.isDirectory) {
    fileListInDir(f.getName, f)
  } else {
    Seq(f -> f.getName)
  }
}

lazy val root = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(JibPlugin)
  .settings(
    name := "webapp-scala",
    nativeImageGraalHome := file(
      s"${System.getenv("HOME")}/.sdkman/candidates/java/21.0.6-graal"
    ).toPath,
    nativeImageCommand := s"${System.getenv("HOME")}/.sdkman/candidates/java/21.0.6-graal/bin/native-image" :: Nil,
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
      "io.opentelemetry" % "opentelemetry-exporter-logging-otlp" % "1.47.0",
      "org.slf4j" % "slf4j-api" % "2.0.16",
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    ),
    Compile / mainClass := Some("com.example.authors.AuthorServer"),
    nativeImageOptions := Seq(
      "-Djdk.http.auth.tunneling.disabledSchemes=",
      "--install-exit-handlers",
      "--verbose",
      "--diagnostics-mode",
      "-O0",
//      "--no-fallback",
      "--enable-http",
      "--enable-url-protocols=http,https"
//      "--force-fallback"
    ),
    dockerEnvVars := imageEnv,
    dockerExposedPorts := Seq(8080),
    Docker / packageName := packageName.value + "-runtime",
    dockerCommands := (dockerCommands.value.filterNot {
      case ExecCmd("ENTRYPOINT" | "CMD", _*) =>
        true
      case Cmd("RUN", "id", _*) =>
        true
      case cmd => false
    } map {
      case Cmd("FROM", _, "AS", "mainstage") =>
        Cmd("FROM", "gcr.io/distroless/base")
      case cmd => cmd
    }) ++ Seq(
      Cmd("ENV", "PATH=/opt/docker/jre/bin:${PATH}")
    ),
    dockerBaseImage := "ghcr.io/graalvm/graalvm-community:23.0.2",
    Compile / compile := ((Compile / compile) dependsOn sqlcGenerate).value,
    assembly / assemblyJarName := packageName.value,
    jlinkIgnoreMissingDependency := JlinkIgnore.everything,
    jlinkModules := jlinkModules.value.filterNot(
      Seq(
        "java.naming",
        "java.desktop",
        "java.compiler",
        "java.management",
        "jdk.management",
//        "java.scripting",
        "java.security.jgss"
      ).contains
    ),
    jibBaseImage := "gcr.io/distroless/java-base-debian12:latest-arm64",
    jibPlatforms := Set(
      new com.google.cloud.tools.jib.api.buildplan.Platform("arm64", "linux")
    ),
    jibExtraMappings := {
      val jre = directoryMap(jlinkBuildImage.value)
      val jreToJibImage = jre map { case (file, name) =>
        file -> ("/opt/jre/" + name)
      }
      jreToJibImage
    },
    jibEntrypoint := Some(
      Seq(
        "/opt/jre/output/bin/java",
        "-XX:SharedClassListFile=/opt/jre/output/lib/classlist",
        "-cp",
        "/app/resources:/app/classes:/app/libs/*",
        "com.example.authors.AuthorServer"
      ).toList
    ),
    jibEnvironment := imageEnv ++ Map(
      "JAVA_HOME" -> "/opt/jre/output"
    ),
    jibUseCurrentTimestamp := true,

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
      case PathList(ps @ _*) if ps.last endsWith ".class" =>
        MergeStrategy.first
      case "reference.conf" => MergeStrategy.concat
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)

    },
    nativeImageAgentOutputDir := baseDirectory.value / "src" / "main" / "resources" / "META-INF" / "native-image" / "app",
    javaOptions += "-Dslf4j.provider=org.slf4j.simple.SimpleServiceProvider",
    Compile / run / fork := true
  )
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(JlinkPlugin)

val imageEnv = Map(
  "JAVA_TOOL_OPTIONS" -> "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xss256k"
)
enablePlugins(ZioSbtCiPlugin)
