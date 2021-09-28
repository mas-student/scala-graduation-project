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


object OrganizerDao {
  class MessageRow(tag: Tag) extends Table[(Int, String)](tag, "MESSAGES") {
    def id = column[Int]("MESSAGE_ID", O.PrimaryKey) // This is the primary key column
    def text = column[String]("MESSAGE_TEXT")
    def * = (id, text)
  }
  val messages = TableQuery[MessageRow]
  //
  class EventRow(tag: Tag) extends Table[(Int, Int, Int, String)](tag, "EVENTS") {
    def id = column[Int]("EVENT_ID", O.PrimaryKey) // This is the primary key column
    def chat_id = column[Int]("EVENT_CHAT_ID")
    def message_id = column[Int]("EVENT_MESSAGE_ID")
    def date = column[String]("EVENT_DATE")
    def * = (id, chat_id, message_id, date)
  }
  val events = TableQuery[EventRow]
}


case class OrganizerDao(db: H2Profile.backend.Database) {
  def addMessage(message: Message): IO[Unit] = for {
    msgOpt <- IO.fromFuture[Option[(Int, String)]]( IO.pure(db.run(OrganizerDao.messages.filter(_.id === message.message_id).result.headOption)))
    _ <- if (msgOpt.isEmpty) {
      IO.fromFuture[Unit](IO.pure(db.run(DBIO.seq(OrganizerDao.messages += (message.message_id, message.text)))))
    } else {
      IO.pure(())
    }
  } yield ()

  def addEvent(chat_id: Int, message: Message, date: String): IO[Unit] = for {
    eventOpt <- IO.fromFuture[Option[(Int, Int, Int, String)]]( IO.pure(db.run(OrganizerDao.events.filter(_.id === message.message_id).result.headOption)))
    _ <- if (eventOpt.isEmpty) {
      IO.fromFuture[Unit](IO.pure(db.run(DBIO.seq(OrganizerDao.events += (message.message_id, chat_id, message.message_id, date)))))
    } else {
      IO.pure(())
    }
  } yield ()

  def fetchEvents(chat_id: Int): IO[List[Event]] = for {
    eventsRows <- IO.fromFuture[Seq[(Int, Int, Int, String)]]( IO.pure(db.run(OrganizerDao.events.filter(_.chat_id === chat_id).result)))
    result = eventsRows.map(row => Event(row._4, "")).toList
  } yield result

  def fetchMessages: IO[List[Message]] = for {
    messageRows <- IO.fromFuture[Seq[(Int, String)]]( IO.pure(db.run(OrganizerDao.messages.result)))
    result = messageRows.map(row => Message(row._1, row._2, Chat(-1, ""))).toList
  } yield result
}