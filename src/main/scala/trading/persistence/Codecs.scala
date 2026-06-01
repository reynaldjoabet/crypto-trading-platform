package trading.persistence

import java.time.{Instant, ZoneOffset}
import skunk.*
import skunk.codec.all.*
import org.typelevel.twiddles.syntax.*
// import io.github.iltotore.iron.*
// import io.github.iltotore.iron.constraint.all.*
import trading.domain.accounts.*
import trading.domain.addresses.*
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.strategy.*
import trading.domain.market.*
import trading.domain.user.*

object Codecs {

  // ── ids ──
  val userIdC: Codec[UserId] = uuid.imap(UserId.assume)(_.value)
  val accountIdC: Codec[AccountId] = uuid.imap(AccountId.assume)(_.value)
  val walletIdC: Codec[WalletId] = uuid.imap(WalletId.assume)(_.value)
  val instrumentIdC: Codec[InstrumentId] = uuid.imap(InstrumentId.assume)(_.value)
  val strategyIdC: Codec[StrategyId] = uuid.imap(StrategyId.assume)(_.value)
  val componentIdC: Codec[ComponentId] = uuid.imap(ComponentId.assume)(_.value)
  val orderIdC: Codec[OrderId] = uuid.imap(OrderId.assume)(_.value)
  val tradeIdC: Codec[TradeId] = uuid.imap(TradeId.assume)(_.value)
  val ledgerEntryIdC: Codec[LedgerEntryId] = uuid.imap(LedgerEntryId.assume)(_.value)
  val projectIdC: Codec[ProjectId] = uuid.imap(ProjectId.assume)(_.value)
  val donationIdC: Codec[DonationId] = uuid.imap(DonationId.assume)(_.value)
  val postIdC: Codec[PostId] = uuid.imap(PostId.assume)(_.value)

  // ── money ──
  val priceC: Codec[Price] = numeric.eimap(Price.either)(_.value)
  val quantityC: Codec[Quantity] = numeric.eimap(Quantity.either)(_.value)
  val amountC: Codec[Amount] = numeric.eimap(Amount.either)(_.value)
  val percentC: Codec[Percent] = float8.eimap(Percent.either)(_.value)
  val feeBpsC: Codec[FeeBps] = int4.eimap(FeeBps.either)(_.value)
  val currencyC: Codec[CurrencyCode] = text.eimap(CurrencyCode.either)(_.value)
  val emailC: Codec[Email] = text.eimap(Email.either)(_.value)

  // ── enums ──
  private def stringEnum[A](toS: A => String, fromS: String => Either[String, A]): Codec[A] = {
    text.eimap(fromS)(toS)
  }

  val sideC: Codec[Side] = stringEnum(
    { case Side.Buy => "Buy"; case Side.Sell => "Sell" },
    { case "Buy" => Right(Side.Buy); case "Sell" => Right(Side.Sell); case s => Left(s"unknown Side: $s") }
  )

  val orderTypeC: Codec[OrderType] = stringEnum(
    {
      case OrderType.Market     => "Market"
      case OrderType.Limit      => "Limit"
      case OrderType.StopLimit  => "StopLimit"
      case OrderType.TakeProfit => "TakeProfit"
    },
    s => OrderType.values.find(_.toString == s).toRight(s"unknown OrderType: $s")
  )

  val timeInForceC: Codec[TimeInForce] = stringEnum(
    _.toString,
    s => TimeInForce.values.find(_.toString == s).toRight(s"unknown TimeInForce: $s")
  )

  val orderStatusC: Codec[OrderStatus] = stringEnum(
    _.toString,
    s => OrderStatus.values.find(_.toString == s).toRight(s"unknown OrderStatus: $s")
  )

  val venueC: Codec[Venue] = stringEnum(
    _.code,
    s => Venue.values.find(_.code == s).toRight(s"unknown Venue: $s")
  )

  val roleC: Codec[Role] = stringEnum(
    _.toString,
    s => Role.values.find(_.toString == s).toRight(s"unknown Role: $s")
  )

  val kycC: Codec[KycStatus] = stringEnum(
    _.toString,
    s => KycStatus.values.find(_.toString == s).toRight(s"unknown KycStatus: $s")
  )

  val tierC: Codec[Tier] = stringEnum(
    _.toString,
    s => Tier.values.find(_.toString == s).toRight(s"unknown Tier: $s")
  )

  val accountKindC: Codec[AccountKind] = stringEnum(
    _.toString,
    s => AccountKind.values.find(_.toString == s).toRight(s"unknown AccountKind: $s")
  )

  val directionC: Codec[EntryDirection] = stringEnum(
    _.toString,
    s => EntryDirection.values.find(_.toString == s).toRight(s"unknown EntryDirection: $s")
  )

  val indicatorC: Codec[Indicator] = stringEnum(
    _.toString,
    s => Indicator.values.find(_.toString == s).toRight(s"unknown Indicator: $s")
  )

  val riskC: Codec[RiskLevel] = stringEnum(
    _.toString,
    s => RiskLevel.values.find(_.toString == s).toRight(s"unknown RiskLevel: $s")
  )

  val symbolC: Codec[Symbol] = text.eimap(Symbol.either)(_.value)

  val instantTz: Codec[Instant] = {
    timestamptz.imap(_.toInstant)(_.atOffset(ZoneOffset.UTC))
  }

  val countryIsoC: Codec[CountryIso] = bpchar(2).eimap(CountryIso.either)(_.value)

  val accountCodec: Codec[Account] = {
    (accountIdC *: userIdC.opt *: accountKindC *: currencyC *: instantTz)
      .imap { case (id, (userId, (kind, (currency, createdAt)))) =>
        Account(id, userId, kind, currency, createdAt)
      } { a =>
        (a.id, (a.userId, (a.kind, (a.currency, a.createdAt))))
      }
  }

  val entryCodec: Codec[LedgerEntry] = {
    (ledgerEntryIdC *: accountIdC *: directionC *: amountC *: currencyC *: text *: instantTz)
      .imap { case (id, (accountId, (direction, (amount, (ccy, (reference, postedAt)))))) =>
        LedgerEntry(id, accountId, direction, amount, ccy, reference, postedAt)
      } { entry =>
        (
          entry.id,
          (entry.accountId, (entry.direction, (entry.amount, (entry.currency, (entry.reference, entry.postedAt)))))
        )
      }
  }

  val userCodec: Codec[User] = {
    (userIdC *: emailC *: text *: roleC *: tierC *: kycC *: text *: countryIsoC *: instantTz *: instantTz)
      .imap { case (id, (email, (name, (role, (tier, (kyc, (sub, (country, (c, u))))))))) =>
        val displayName = DisplayName.either(name).fold(_ => DisplayName.applyUnsafe("User"), identity)
        User(id, email, displayName, role, tier, kyc, sub, country, c, u)
      } { u =>
        (
          u.id,
          (
            u.email,
            (
              u.displayName.value,
              (u.role, (u.tier, (u.kyc, (u.externalSubject, (u.countryIso, (u.createdAt, u.updatedAt))))))
            )
          )
        )
      }
  }

  val orderCodec: Codec[Order] = {
    (orderIdC *: accountIdC *: instrumentIdC *: strategyIdC.opt *: sideC *: orderTypeC *:
      quantityC *: priceC.opt *: priceC.opt *: timeInForceC *: orderStatusC *: venueC *:
      text.opt *: amountC *: priceC.opt *: instantTz *: instantTz)
      .imap {
        case (
              id,
              (
                acct,
                (
                  inst,
                  (
                    strat,
                    (
                      side,
                      (otype, (qty, (lim, (stop, (tif, (status, (venue, (voId, (filled, (avg, (created, last)))))))))))
                    )
                  )
                )
              )
            ) =>
          Order(
            OrderIntent(id, acct, inst, side, otype, qty, lim, stop, tif, strat, created),
            status,
            venue,
            voId,
            filled,
            avg,
            last
          )
      } { o =>
        val i = o.intent
        (
          i.id,
          (
            i.accountId,
            (
              i.instrumentId,
              (
                i.strategyId,
                (
                  i.side,
                  (
                    i.orderType,
                    (
                      i.quantity,
                      (
                        i.limitPrice,
                        (
                          i.stopPrice,
                          (
                            i.timeInForce,
                            (
                              o.status,
                              (o.venue, (o.venueOrderId, (o.filledQty, (o.avgFillPrice, (i.createdAt, o.lastEventAt)))))
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      }
  }

  private val strategyRowNested: Codec[
    (StrategyId, (String, (Symbol, (Percent, (Int, (FeeBps, (FeeBps, (RiskLevel, (Boolean, (Instant, Instant))))))))))
  ] = {
    strategyIdC *: text *: symbolC *: percentC *: int4 *: feeBpsC *: feeBpsC *:
      riskC *: bool *: instantTz *: instantTz
  }

  val strategyRow
      : Codec[(StrategyId, String, Symbol, Percent, Int, FeeBps, FeeBps, RiskLevel, Boolean, Instant, Instant)] = {
    strategyRowNested.imap { case (id, (name, (sym, (dd, (lev, (mf, (pf, (risk, (pub, (c, u)))))))))) =>
      (id, name, sym, dd, lev, mf, pf, risk, pub, c, u)
    } { case (id, name, sym, dd, lev, mf, pf, risk, pub, c, u) =>
      (id, (name, (sym, (dd, (lev, (mf, (pf, (risk, (pub, (c, u))))))))))
    }
  }

  private def componentToTuple(comp: StrategyComponent) = {
    (comp.id, (comp.name.value, (comp.indicator, (serializeParams(comp.params), (comp.isActive, comp.createdAt)))))
  }

  val componentCodec: Codec[StrategyComponent] = {
    (componentIdC *: text *: indicatorC *: text *: bool *: instantTz)
      .imap { case (id, (name, (ind, (params, (active, created))))) =>
        StrategyComponent(id, ComponentName.applyUnsafe(name), ind, parseParams(params), active, created)
      }(componentToTuple)
  }

  def serializeParams(m: Map[String, Double]): String = {
    m.toList.map((k, v) => s""""$k":$v""").mkString("{", ",", "}")
  }

  def parseParams(json: String): Map[String, Double] = {
    val s = json.trim
    if s == "{}" || s.isEmpty then Map.empty
    else {
      val body = s.stripPrefix("{").stripSuffix("}")
      body
        .split(",")
        .iterator
        .flatMap { kv =>
          kv.split(":").toList match {
            case k :: v :: Nil =>
              scala.util
                .Try(v.toDouble)
                .toOption
                .map(d => k.trim.stripPrefix("\"").stripSuffix("\"") -> d)
            case _ => None
          }
        }
        .toMap
    }
  }

}
