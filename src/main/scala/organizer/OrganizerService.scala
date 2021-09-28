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


case class Chat(id: Int, username: String)
case class Message(message_id: Int, text: String, chat: Chat)
case class Update(update_id: Int, message: Message)

case class Event(date: String, text: String)
case class GetUpdatesResponse(ok: Boolean, result: List[Update])


case class OrganizerService(api: OrganizerTelegramBotApi, router: OrganizerRouter) {

  def processUpdates(offset: Int): IO[Int] = {
    val result: IO[Int] = for {
      respEither <- api.getUpdates(offset).attempt
      last_update_id <- respEither match {
        case Left(_) => IO.pure(-1)
        case Right(resp) => router.processResponse(IO.pure(resp))
      }

    } yield last_update_id + 1

    result
  }

}


object OrganizerService {
  val BOT_ACCESS_TOKEN = ""

  def infiniteIO(body: Int => IO[Int])(cs: ContextShift[IO]): IO[Fiber[IO, Unit]] = {
    def exec(result: Int): IO[Int] = for {
      _ <- IO { println("exec {") }
      res <- body(result)
      _ <- IO { println("exec }") }
    } yield res

    def repeat(result: Int): IO[Unit] = exec(result).flatMap(res => repeat(res))

    repeat(-1).start(cs)
  }

   def run(): IO[Unit] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(global)
    implicit val timer: Timer[IO] = IO.timer(global)
    val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(5))
    val httpClient: Client[IO] = JavaNetClientBuilder[IO](blockingEC).create

    val db: H2Profile.backend.Database = Database.forConfig("h2mem1")

    val api = OrganizerTelegramBotApi(access_token=BOT_ACCESS_TOKEN, httpClient=httpClient)
    val dao = OrganizerDao(db)
    val router = OrganizerRouter(httpClient, api, dao)
    val serv: OrganizerService = OrganizerService(api, router)

    for {
      _ <- IO.fromFuture[Unit](IO.pure(db.run(DBIO.seq((OrganizerDao.messages.schema ++ OrganizerDao.events.schema).create))))

      fib <- infiniteIO(serv.processUpdates)(cs)

      _ <- fib.join
    } yield ()
   }
}
