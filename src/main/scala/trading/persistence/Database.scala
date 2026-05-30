package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.Logger
import skunk.Session
import cats.effect.std.Console
import scala.concurrent.duration.*
import fs2.io.net.Network

final case class DbConfig(
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
    poolSize: Int = 16,
    connectTimeout: FiniteDuration = 5.seconds
)

object Database {

  /** Run Flyway against the JDBC URL derived from skunk config. We keep Flyway in JDBC-land so we don't have to
    * reinvent migration ordering on top of skunk.
    */
  def migrate[F[_]: Sync: Logger](cfg: DbConfig): F[Int] = {
    Sync[F]
      .blocking {
        val url = s"jdbc:postgresql://${cfg.host}:${cfg.port}/${cfg.database}"
        val fw = Flyway
          .configure()
          .dataSource(url, cfg.user, cfg.password)
          .locations("classpath:db/migrations")
          .baselineOnMigrate(true)
          .load()
        fw.migrate().migrationsExecuted
      }
      .flatTap(n => Logger[F].info(s"Flyway applied $n migration(s)"))
  }

  /** A skunk session pool. */
  def pool[F[_]: Async: Logger: Console: Network](
      cfg: DbConfig
  ): Resource[F, Resource[F, Session[F]]] = {
    Session
      .Builder[F]
      .pooled(
        max = cfg.poolSize
      )
      .evalTap(_ => Logger[F].info(s"Postgres pool ready (size=${cfg.poolSize})"))
  }

}
