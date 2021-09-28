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


case class OrganizerRouter(httpClient: Client[IO], api: OrganizerTelegramBotApi, dao: OrganizerDao) {
  val TRUSTED_USERNAME = "martynov_a_s"

  def processCalendarMessage(message: Message): IO[Unit] = {
    val chat_id = message.chat.id

    for {
      events <- dao.fetchEvents(chat_id)

      lines = events.map(event => event.date + " - "+ event.text)
      text = lines.mkString("%0A") // "%20"
      _ <- api.sendMessage(chat_id, s"Event:%0A%0A$text")
      _ <- api.sendMessage(chat_id, s"Event:%0A%0A$text")
    } yield ()
  }


  def processMemoMessage(message: Message): IO[Unit] = {
    val chat_id = message.chat.id
    val msg = message.text.filter(c => ('a' to 'z').toList.contains(c))
    val text = s"accepted:$msg"
    val dates = message.text.split(" ").filter(w => List("today", "tomorrow", "сегодня", "завтра").contains(w)).toList

    for {
      _ <- IO { println("processMemoMessage") }

      r1 = dates.map(date => dao.addEvent(chat_id, message, date)).toList
      r2 <- r1.sequence

      _ <- api.sendMessage(chat_id, text)

    } yield ();
  }

  def processLinkMessage(message: Message): IO[Unit] = {
    val chat_id = message.chat.id
    val username = message.chat.username
    val url = message.text

    if (username != TRUSTED_USERNAME)
      IO { println(s"untrusted user = $username") }
    else {
      for {
        htmlEither <- httpClient.expect[String](message.text).attempt
        result <- htmlEither match {
          case Left(_) => IO { println(s"download failed from $url") }
          case Right(_) => IO { println("download success from $url") }
        }

      } yield result
    };
  }

  def processUpdate(update: Update): IO[Unit] = {
    val text = update.message.text

    for {
      _ <- update.message.text match {
        case "/calendar" => processCalendarMessage(update.message)
        case text => if (text.startsWith("https://")) {
          processLinkMessage(update.message)
        } else {
          processMemoMessage(update.message)
        }
      }
    } yield ()
  }

  def processResponse(response: IO[GetUpdatesResponse]): IO[Int] = {
    for {
      resp <- response

      count = resp.result.length

      _ <- IO { println(s"got $count Updates") }

      _ <- resp.result.map(update => processUpdate(update)).sequence

      last_update_id = if (resp.result.nonEmpty) resp.result.map(update => update.update_id).last else -1
    } yield last_update_id + 1
  }

}
