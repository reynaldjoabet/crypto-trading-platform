package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import trading.domain.ids.*
import trading.domain.market.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

import java.time.Instant

trait OrderRepo[F[_]] {
  def insert(o: Order): F[Unit]
  def find(id: OrderId): F[Option[Order]]
  def listOpen(): F[List[Order]]
  def listForAccount(accountId: AccountId, limit: Int): F[List[Order]]
  def markSubmitted(id: OrderId, venueOrderId: String, at: Instant): F[Unit]
  def applyFill(id: OrderId, addQty: BigDecimal, newAvg: Option[BigDecimal], at: Instant): F[Unit]
  def transition(id: OrderId, status: OrderStatus, at: Instant): F[Unit]
}

object OrderRepo {
  import Codecs.*

  private val insertOrder: Command[Order] = {
    sql"""INSERT INTO orders
            (id, account_id, instrument_id, strategy_id, side, order_type, quantity,
             limit_price, stop_price, time_in_force, status, venue, venue_order_id,
             filled_qty, avg_fill_price, created_at, last_event_at)
          VALUES ($orderCodec)""".command
  }

  private val selectById: Query[OrderId, Order] = {
    sql"""SELECT id, account_id, instrument_id, strategy_id, side, order_type, quantity,
                 limit_price, stop_price, time_in_force, status, venue, venue_order_id,
                 filled_qty, avg_fill_price, created_at, last_event_at
          FROM orders WHERE id = $orderIdC""".query(orderCodec)
  }

  private val selectOpen: Query[Void, Order] = {
    sql"""SELECT id, account_id, instrument_id, strategy_id, side, order_type, quantity,
                 limit_price, stop_price, time_in_force, status, venue, venue_order_id,
                 filled_qty, avg_fill_price, created_at, last_event_at
          FROM orders
          WHERE status IN ('Pending','Submitted','PartiallyFilled')
          ORDER BY created_at""".query(orderCodec)
  }

  private val selectByAccount: Query[(AccountId, Int), Order] = {
    sql"""SELECT id, account_id, instrument_id, strategy_id, side, order_type, quantity,
                 limit_price, stop_price, time_in_force, status, venue, venue_order_id,
                 filled_qty, avg_fill_price, created_at, last_event_at
          FROM orders WHERE account_id = $accountIdC
          ORDER BY created_at DESC LIMIT $int4""".query(orderCodec)
  }

  private val updateSubmitted: Command[(String, Instant, OrderId)] = {
    sql"""UPDATE orders SET status='Submitted', venue_order_id=$text, last_event_at=$instantTz
          WHERE id = $orderIdC AND status='Pending'""".command
  }

  private val updateFill: Command[(BigDecimal, Option[BigDecimal], Instant, OrderId)] = {
    sql"""UPDATE orders SET
            filled_qty     = filled_qty + $numeric,
            avg_fill_price = COALESCE(${numeric.opt}, avg_fill_price),
            status         = 'PartiallyFilled',
            last_event_at  = $instantTz
          WHERE id = $orderIdC""".command
  }

  private val updateStatus: Command[(OrderStatus, Instant, OrderId)] = {
    sql"UPDATE orders SET status=$orderStatusC, last_event_at=$instantTz WHERE id=$orderIdC".command
  }

  def fromSession[F[_]: Concurrent](pool: Resource[F, Session[F]]): OrderRepo[F] = {
    new OrderRepo[F] {
      def insert(o: Order): F[Unit] = {
        pool.use(_.prepare(insertOrder).flatMap(_.execute(o))).void
      }

      def find(id: OrderId): F[Option[Order]] = {
        pool.use(_.prepare(selectById).flatMap(_.option(id)))
      }

      def listOpen(): F[List[Order]] = {
        pool.use(_.execute(selectOpen))
      }

      def listForAccount(accountId: AccountId, limit: Int): F[List[Order]] = {
        pool.use(_.prepare(selectByAccount).flatMap(_.stream((accountId, limit), 64).compile.toList))
      }

      def markSubmitted(id: OrderId, venueOrderId: String, at: Instant): F[Unit] = {
        pool.use(_.prepare(updateSubmitted).flatMap(_.execute((venueOrderId, at, id)))).void
      }

      def applyFill(id: OrderId, addQty: BigDecimal, newAvg: Option[BigDecimal], at: Instant): F[Unit] = {
        pool.use(_.prepare(updateFill).flatMap(_.execute((addQty, newAvg, at, id)))).void
      }

      def transition(id: OrderId, status: OrderStatus, at: Instant): F[Unit] = {
        pool.use(_.prepare(updateStatus).flatMap(_.execute((status, at, id)))).void
      }
    }
  }
}
