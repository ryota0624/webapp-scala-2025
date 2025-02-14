import zio.*
import zio.http.*
import zio.http.codec.PathCodec.path
import zio.http.codec.*
import zio.http.endpoint.*
import zio.http.endpoint.AuthType.None
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}

private object ServerEndpoint:
  val root: Endpoint[Unit, Unit, ZNothing, String, None] =
    Endpoint(Method.GET / Root).out[String]

  val greeting: Endpoint[Unit, String, ZNothing, String, None] =
    Endpoint(Method.GET / "greet")
      .out[String]
      .query[String](HttpCodec.query[String]("name"))

  def publicEndpoints: Seq[Endpoint[Unit, String, ZNothing, String, None]] =
    greeting :: Nil

end ServerEndpoint

object GreetingServer extends ZIOAppDefault:

  private val routes =
    val rootRoute =
      ServerEndpoint.root.implement { _ =>
        throw new RuntimeException("Not implemented")
      } handleErrorCauseZIO    (cause =>
        ZIO.logError(cause.prettyPrint).as(Response.error(Status.InternalServerError))
      )

    val greetingRoute =
      ServerEndpoint.greeting.implement { name =>
        ZIO.succeed(s"Hello $name!")
      }

    val openAPI = OpenAPIGen.fromEndpoints(
      title = "Endpoint Example",
      version = "1.0",
      ServerEndpoint.publicEndpoints
    )

    Routes(rootRoute, greetingRoute) ++ SwaggerUI.routes(
      "docs" / "openapi",
      openAPI
    )

  def run: ZIO[Any, Throwable, Nothing] =
    Server.serve(routes).provide(Server.default)
end GreetingServer
