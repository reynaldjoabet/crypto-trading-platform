package trading.orders

import cats.effect.*
import cats.syntax.all.*
import cats.effect.std.UUIDGen
import trading.accounts.AccountService
import trading.domain.AppError
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.market.*
import trading.exchanges.{ExchangeClient, VenueFill}
import trading.instruments.InstrumentService
import trading.persistence.OrderRepo
import org.typelevel.log4cats.Logger

import java.time.Instant

final case class PlaceOrderRequest(
    accountId: AccountId,
    instrumentId: InstrumentId,
    side: Side,
    orderType: OrderType,
    quantity: Quantity,
    limitPrice: Option[Price],
    stopPrice: Option[Price],
    timeInForce: TimeInForce,
    strategyId: Option[StrategyId]
)

trait OrderService[F[_]] {
  def place(req: PlaceOrderRequest): F[Order]
  def cancel(id: OrderId): F[Unit]
  def reconcile(id: OrderId): F[Order]
  def list(accountId: AccountId, limit: Int): F[List[Order]]
}

object OrderService {

  def make[F[_]: Async: UUIDGen: Logger](
      orders: OrderRepo[F],
      instruments: InstrumentService[F],
      accounts: AccountService[F],
      selectExchange: Venue => F[ExchangeClient[F]]
  ): OrderService[F] = {
    val _ = accounts // reservation/release wired in once OrderRepo exposes userId; see TODO in place()
    new OrderService[F] {
      def place(req: PlaceOrderRequest): F[Order] = {
        for {
          inst <- instruments.get(req.instrumentId)
          uid <- UUIDGen[F].randomUUID
          now <- Clock[F].realTimeInstant
          intent = OrderIntent(
            id = OrderId(uid),
            accountId = req.accountId,
            instrumentId = req.instrumentId,
            side = req.side,
            orderType = req.orderType,
            quantity = req.quantity,
            limitPrice = req.limitPrice,
            stopPrice = req.stopPrice,
            timeInForce = req.timeInForce,
            strategyId = req.strategyId,
            createdAt = now
          )
          // Pre-trade reservation: lock notional in the user's trading account.
          notional = notionalFor(intent, inst)
          _ <- notional.fold(Async[F].unit)(amt =>
            // We don't have userId here; accounts are user-scoped via AccountId. In a
            // real impl, we'd look up the user from AccountId. Skipped in this slice.
            Logger[F].debug(s"[OrderService] would reserve $amt for ${intent.id}")
          )
          order = Order(intent, OrderStatus.Pending, inst.venue, None, Amount.Zero, None, now)
          _ <- orders.insert(order)
          // Submit to venue
          venue <- selectExchange(inst.venue)
          ack <- venue.submit(intent).attempt
          updated <- ack match {
            case Right(a) => {
              orders.markSubmitted(intent.id, a.venueOrderId, a.acceptedAt) *>
                orders.find(intent.id).map(_.getOrElse(order))
            }
            case Left(t) => {
              Logger[F].error(t)(s"venue submit failed for ${intent.id}") *>
                orders
                  .transition(intent.id, OrderStatus.Rejected, now)
                  .as(
                    order.copy(status = OrderStatus.Rejected, lastEventAt = now)
                  )
            }
          }
        } yield updated
      }

      def cancel(id: OrderId): F[Unit] = {
        for {
          ord <- orders.find(id).flatMap {
            case Some(o) => o.pure[F]
            case None    => Async[F].raiseError(AppError.NotFound("order", id.value.toString))
          }
          _ <- {
            if ord.isTerminal then Async[F].raiseError(AppError.Conflict("order is terminal"))
            else Async[F].unit
          }
          inst <- instruments.get(ord.intent.instrumentId)
          ex <- selectExchange(inst.venue)
          _ <- ord.venueOrderId.fold(Async[F].unit)(ex.cancel)
          now <- Clock[F].realTimeInstant
          _ <- orders.transition(id, OrderStatus.Cancelled, now)
        } yield ()
      }

      def reconcile(id: OrderId): F[Order] = {
        for {
          ord <- orders.find(id).flatMap {
            case Some(o) => o.pure[F]
            case None    => Async[F].raiseError(AppError.NotFound("order", id.value.toString))
          }
          inst <- instruments.get(ord.intent.instrumentId)
          ex <- selectExchange(inst.venue)
          fills <- ord.venueOrderId.fold(Async[F].pure(List.empty[VenueFill]))(ex.fetchFills)
          now <- Clock[F].realTimeInstant
          _ <- fills.traverse_(applyFill(ord, _, now))
          // If cumulative fills meet quantity, mark Filled (the repo only sets PartiallyFilled).
          refreshed <- orders.find(id).map(_.getOrElse(ord))
          _ <- {
            if refreshed.filledQty.value >= refreshed.intent.quantity.value then
              orders.transition(id, OrderStatus.Filled, now)
            else Async[F].unit
          }
          final_ <- orders.find(id).map(_.getOrElse(refreshed))
        } yield final_
      }

      def list(accountId: AccountId, limit: Int): F[List[Order]] = {
        orders.listForAccount(accountId, limit)
      }

      private def applyFill(ord: Order, f: VenueFill, now: Instant): F[Unit] = {
        orders.applyFill(ord.intent.id, f.quantity.value, Some(f.price.value), now)
      }

      private def notionalFor(intent: OrderIntent, inst: Instrument): Option[Amount] = {
        intent.limitPrice.map(p => (p.value * intent.quantity.value).abs).flatMap(v => Amount(v).toOption)
      }
    }
  }

}
