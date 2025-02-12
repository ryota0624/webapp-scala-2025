package com.example.authors

import zio.*
import zio.http.*
import zio.http.codec.PathCodec.path
import zio.http.codec.*
import zio.http.endpoint.*
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}

object AuthorServer extends ZIOAppDefault:
  private def openAPI =
    OpenAPIGen.fromEndpoints(
      title = "Author API",
      version = "1.0",
      AuthorEndpoint.publicEndpoints
    )
  private val routes = AuthorRoute(AuthorEndpoint).publicRoutes ++
    SwaggerUI.routes(
      "docs" / "openapi",
      openAPI
    )

  def run: ZIO[Any, Throwable, Nothing] =
    Server.serve(routes).provide(Server.default)
end AuthorServer
