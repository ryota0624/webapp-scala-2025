package com.example.authors.db

import com.dimafeng.testcontainers.PostgreSQLContainer
import zio.{Scope, ZIO, ZLayer}

def containerLayer: ZLayer[Any & Scope, Throwable, PostgreSQLContainer] = {
  ZLayer.fromZIO(
    ZIO
      .acquireRelease({
        val container = PostgreSQLContainer()
        container.container.withInitScript("postgresql/schema.sql")

        ZIO.attempt(container.start()).as(container)
      })(c => ZIO.succeed(c.stop()))
  )
}
case class DBConfig(jdbcUrl: String, username: String, password: String)
object DBConfig:
  val fromContainerLive: ZLayer[Scope, Throwable, DBConfig] =
    containerLayer.project { container =>
      DBConfig(
        container.jdbcUrl,
        container.username,
        container.password
      )
    }

  val fakeContainerLive = ZLayer.succeed(
    DBConfig(
      "jdbc:postgresql://localhost:5432/authors",
      "authors",
      "password"
    )
  )
