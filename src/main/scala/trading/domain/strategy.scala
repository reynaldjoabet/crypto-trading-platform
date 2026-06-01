package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.market.Symbol

import java.time.Instant

object strategy {

  enum Indicator derives CanEqual {
    case RSI, MACD, EMA, SMA, BollingerBands, ATR, Stochastic, VWAP
  }

  type ComponentName = ComponentName.T
  object ComponentName extends RefinedType[String, Not[Empty] & MaxLength[120]]

  final case class StrategyComponent(
      id: ComponentId,
      name: ComponentName,
      indicator: Indicator,
      params: Map[String, Double],
      isActive: Boolean,
      createdAt: Instant
  ) derives CanEqual

  enum RiskLevel derives CanEqual {
    case Conservative, Balanced, Aggressive
  }

  type StrategyName = StrategyName.T
  object StrategyName extends RefinedType[String, Not[Empty] & MaxLength[120]]

  type MaxLeverage = MaxLeverage.T
  object MaxLeverage extends RefinedType[Int, GreaterEqual[1] & LessEqual[25]]

  final case class Strategy(
      id: StrategyId,
      name: StrategyName,
      symbol: Symbol,
      components: List[ComponentId],
      maxDrawdown: Percent,
      maxLeverage: MaxLeverage,
      managementFee: FeeBps,
      performanceFee: FeeBps,
      risk: RiskLevel,
      isPublished: Boolean,
      createdAt: Instant,
      updatedAt: Instant
  ) derives CanEqual

  final case class StrategyStats(
      strategyId: StrategyId,
      returns30d: Double,
      returns90d: Double,
      sharpe: Double,
      volatility: Double,
      asOf: Instant
  ) derives CanEqual

}
