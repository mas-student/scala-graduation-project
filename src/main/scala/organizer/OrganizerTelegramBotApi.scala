package organizer

import cats.NonEmptyTraverse.ops.toAllNonEmptyTraverseOps
import cats.effect.Fiber
import cats.effect._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import io.circe._
import io.circe.generic.auto._
import io.circe.literal._
import io.circe.syntax._
import java.util.concurrent.Executors
import java.util.concurrent._
import org.h2.jdbc.JdbcSQLException
import org.http4s.Uri
import org.http4s._
import org.http4s.circe._
import org.http4s.circe.jsonOf
import org.http4s.client._
import org.http4s.client.blaze._
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import scala.collection.immutable
import scala.concurrent
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import slick.jdbc.H2Profile
import slick.jdbc.H2Profile.api._


case class OrganizerTelegramBotApi(access_token: String, httpClient: Client[IO]) {
  val bareRoot = "https://api.telegram.org"

  def getUpdates(offset: Int): IO[GetUpdatesResponse] = {
    val root = s"$bareRoot/$access_token"
    implicit val myDecoder = jsonOf[IO, GetUpdatesResponse]

    def makeUrl(offset: Int): String = offset match {
      case -1 => s"$root/getUpdates?timeout=1000000"
      case offset => s"$root/getUpdates?timeout=1000000&offset=$offset"
    }

    for {
      resp <- httpClient.expect[GetUpdatesResponse](makeUrl(offset))
    } yield resp
  }

  def sendMessage(chat_id: Int, text: String): IO[Unit] = {
    val formatted_text = text.replace(" ", "%20")
    val url = s"$bareRoot/$access_token/sendMessage?chat_id=$chat_id&text=$formatted_text"
    for {
      _ <- httpClient.expect[String](url)
    } yield ()
  }
}
