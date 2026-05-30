package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import trading.domain.accounts.*
import trading.domain.ids.*
import trading.domain.money.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import io.github.iltotore.iron.*

trait AccountRepo[F[_]] {
  def find(id: AccountId): F[Option[Account]]
  def findOrCreate(userId: Option[UserId], kind: AccountKind, ccy: CurrencyCode): F[Account]
  def post(entries: List[LedgerEntry]): F[Unit]
  def balance(id: AccountId, ccy: CurrencyCode): F[Balance]
}

object AccountRepo {
  import Codecs.*
  private val Q_BY_ID: Query[AccountId, Account] = {
    sql"""SELECT id, user_id, kind, currency, created_at
          FROM accounts WHERE id = $accountIdC""".query(accountCodec)
  }

  private val Q_BY_TRIPLE: Query[(Option[UserId], AccountKind, CurrencyCode), Account] = {
    sql"""SELECT id, user_id, kind, currency, created_at
          FROM accounts
          WHERE user_id IS NOT DISTINCT FROM ${userIdC.opt}
            AND kind = $accountKindC
            AND currency = $currencyC""".query(accountCodec)
  }

  private val C_INSERT
      : Command[(AccountId, Option[UserId], AccountKind, trading.domain.money.CurrencyCode, java.time.Instant)] = {
    sql"""INSERT INTO accounts (id, user_id, kind, currency, created_at)
          VALUES ($accountIdC, ${userIdC.opt}, $accountKindC, $currencyC, $instantTz)""".command
  }

  private val C_POST: Command[LedgerEntry] = {
    sql"""INSERT INTO ledger_entries
            (id, account_id, direction, amount, currency, reference, posted_at)
          VALUES ($entryCodec)""".command
  }

  // Available balance = sum(credits) - sum(debits) for the (account, currency) pair.
  private val Q_BALANCE: Query[(AccountId, CurrencyCode), BigDecimal] = {
    sql"""SELECT COALESCE(SUM(
              CASE direction WHEN 'Credit' THEN amount ELSE -amount END
            ), 0)::numeric
          FROM ledger_entries
          WHERE account_id = $accountIdC AND currency = $currencyC""".query(numeric)
  }

  def fromSession[F[_]: Concurrent: Clock](pool: Resource[F, Session[F]])(using
      cats.effect.std.UUIDGen[F]
  ): AccountRepo[F] = {
    new AccountRepo[F] {
      def find(id: AccountId): F[Option[Account]] = {
        pool.use(_.prepare(Q_BY_ID).flatMap(_.option(id)))
      }

      def findOrCreate(userId: Option[UserId], kind: AccountKind, ccy: CurrencyCode): F[Account] = {
        pool.use { s =>
          s.prepare(Q_BY_TRIPLE).flatMap(_.option((userId, kind, ccy))).flatMap {
            case Some(a) => a.pure[F]
            case None    => {
              for {
                now <- Clock[F].realTimeInstant
                id <- cats.effect.std.UUIDGen[F].randomUUID
                acc = Account(AccountId(id), userId, kind, ccy, now)
                _ <- s.prepare(C_INSERT).flatMap(_.execute((acc.id, acc.userId, acc.kind, acc.currency, acc.createdAt)))
              } yield acc
            }
          }
        }
      }

      def post(entries: List[LedgerEntry]): F[Unit] = {
        // Single transaction: all or none. Skunk doesn't have a `transactional` combinator on
        // bare Session — we use `transaction.use` to ensure rollback on any failure.
        pool.use { s =>
          s.transaction.use { _ =>
            s.prepare(C_POST).flatMap(pc => entries.traverse_(pc.execute))
          }
        }
      }

      def balance(id: AccountId, ccy: CurrencyCode): F[Balance] = {
        pool.use(_.prepare(Q_BALANCE).flatMap(_.unique((id, ccy)))).map { net =>
          Balance(id, ccy, available = net, pending = BigDecimal(0))
        }
      }
    }
  }
}
