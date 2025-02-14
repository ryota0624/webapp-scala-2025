package com.example.authors.http

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import zio.{Scope, ZIO}
import zio.http.{Handler, Headers, Middleware, Routes}
import zio.logging.LogAnnotation
import zio.telemetry.opentelemetry
import zio.telemetry.opentelemetry.baggage.Baggage
import zio.telemetry.opentelemetry.baggage.propagation.BaggagePropagator
import zio.telemetry.opentelemetry.context.{
  ContextStorage,
  IncomingContextCarrier
}
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.tracing.propagation.TraceContextPropagator

import java.util.UUID

val assignTraceId: Middleware[Any] =
  new Middleware[Any] {
    override def apply[Env1 <: Any, Err](
        routes: Routes[Env1, Err]
    ): Routes[Env1, Err] =
      routes.transform { handler =>
        Handler.fromFunctionZIO { request =>
          handler(
            request
          ) @@ LogAnnotation.TraceId(
            request.headers
              .get("X-Trace-Id")
              .fold[UUID](UUID.randomUUID())(UUID.fromString)
          )
        }
      }
  }
end assignTraceId

def headersCarrier(initial: Headers): IncomingContextCarrier[Headers] =
  new IncomingContextCarrier[Headers] {
    override val kernel: Headers = initial

    override def getAllKeys(carrier: Headers): Iterable[String] =
      carrier.headers.map(_.headerName)

    override def getByKey(carrier: Headers, key: String): Option[String] =
      carrier.headers.get(key)
  }

val tracingMiddleware: Middleware[Tracing & Baggage] =
  new Middleware[Tracing & Baggage] {
    override def apply[Env1 <: Tracing & Baggage, Err](
        routes: Routes[Env1, Err]
    ): Routes[Env1, Err] =
      routes.transform { handler =>
        Handler.fromFunctionZIO { request =>
          val carrier = headersCarrier(request.headers)
          for {
            _ <- ZIO.serviceWith[Baggage] { baggage =>
              baggage
                .extract(BaggagePropagator.default, carrier)
            }
            response <- ZIO.serviceWithZIO[Tracing] { tracing =>
              handler(request)
                @@
                  tracing.aspects.extractSpan(
                    TraceContextPropagator.default,
                    carrier,
                    request.path.encode,
                    SpanKind.SERVER
                  )
            }
          } yield response
        }
      }
  }

end tracingMiddleware

val loggingMiddleware
    : Middleware[Tracing & OpenTelemetry & ContextStorage & Scope] =
  new Middleware[Tracing & OpenTelemetry & ContextStorage & Scope] {
    override def apply[
        Env1 <: Tracing & OpenTelemetry & ContextStorage & Scope,
        Err
    ](
        routes: Routes[Env1, Err]
    ): Routes[Env1, Err] =
      routes.transform { handler =>
        Handler.fromFunctionZIO { request =>
          for {
            _ <- opentelemetry.OpenTelemetry.logging("loggingMiddleware").build
            response <- handler(request)
          } yield response
        }
      }
  }

end loggingMiddleware
