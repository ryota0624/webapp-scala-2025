package com.example.authors

import com.example.authors.db.{DBConfig, MyConnection}
import com.example.authors.postgresql.{Queries, QueriesImpl}
import zio.http.{Response, Server, Status}
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}
import zio.logging.{
  ConsoleLoggerConfig,
  LogFilter,
  LogFormat,
  consoleJsonLogger
}
import zio.{URIO, *}
import zio.http.codec.*

import java.sql.DriverManager

def queryLayer: ZLayer[DBConfig, Throwable, QueriesImpl] =
  ZLayer.fromZIO {
    DriverManager.registerDriver(new org.postgresql.Driver)
    for {
      config <- ZIO.service[DBConfig]
      connection <- ZIO.attempt(
        DriverManager.getConnection(
          config.jdbcUrl,
          config.username,
          config.password
        )
      )
    } yield QueriesImpl(MyConnection(connection))
  }

val appRoutes = {
  val openAPI =
    OpenAPIGen.fromEndpoints(
      title = "Author API",
      version = "1.0",
      AuthorEndpoint.publicEndpoints
    )
  import zio.http.codec.PathCodec.path
  for {
    authorRoute <- ZIO.service[AuthorRoute]
  } yield authorRoute.publicRoutes ++ SwaggerUI.routes(
    "docs" / "openapi",
    openAPI
  )
}

object AuthorServer extends ZIOAppDefault:
  private val logConfig = ConsoleLoggerConfig.default.copy(
    format = LogFormat.colored + LogFormat.allAnnotations,
    filter = LogFilter.LogLevelByNameConfig(LogLevel.Info)
  )
//  override val bootstrap: ZLayer[ZIOAppArgs, Nothing, Unit] =
//    Runtime.removeDefaultLoggers >>> consoleJsonLogger(
//      logConfig
//    ) // >+> Slf4jBridge.init(logConfig.toFilter)

  def run: ZIO[ZIOAppArgs, Throwable, Unit] =
    val scope = Scope.make
    val isWarmup = for {
      isWarmup <- getArgs
        .tap(args => ZIO.logInfo(s"args: ${args.mkString(" ")}"))
        .map { args => args.headOption.contains("warmup") }
    } yield isWarmup
    val server = for {
      routes <- appRoutes
      serve <- Server
        .serve(
          routes
            .handleErrorRequestCauseZIO({ (_, cause) =>
              ZIO
                .logError(s"Error while router handling: ${cause.prettyPrint}")
                .as(
                  Response.error(Status.InternalServerError)
                )
            })
        )
    } yield serve

    ZIO.ifZIO(isWarmup)(
      ZIO.logInfo("warmup").as(ZIO.unit),
      server.provide(
        ZLayer.fromZIO(scope),
        OtelSdk.custom("AuthorServer"),
        DBConfig.fromContainerLive,
        queryLayer,
        telemetryLayer,
        AuthorRoute.live,
        AuthorEndpoint.live,
        Server.default
      ).catchAllCause(cause =>
        ZIO.logError(s"Error while running server: ${cause.prettyPrint}")
      )
    )

end AuthorServer
