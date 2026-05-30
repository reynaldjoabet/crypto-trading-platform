package trading.accounts

import cats.effect.*
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.accounts.*
import trading.domain.ids.*
import trading.domain.money.*
import trading.persistence.AccountRepo

trait AccountService[F[_]] {
  def deposit(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit]
  def withdraw(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit]
  def reserve(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit]
  def release(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit]
  def balance(userId: UserId, ccy: CurrencyCode): F[Balance]
}

object AccountService {

  def make[F[_]: Sync: UUIDGen](repo: AccountRepo[F]): AccountService[F] = {
    new AccountService[F] {
      def deposit(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit] = {
        for {
          cash <- repo.findOrCreate(Some(userId), AccountKind.Cash, ccy)
          omni <- repo.findOrCreate(None, AccountKind.ExchangeOmnibus, ccy)
          entries <- pair(cash.id, omni.id, amt, ccy, reference, customerCredits = true)
          _ <- repo.post(entries)
        } yield ()
      }

      def withdraw(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit] = {
        for {
          cash <- repo.findOrCreate(Some(userId), AccountKind.Cash, ccy)
          bal <- repo.balance(cash.id, ccy)
          _ <- {
            if bal.available >= amt.value then Sync[F].unit
            else Sync[F].raiseError[Unit](AppError.InsufficientFunds(amt.value, bal.available))
          }
          omni <- repo.findOrCreate(None, AccountKind.ExchangeOmnibus, ccy)
          entries <- pair(cash.id, omni.id, amt, ccy, reference, customerCredits = false)
          _ <- repo.post(entries)
        } yield ()
      }

      def reserve(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit] = {
        for {
          cash <- repo.findOrCreate(Some(userId), AccountKind.Cash, ccy)
          bal <- repo.balance(cash.id, ccy)
          _ <- {
            if bal.available >= amt.value then Sync[F].unit
            else Sync[F].raiseError[Unit](AppError.InsufficientFunds(amt.value, bal.available))
          }
          trading <- repo.findOrCreate(Some(userId), AccountKind.Trading, ccy)
          entries <- pair(cash.id, trading.id, amt, ccy, reference, customerCredits = false)
          _ <- repo.post(entries)
        } yield ()
      }

      def release(userId: UserId, ccy: CurrencyCode, amt: Amount, reference: String): F[Unit] = {
        for {
          trading <- repo.findOrCreate(Some(userId), AccountKind.Trading, ccy)
          cash <- repo.findOrCreate(Some(userId), AccountKind.Cash, ccy)
          entries <- pair(trading.id, cash.id, amt, ccy, reference, customerCredits = false)
          _ <- repo.post(entries)
        } yield ()
      }

      def balance(userId: UserId, ccy: CurrencyCode): F[Balance] = {
        for {
          cash <- repo.findOrCreate(Some(userId), AccountKind.Cash, ccy)
          bal <- repo.balance(cash.id, ccy)
        } yield bal
      }

      /** Build a debit/credit pair. `customerCredits=true` means money moves INTO the customer (deposit); `false` means
        * money moves OUT (withdraw, reserve).
        */
      private def pair(
          customer: AccountId,
          counter: AccountId,
          amt: Amount,
          ccy: CurrencyCode,
          reference: String,
          customerCredits: Boolean
      ): F[List[LedgerEntry]] = {
        for {
          now <- Clock[F].realTimeInstant
          id1 <- UUIDGen[F].randomUUID
          id2 <- UUIDGen[F].randomUUID
          (custDir, counterDir) = {
            if customerCredits then (EntryDirection.Credit, EntryDirection.Debit)
            else (EntryDirection.Debit, EntryDirection.Credit)
          }
        } yield List(
          LedgerEntry(LedgerEntryId(id1), customer, custDir, amt, ccy, reference, now),
          LedgerEntry(LedgerEntryId(id2), counter, counterDir, amt, ccy, reference, now)
        )
      }
    }
  }

}
