package com.example.authors

import com.example.authors.http.*
import com.example.authors.postgresql.Queries
import io.opentelemetry.api.OpenTelemetry
import zio.http.*
import zio.telemetry.opentelemetry
import zio.telemetry.opentelemetry.baggage.Baggage
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.{Scope, ZIO, ZLayer}
import zio.http.*

import java.util.UUID
import scala.language.postfixOps

//  --initialize-at-build-time=zio.logging.slf4j.bridge.ZioLogger --trace-object-instantiation=zio.logging.slf4j.bridge.ZioLoggerFactory --initialize-at-build-time=zio.logging.slf4j.bridge.LoggerData --initialize-at-build-time=zio.logging.slf4j.bridge.ZioLoggerFactory --initialize-at-run-time=io.netty.channel.ChannelInitializer
object AuthorRoute:
  type Env = Queries & Tracing
  val live =
    ZLayer {
      for {
        e <- ZIO.service[AuthorEndpoint]
      } yield AuthorRoute(e)
    }

class AuthorRoute(endpoint: AuthorEndpoint):
  val listAuthors: Route[Queries & Tracing, Any] =
    endpoint.authors.impl({ _ =>
      for {
        _ <- ZIO.serviceWithZIO[Tracing](_.addEvent("Listing authors Events"))
        _ <- ZIO.logInfo("Listing authors")
        authors <- ZIO
          .serviceWithZIO[Queries](service =>
            ZIO.attempt(service.listAuthors())
          ).mapBoth(_.getMessage, _.map(a => Author(a.id.toString, a.name, a.bio)))
      } yield authors
    })

  val getAuthor: Route[Tracing, Any] = endpoint.getAuthor.impl { authorId =>
    for {
      _ <- ZIO.serviceWithZIO[Tracing](_.addEvent("Get author Events"))
      _ <- ZIO.logInfo("get authors")
      resp <- ZIO.succeed(Author(authorId, "John Doe", scala.None))
    } yield resp
  }

  val updateAuthor: Route[Any, Any] = endpoint.updateAuthor.impl {
    (authorId, update) =>
      for {
        _ <- ZIO.log(s"Updating author $authorId")
      } yield {
        Author(authorId, update.name.getOrElse("John Doe"), update.bio)
      }
  }

  val registerAuthor =
    endpoint.registerAuthor.impl { registration =>
      for {
        _ <- ZIO.serviceWith[Queries](_.createAuthor(UUID.randomUUID(), registration.name, registration.bio))
        _ <- ZIO.log(s"Registering author ${registration.name}")
      } yield Author(
        UUID.randomUUID().toString,
        registration.name,
        registration.bio
      )
    }

  val publicRoutes: Routes[
    Tracing & OpenTelemetry & ContextStorage & Scope & Tracing & Baggage &
      Queries & Tracing,
    Any
  ] =
    Routes(
      listAuthors,
      getAuthor,
      updateAuthor,
      registerAuthor
    ) @@ assignTraceId @@ tracingMiddleware @@ loggingMiddleware
