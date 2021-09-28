import cats.NonEmptyTraverse.ops.toAllNonEmptyTraverseOps
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.client.blaze._
import org.http4s.client._
import slick.jdbc.H2Profile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import java.util.concurrent._
import scala.collection.immutable
import scala.concurrent

import cats.effect._
import io.circe._
import io.circe.literal._
import org.http4s._
import org.http4s.dsl.io._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s.circe._


import slick.jdbc.H2Profile.api._
import org.h2.jdbc.JdbcSQLException
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import cats.implicits._


import organizer.OrganizerService


object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
       _ <- OrganizerService.run()
    } yield ExitCode.Success
  }
}
