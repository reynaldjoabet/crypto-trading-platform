package trading.instruments

import cats.effect.*
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.ids.InstrumentId
import trading.domain.money.CurrencyCode
import trading.domain.market.*
import trading.persistence.InstrumentRepo

trait InstrumentService[F[_]] {
  def list(activeOnly: Boolean): F[List[Instrument]]
  def get(id: InstrumentId): F[Instrument]
  def create(symbol: Symbol, base: CurrencyCode, quote: CurrencyCode, venue: Venue): F[Instrument]
  def setActive(id: InstrumentId, active: Boolean): F[Unit]
}

object InstrumentService {
  def make[F[_]: Sync: UUIDGen](repo: InstrumentRepo[F]): InstrumentService[F] = {
    new InstrumentService[F] {
      def list(activeOnly: Boolean): F[List[Instrument]] = {
        repo.list(activeOnly)
      }

      def get(id: InstrumentId): F[Instrument] = {
        repo.find(id).flatMap {
          case Some(i) => i.pure[F]
          case None    => Sync[F].raiseError(AppError.NotFound("instrument", id.value.toString))
        }
      }

      def create(symbol: Symbol, base: CurrencyCode, quote: CurrencyCode, venue: Venue): F[Instrument] = {
        for {
          existing <- repo.findBySymbol(symbol)
          _ <- existing.fold(Sync[F].unit)(_ =>
            Sync[F].raiseError[Unit](AppError.Conflict(s"symbol ${symbol.value} exists"))
          )
          uid <- UUIDGen[F].randomUUID
          now <- Clock[F].realTimeInstant
          inst = Instrument(InstrumentId(uid), symbol, base, quote, venue, isActive = true, now)
          _ <- repo.upsert(inst)
        } yield inst
      }

      def setActive(id: InstrumentId, active: Boolean): F[Unit] = {
        repo.setActive(id, active)
      }
    }
  }
}
