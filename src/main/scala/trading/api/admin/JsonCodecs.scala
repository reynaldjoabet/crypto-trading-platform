package trading.api.admin

import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import trading.domain.AppError
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.strategy.*
import trading.domain.market.*

import java.util.UUID

/** Hand-written JSON codecs for the admin API. Iron-refined types are encoded as their base primitive and decoded with
  * a refining step that surfaces validation errors as 400s.
  */
object JsonCodecs {

  // ── id codecs ──
  given Encoder[UUID] = Encoder.encodeString.contramap(_.toString)
  given Decoder[UUID] = Decoder.decodeString.emapTry(s => scala.util.Try(UUID.fromString(s)))

  given Encoder[InstrumentId] = Encoder[UUID].contramap(_.value)
  given Decoder[InstrumentId] = Decoder[UUID].map(InstrumentId.apply)
  given Encoder[StrategyId] = Encoder[UUID].contramap(_.value)
  given Decoder[StrategyId] = Decoder[UUID].map(StrategyId.apply)
  given Encoder[ComponentId] = Encoder[UUID].contramap(_.value)
  given Decoder[ComponentId] = Decoder[UUID].map(ComponentId.apply)
  given Encoder[OrderId] = Encoder[UUID].contramap(_.value)
  given Decoder[OrderId] = Decoder[UUID].map(OrderId.apply)
  given Encoder[AccountId] = Encoder[UUID].contramap(_.value)
  given Decoder[AccountId] = Decoder[UUID].map(AccountId.apply)

  // ── money codecs ──
  given Encoder[Symbol] = Encoder.encodeString.contramap(_.value)
  given Decoder[Symbol] = Decoder.decodeString.emap(Symbol.either)
  given Encoder[CurrencyCode] = Encoder.encodeString.contramap(_.value)
  given Decoder[CurrencyCode] = Decoder.decodeString.emap(CurrencyCode.either)
  given Encoder[Price] = Encoder.encodeBigDecimal.contramap(_.value)
  given Decoder[Price] = Decoder.decodeBigDecimal.emap(Price.either)
  given Encoder[Quantity] = Encoder.encodeBigDecimal.contramap(_.value)
  given Decoder[Quantity] = Decoder.decodeBigDecimal.emap(Quantity.either)
  given Encoder[Amount] = Encoder.encodeBigDecimal.contramap(_.value)
  given Decoder[Amount] = Decoder.decodeBigDecimal.emap(Amount.either)
  given Encoder[Percent] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[Percent] = Decoder.decodeDouble.emap(Percent.either)
  given Encoder[FeeBps] = Encoder.encodeInt.contramap(_.value)
  given Decoder[FeeBps] = Decoder.decodeInt.emap(FeeBps.either)

  // ── enums as strings ──
  given Encoder[Side] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Side] =
    Decoder.decodeString.emap(s => Side.values.find(_.toString == s).toRight(s"unknown Side $s"))
  given Encoder[OrderType] = Encoder.encodeString.contramap(_.toString)
  given Decoder[OrderType] =
    Decoder.decodeString.emap(s => OrderType.values.find(_.toString == s).toRight(s"unknown OrderType $s"))
  given Encoder[OrderStatus] = Encoder.encodeString.contramap(_.toString)
  given Decoder[OrderStatus] =
    Decoder.decodeString.emap(s => OrderStatus.values.find(_.toString == s).toRight(s"unknown OrderStatus $s"))
  given Encoder[TimeInForce] = Encoder.encodeString.contramap(_.toString)
  given Decoder[TimeInForce] =
    Decoder.decodeString.emap(s => TimeInForce.values.find(_.toString == s).toRight(s"unknown TimeInForce $s"))
  given Encoder[Venue] = Encoder.encodeString.contramap(_.code)
  given Decoder[Venue] =
    Decoder.decodeString.emap(s => Venue.values.find(_.code == s).toRight(s"unknown Venue $s"))
  given Encoder[RiskLevel] = Encoder.encodeString.contramap(_.toString)
  given Decoder[RiskLevel] =
    Decoder.decodeString.emap(s => RiskLevel.values.find(_.toString == s).toRight(s"unknown RiskLevel $s"))
  given Encoder[Indicator] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Indicator] =
    Decoder.decodeString.emap(s => Indicator.values.find(_.toString == s).toRight(s"unknown Indicator $s"))

  // ── domain objects ──
  given Encoder[Instrument] = i =>
    Json.obj(
      "id" -> i.id.asJson,
      "symbol" -> i.symbol.asJson,
      "base" -> i.base.asJson,
      "quote" -> i.quote.asJson,
      "venue" -> i.venue.asJson,
      "isActive" -> Json.fromBoolean(i.isActive),
      "createdAt" -> Json.fromString(i.createdAt.toString)
    )

  final case class CreateInstrumentRequest(
      symbol: Symbol,
      base: CurrencyCode,
      quote: CurrencyCode,
      venue: Venue
  )
  given Decoder[CreateInstrumentRequest] = Decoder.instance(c =>
    for
      s <- c.get[Symbol]("symbol")
      b <- c.get[CurrencyCode]("base")
      q <- c.get[CurrencyCode]("quote")
      v <- c.get[Venue]("venue")
    yield CreateInstrumentRequest(s, b, q, v)
  )

  final case class ErrorBody(code: String, message: String)
  given Encoder[ErrorBody] = e => Json.obj("code" -> e.code.asJson, "message" -> e.message.asJson)
  given Encoder[AppError] = e => ErrorBody(e.code, e.message).asJson
  // circe's Encoder is invariant; downcast to AppError for subtype call sites.
  given [E <: AppError]: Encoder[E] = Encoder[AppError].contramap(identity)

}
