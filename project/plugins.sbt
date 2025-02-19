addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.4.0-alpha.30")
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "0.4.0-alpha.30")
addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.4.0-alpha.30")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
libraryDependencies += "com.google.cloud.tools" % "jib-core" % "0.27.2"
resolvers ++= Resolver.sonatypeOssRepos("public")
resolvers += "jitpack" at "https://jitpack.io"
lazy val root = project.in(file(".")).dependsOn(jitpackPlugin)

lazy val jitpackPlugin = ProjectRef(
  build = uri("https://github.com/ryota0624/sbt-jib.git#v0.0.1"),
  project = "sbtJib"
)
