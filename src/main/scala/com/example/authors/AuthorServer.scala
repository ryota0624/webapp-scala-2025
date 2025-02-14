package com.example.authors

import com.example.authors.db.{DBConfig, MyConnection}
import com.example.authors.postgresql.{Queries, QueriesImpl}
import io.opentelemetry.api.OpenTelemetry
import zio.http.*
import zio.http.codec.*
import zio.http.codec.PathCodec.path
import zio.http.endpoint.*
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.{
  ConsoleLoggerConfig,
  LogFilter,
  LogFormat,
  consoleJsonLogger
}
import zio.telemetry.opentelemetry.baggage.Baggage
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.{URIO, *}

import java.sql.DriverManager

def queryLayer: ZLayer[DBConfig, Throwable, QueriesImpl] =
  ZLayer.fromZIO {
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
  override val bootstrap: ZLayer[ZIOAppArgs, Nothing, Unit] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(
      logConfig
    ) >+> Slf4jBridge.init(logConfig.toFilter)

  def run: ZIO[Any, Throwable, Nothing] =
    val scope = Scope.make
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
    server
      .provide(
        ZLayer.fromZIO(scope),
        OtelSdk.custom("AuthorServer"),
        DBConfig.fromContainerLive,
        queryLayer,
        telemetryLayer,
        AuthorRoute.live,
        AuthorEndpoint.live,
        Server.default
      )
      .ensuring(scope.map(_.close(Exit.succeed(()))))

end AuthorServer
