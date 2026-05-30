package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.market.Symbol

import java.time.Instant

/** Strategy = composition of components configured by super-admins. Admins assemble them. */
object strategy {

  /** Technical indicator a component can be parameterised against. */
  enum Indicator derives CanEqual {
    case RSI, MACD, EMA, SMA, BollingerBands, ATR, Stochastic, VWAP
  }

  /** A reusable building block authored by super-admins. */
  final case class StrategyComponent(
      id: ComponentId,
      name: String :| (Not[Empty] & MaxLength[120]),
      indicator: Indicator,
      params: Map[String, Double], // e.g. {"period": 14, "overbought": 70}
      isActive: Boolean,
      createdAt: Instant
  ) derives CanEqual

  enum RiskLevel derives CanEqual {
    case Conservative, Balanced, Aggressive
  }

  final case class Strategy(
      id: StrategyId,
      name: String :| (Not[Empty] & MaxLength[120]),
      symbol: Symbol,
      components: List[ComponentId],
      maxDrawdown: Percent,
      maxLeverage: Int :| (GreaterEqual[1] & LessEqual[25]),
      managementFee: FeeBps,
      performanceFee: FeeBps,
      risk: RiskLevel,
      isPublished: Boolean,
      createdAt: Instant,
      updatedAt: Instant
  ) derives CanEqual

  /** Past performance, computed offline; cached for display in the client portal. */
  final case class StrategyStats(
      strategyId: StrategyId,
      returns30d: Double,
      returns90d: Double,
      sharpe: Double,
      volatility: Double,
      asOf: Instant
  ) derives CanEqual

}
