package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.ids.*
import trading.domain.money.*

import java.time.Instant

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
    case FTX extends Venue("FTX")
    case Mock extends Venue("MOCK")
  }

  type Symbol = Symbol.T
  object Symbol extends RefinedType[String, Match["^[A-Z0-9]{2,10}/[A-Z0-9]{2,10}$"]]

  final case class Instrument(
      id: InstrumentId,
      symbol: Symbol,
      base: CurrencyCode,
      quote: CurrencyCode,
      venue: Venue,
      isActive: Boolean,
      createdAt: Instant
  ) derives CanEqual

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
