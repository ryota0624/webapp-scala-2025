package com.example.authors

import zio.{ZLayer, ZNothing}
import zio.http.{Method, Status, string}
import zio.http.endpoint.AuthType.None
import zio.http.endpoint.Endpoint
import zio.schema.{DeriveSchema, Schema}

trait AuthorEndpoint:
  val authors: Endpoint[Unit, Unit, String, List[Author], None] =
    Endpoint(Method.GET / "authors")
      .out[List[Author]]
      .outError[String](Status.InternalServerError)

  val registerAuthor
      : Endpoint[Unit, AuthorRegistration, ZNothing, Author, None] =
    Endpoint(Method.POST / "authors")
      .in[AuthorRegistration]
      .out[Author]

  val getAuthor: Endpoint[String, String, ZNothing, Author, None] =
    Endpoint(Method.GET / "authors" / string("authorId"))
      .out[Author]

  val updateAuthor
      : Endpoint[String, (String, AuthorUpdate), ZNothing, Author, None] =
    Endpoint(Method.PUT / "authors" / string("authorId"))
      .in[AuthorUpdate]
      .out[Author]

  def publicEndpoints: Seq[Endpoint[_, _, _, _, None]] =
    authors :: registerAuthor :: getAuthor :: updateAuthor :: Nil

object AuthorEndpoint extends AuthorEndpoint:
  val live: ZLayer[Any, Nothing, AuthorEndpoint] =
    ZLayer.succeed(AuthorEndpoint)

case class AuthorRegistration(
    name: String,
    bio: Option[String]
)

case class Author(
    id: String,
    name: String,
    bio: Option[String]
)

case class AuthorUpdate(
    name: Option[String],
    bio: Option[String]
)

given Schema[Author] = DeriveSchema.gen[Author]
given Schema[AuthorRegistration] = DeriveSchema.gen[AuthorRegistration]
given Schema[AuthorUpdate] = DeriveSchema.gen[AuthorUpdate]
