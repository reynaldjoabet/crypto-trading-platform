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

  private val selectById: Query[UserId, User] = {
    sql"""SELECT id, email, display_name, role, tier, kyc_status, external_subject,
                 country_iso, created_at, updated_at
          FROM users WHERE id = $userIdC""".query(userCodec)
  }

  private val selectBySubject: Query[String, User] = {
    sql"""SELECT id, email, display_name, role, tier, kyc_status, external_subject,
                 country_iso, created_at, updated_at
          FROM users WHERE external_subject = $text""".query(userCodec)
  }

  // Upsert keyed on the OIDC subject (external_subject is UNIQUE): the first authenticated
  // request provisions the row; subsequent logins refresh the mutable profile fields. The
  // primary-key id is preserved on conflict so foreign keys (accounts, orders) stay stable.
  private val upsertUser: Command[User] = {
    sql"""INSERT INTO users
            (id, email, display_name, role, tier, kyc_status, external_subject, country_iso, created_at, updated_at)
          VALUES ($userCodec)
          ON CONFLICT (external_subject) DO UPDATE SET
            email        = EXCLUDED.email,
            display_name = EXCLUDED.display_name,
            role         = EXCLUDED.role,
            tier         = EXCLUDED.tier,
            kyc_status   = EXCLUDED.kyc_status,
            country_iso  = EXCLUDED.country_iso,
            updated_at   = EXCLUDED.updated_at""".command
  }

  def fromSession[F[_]: Concurrent](pool: Resource[F, Session[F]]): UserRepo[F] = {
    new UserRepo[F] {
      def find(id: UserId): F[Option[User]] = {
        pool.use(_.prepare(selectById).flatMap(_.option(id)))
      }
      def findBySubject(sub: String): F[Option[User]] = {
        pool.use(_.prepare(selectBySubject).flatMap(_.option(sub)))
      }
      def upsert(u: User): F[Unit] = {
        pool.use(_.prepare(upsertUser).flatMap(_.execute(u))).void
      }
    }
  }
}
