package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.money.*

import java.time.Instant

import trading.domain.ids.*

object market {

  enum Side derives CanEqual {
    case Buy, Sell
  }

  enum OrderType derives CanEqual {
    case Market, Limit, StopLimit, TakeProfit
  }

  enum TimeInForce derives CanEqual {
    case Gtc, Ioc, Fok, Day
  }

  enum OrderStatus derives CanEqual {
    case Pending, Submitted, PartiallyFilled, Filled, Cancelled, Rejected, Failed
  }

  enum Venue(val code: String) derives CanEqual {
    case Kraken extends Venue("KRAKEN")
    case Binance extends Venue("BINANCE")
    case FTX extends Venue("FTX") // historical — kept for migration tests
    case Mock extends Venue("MOCK")
  }

  /** Trading pair like BTC/USD. */
  type Symbol = String :| Match["^[A-Z0-9]{2,10}/[A-Z0-9]{2,10}$"]
  object Symbol {
    inline def apply(s: String): Either[String, Symbol] = s.refineEither
    inline def unsafe(s: String): Symbol = s.refineUnsafe
  }

  final case class Instrument(
      id: InstrumentId,
      symbol: Symbol,
      base: CurrencyCode,
      quote: CurrencyCode,
      venue: Venue,
      isActive: Boolean,
      createdAt: Instant
  ) derives CanEqual

  /** What a client/admin asks for. Pure intent — no exchange ack yet. */
  final case class OrderIntent(
      id: OrderId,
      accountId: AccountId,
      instrumentId: InstrumentId,
      side: Side,
      orderType: OrderType,
      quantity: Quantity,
      limitPrice: Option[Price],
      stopPrice: Option[Price],
      timeInForce: TimeInForce,
      strategyId: Option[StrategyId],
      createdAt: Instant
  ) derives CanEqual

  /** Lifecycle state pinned to a venue-side order id once submitted. */
  final case class Order(
      intent: OrderIntent,
      status: OrderStatus,
      venue: Venue,
      venueOrderId: Option[String],
      filledQty: Amount,
      avgFillPrice: Option[Price],
      lastEventAt: Instant
  ) derives CanEqual {
    def isTerminal: Boolean = status match {
      case OrderStatus.Filled | OrderStatus.Cancelled | OrderStatus.Rejected | OrderStatus.Failed => true
      case _                                                                                      => false
    }
  }

  final case class Trade(
      id: TradeId,
      orderId: OrderId,
      venueTradeId: String,
      price: Price,
      quantity: Quantity,
      fee: Amount,
      feeCurrency: CurrencyCode,
      executedAt: Instant
  ) derives CanEqual

}
