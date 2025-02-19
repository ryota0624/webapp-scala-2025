package com.example.authors

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.otlp.{
  OtlpJsonLoggingLogRecordExporter,
  OtlpJsonLoggingSpanExporter
}
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import zio.telemetry.opentelemetry
import zio.*
import zio.telemetry.opentelemetry.baggage.Baggage
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import java.io.File



def telemetryLayer: ZLayer[
  Any & OpenTelemetry,
  Nothing,
  ContextStorage & Unit & Tracing & Baggage
] =
  opentelemetry.OpenTelemetry.contextZIO >+>
    opentelemetry.OpenTelemetry.logging(instrumentationScopeName) >+>
    opentelemetry.OpenTelemetry.tracing(instrumentationScopeName) >+>
    opentelemetry.OpenTelemetry.baggage()

private val instrumentationScopeName =
  "zio.telemetry.opentelemetry.example.BackendApp"

object LoggerProvider {

  /** Prints to stdout in OTLP Json format
    */
  def stdout(resourceName: String): RIO[Scope, SdkLoggerProvider] =
    for {
      logRecordExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpJsonLoggingLogRecordExporter.create())
      )
      logRecordProcessor <- ZIO.fromAutoCloseable(
        ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter))
      )
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(
                Resource.create(
                  Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addLogRecordProcessor(logRecordProcessor)
              .build()
          )
        )
    } yield loggerProvider
}

object OtelSdk {

  import io.opentelemetry.api
  import io.opentelemetry.sdk.OpenTelemetrySdk
  import zio.*
  def custom(resourceName: String): TaskLayer[api.OpenTelemetry] =
    opentelemetry.OpenTelemetry.custom(
      for {
        loggerProvider <- LoggerProvider.stdout(resourceName)
        tracerProvider <- TracerProvider.stdout(resourceName)
        openTelemetry <- ZIO.fromAutoCloseable(
          ZIO.succeed(
            OpenTelemetrySdk
              .builder()
              .setLoggerProvider(loggerProvider)
              .setTracerProvider(tracerProvider)
              .build
          )
        )
      } yield openTelemetry
    )

}

object TracerProvider {
  import io.opentelemetry.sdk.resources.Resource

  /** Prints to stdout in OTLP Json format
    */
  def stdout(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpJsonLoggingSpanExporter.create())
      )
      spanProcessor <- ZIO.fromAutoCloseable(
        ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
      )
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(
                Resource.create(
                  Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider
}
