package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import trading.domain.ids.UserId
import trading.domain.user.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait UserRepo[F[_]] {
  def find(id: UserId): F[Option[User]]
  def findBySubject(sub: String): F[Option[User]]
  def upsert(u: User): F[Unit]
}

object UserRepo {
  import Codecs.*

  private val Q_BY_ID: Query[UserId, User] = {
    sql"""SELECT id, email, display_name, role, tier, kyc_status, external_subject,
                 country_iso, created_at, updated_at
          FROM users WHERE id = $userIdC""".query(userCodec)
  }

  private val Q_BY_SUB: Query[String, User] = {
    sql"""SELECT id, email, display_name, role, tier, kyc_status, external_subject,
                 country_iso, created_at, updated_at
          FROM users WHERE external_subject = $text""".query(userCodec)
  }

  // Note: UPSERT with complex User type is tricky with Skunk's codec system
  // Defining as a stub for now - user upsert would need individual field handling

  def fromSession[F[_]: Concurrent](pool: Resource[F, Session[F]]): UserRepo[F] = {
    new UserRepo[F] {
      def find(id: UserId): F[Option[User]] = {
        pool.use(_.prepare(Q_BY_ID).flatMap(_.option(id)))
      }
      def findBySubject(sub: String): F[Option[User]] = {
        pool.use(_.prepare(Q_BY_SUB).flatMap(_.option(sub)))
      }
      def upsert(u: User): F[Unit] = {
        // TODO: Implement user upsert - requires handling complex codec composition
        Concurrent[F].unit
      }
    }
  }
}
