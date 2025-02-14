addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.4.0-alpha.30")
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "0.4.0-alpha.30")
addSbtPlugin("dev.zio" % "zio-sbt-website"   % "0.4.0-alpha.30")

resolvers ++= Resolver.sonatypeOssRepos("public")
