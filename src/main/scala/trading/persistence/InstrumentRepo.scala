package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import trading.domain.ids.InstrumentId
import trading.domain.market.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait InstrumentRepo[F[_]] {
  def list(activeOnly: Boolean): F[List[Instrument]]
  def find(id: InstrumentId): F[Option[Instrument]]
  def findBySymbol(s: Symbol): F[Option[Instrument]]
  def upsert(i: Instrument): F[Unit]
  def setActive(id: InstrumentId, active: Boolean): F[Unit]
}

object InstrumentRepo {
  import Codecs.*

  private val instrumentCodec: Codec[Instrument] = {
    (instrumentIdC *: symbolC *: currencyC *: currencyC *: venueC *: bool *: instantTz)
      .imap(Instrument.apply.tupled)(i => (i.id, i.symbol, i.base, i.quote, i.venue, i.isActive, i.createdAt))
  }

  private val selectAll: Query[Void, Instrument] = {
    sql"SELECT id, symbol, base, quote, venue, is_active, created_at FROM instruments ORDER BY symbol"
      .query(instrumentCodec)
  }

  private val selectActive: Query[Void, Instrument] = {
    sql"""SELECT id, symbol, base, quote, venue, is_active, created_at
          FROM instruments WHERE is_active = true ORDER BY symbol""".query(instrumentCodec)
  }

  private val selectById: Query[InstrumentId, Instrument] = {
    sql"""SELECT id, symbol, base, quote, venue, is_active, created_at
          FROM instruments WHERE id = $instrumentIdC""".query(instrumentCodec)
  }

  private val selectBySymbol: Query[Symbol, Instrument] = {
    sql"""SELECT id, symbol, base, quote, venue, is_active, created_at
          FROM instruments WHERE symbol = $symbolC""".query(instrumentCodec)
  }

  private val upsertInstrument: Command[Instrument] = {
    sql"""INSERT INTO instruments (id, symbol, base, quote, venue, is_active, created_at)
          VALUES ($instrumentCodec)
          ON CONFLICT (id) DO UPDATE SET
            symbol = EXCLUDED.symbol,
            base   = EXCLUDED.base,
            quote  = EXCLUDED.quote,
            venue  = EXCLUDED.venue,
            is_active = EXCLUDED.is_active""".command
  }

  private val updateActive: Command[(Boolean, InstrumentId)] = {
    sql"UPDATE instruments SET is_active = $bool WHERE id = $instrumentIdC".command
  }

  def fromSession[F[_]: Concurrent](pool: Resource[F, Session[F]]): InstrumentRepo[F] = {
    new InstrumentRepo[F] {
      def list(activeOnly: Boolean): F[List[Instrument]] = {
        pool.use(_.execute(if activeOnly then selectActive else selectAll))
      }

      def find(id: InstrumentId): F[Option[Instrument]] = {
        pool.use(_.prepare(selectById).flatMap(_.option(id)))
      }

      def findBySymbol(s: Symbol): F[Option[Instrument]] = {
        pool.use(_.prepare(selectBySymbol).flatMap(_.option(s)))
      }

      def upsert(i: Instrument): F[Unit] = {
        pool.use(_.prepare(upsertInstrument).flatMap(_.execute(i))).void
      }

      def setActive(id: InstrumentId, active: Boolean): F[Unit] = {
        pool.use(_.prepare(updateActive).flatMap(_.execute((active, id)))).void
      }
    }
  }
}
