package com.example.authors.http

import zio.http.*
import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http.codec.CodecConfig
import zio.http.endpoint.{AuthType, Endpoint}
import zio.{NonEmptyChunk, ZIO, Zippable}

private def takeRight[A, B]: Zippable.Out[A, B, B] =
  new Zippable[A, B] {
    type Out = B
    def zip(left: A, right: B): B = right
  }

extension [PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth]
)
  def impl[Env](
      f: Input => ZIO[Env, Err, Output]
  ): Route[Env, Any] =
    val h = handler { (req: Request) =>
      for {
        input <- endpoint.input.decodeRequest(req)
        output <- f(input)
      } yield endpoint.output.encodeResponse(
        output,
        NonEmptyChunk(
          MediaTypeWithQFactor(MediaType.application.`json`, Some(1))
        ),
        CodecConfig.defaultConfig
      )
    }
    Route
      .route(endpoint.route)
      .apply(h)(
        takeRight
      )
