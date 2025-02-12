package com.example.authors

import zio.ZIO
import zio.http.{Route, Routes}

import java.util.UUID

class AuthorRoute(endpoint: AuthorEndpoint):
  def listAuthors: Route[Any, Nothing] = endpoint.authors.implement { _ =>
    ZIO.succeed(List(Author(UUID.randomUUID().toString, "John Doe", None)))
  }

  def getAuthor: Route[Any, Nothing] = endpoint.getAuthor.implement {
    authorId =>
      ZIO.succeed(Author(authorId, "John Doe", None))
  }

  def updateAuthor: Route[Any, Nothing] = endpoint.updateAuthor.implement {
    (authorId, update) =>
      ZIO.succeed(
        Author(authorId, update.name.getOrElse("John Doe"), update.bio)
      )
  }

  def registerAuthor: Route[Any, Nothing] =
    endpoint.registerAuthor.implement { registration =>
      ZIO.succeed(
        Author(UUID.randomUUID().toString, registration.name, registration.bio)
      )
    }

  def publicRoutes: Routes[Any, Nothing] =
    Routes(listAuthors, getAuthor, updateAuthor, registerAuthor)
