import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {
  private val routes =
    Routes(
      Method.GET / Root -> handler(Response.text("Greetings at your service")),
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryParamToOrElse("name", "World")
        Response.text(s"Hello $name!")
      }
    )

  def run: ZIO[Any, Throwable, Nothing] = Server.serve(routes).provide(Server.default)
}
