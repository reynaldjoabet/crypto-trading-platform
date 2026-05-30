package trading.persistence

import java.time.{Instant, ZoneOffset}
import skunk.*
import skunk.codec.all.*
import org.typelevel.twiddles.syntax.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.accounts.*
import trading.domain.addresses.*
import trading.domain.ids.*
import trading.domain.money.*
import trading.domain.strategy.*
import trading.domain.market.*
import trading.domain.user.*

/** skunk codecs for our domain types. Iron refinements unwrap to their underlying primitive on the way out and
  * re-refine on the way in (Postgres CHECK constraints back this up too).
  */
object Codecs {

  // ── ids ──
  val userIdC: Codec[UserId] = uuid.imap(UserId.apply)(_.value)
  val accountIdC: Codec[AccountId] = uuid.imap(AccountId.apply)(_.value)
  val walletIdC: Codec[WalletId] = uuid.imap(WalletId.apply)(_.value)
  val instrumentIdC: Codec[InstrumentId] = uuid.imap(InstrumentId.apply)(_.value)
  val strategyIdC: Codec[StrategyId] = uuid.imap(StrategyId.apply)(_.value)
  val componentIdC: Codec[ComponentId] = uuid.imap(ComponentId.apply)(_.value)
  val orderIdC: Codec[OrderId] = uuid.imap(OrderId.apply)(_.value)
  val tradeIdC: Codec[TradeId] = uuid.imap(TradeId.apply)(_.value)
  val ledgerEntryIdC: Codec[LedgerEntryId] = uuid.imap(LedgerEntryId.apply)(_.value)
  val projectIdC: Codec[ProjectId] = uuid.imap(ProjectId.apply)(_.value)
  val donationIdC: Codec[DonationId] = uuid.imap(DonationId.apply)(_.value)
  val postIdC: Codec[PostId] = uuid.imap(PostId.apply)(_.value)

  // ── money ──
  val priceC: Codec[Price] = numeric.eimap(Price.apply)(_.value)
  val quantityC: Codec[Quantity] = numeric.eimap(Quantity.apply)(_.value)
  val amountC: Codec[Amount] = numeric.eimap(Amount.apply)(_.value)
  val percentC: Codec[Percent] = float8.eimap(Percent.apply)(_.value)
  val feeBpsC: Codec[FeeBps] = int4.eimap(FeeBps.apply)(_.value)
  val currencyC: Codec[CurrencyCode] = text.eimap(CurrencyCode.apply)(s => s: String)
  val emailC: Codec[Email] = text.eimap(Email.apply)(_.value)

  // ── enums ── written as strings; constraints enforce values at the DB level
  private def stringEnum[A](toS: A => String, fromS: String => Either[String, A]): Codec[A] = {
    text.eimap(fromS)(toS)
  }

  val sideC: Codec[Side] = stringEnum(
    {
      case Side.Buy  => "Buy"
      case Side.Sell => "Sell"
    },
    {
      case "Buy"  => Right(Side.Buy)
      case "Sell" => Right(Side.Sell)
      case s      => Left(s"unknown Side: $s")
    }
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

  val symbolC: Codec[Symbol] = text.eimap(Symbol.apply)(s => s: String)

  /** timestamptz <-> Instant (domain uses Instant; skunk's default is OffsetDateTime). */
  val instantTz: Codec[Instant] = {
    timestamptz.imap(_.toInstant)(_.atOffset(ZoneOffset.UTC))
  }

  val countryIsoC: Codec[String :| io.github.iltotore.iron.constraint.string.Match["^[A-Z]{2}$"]] = {
    bpchar(2).imap(_.refineUnsafe[io.github.iltotore.iron.constraint.string.Match["^[A-Z]{2}$"]])((s: String) => s)
  }

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
          (
            entry.accountId,
            (entry.direction, (entry.amount, (entry.currency, (entry.reference, entry.postedAt))))
          )
        )
      }
  }

  val userCodec: Codec[User] = {
    (userIdC *: emailC *: text *: roleC *: tierC *: kycC *: text *:
      countryIsoC *: instantTz *: instantTz)
      .imap { case (id, (email, (name, (role, (tier, (kyc, (sub, (country, (c, u))))))))) =>
        val refined = name
          .refineEither[Not[Empty] & MaxLength[80]]
          .fold(
            _ => "User".refineUnsafe[Not[Empty] & MaxLength[80]],
            identity
          )
        User(id, email, refined, role, tier, kyc, sub, country, c, u)
      }(u =>
        (
          u.id,
          (
            u.email,
            (
              (u.displayName: String),
              (u.role, (u.tier, (u.kyc, (u.externalSubject, (u.countryIso, (u.createdAt, u.updatedAt))))))
            )
          )
        )
      )
  }

  // TODO: Order codec composition fails due to Scala tuple type inference limitations
  // Requires refactoring Order to use smaller codec compositions or custom builders
  val orderCodec: Codec[Order] = ???

  private val strategyRowNested: Codec[
    (
        StrategyId,
        (
            String,
            (
                trading.domain.market.Symbol,
                (
                    trading.domain.money.Percent,
                    (
                        Int,
                        (
                            trading.domain.money.FeeBps,
                            (
                                trading.domain.money.FeeBps,
                                (
                                    RiskLevel,
                                    (
                                        Boolean,
                                        (
                                            java.time.Instant,
                                            java.time.Instant
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
  ] = {
    strategyIdC *: text *: symbolC *: percentC *: int4 *: feeBpsC *: feeBpsC *:
      riskC *: bool *: instantTz *: instantTz
  }

  val strategyRow: Codec[
    (
        StrategyId,
        String,
        trading.domain.market.Symbol,
        trading.domain.money.Percent,
        Int,
        trading.domain.money.FeeBps,
        trading.domain.money.FeeBps,
        RiskLevel,
        Boolean,
        java.time.Instant,
        java.time.Instant
    )
  ] = {
    strategyRowNested.imap { case (id, (name, (sym, (dd, (lev, (mf, (pf, (risk, (pub, (c, u)))))))))) =>
      (id, name, sym, dd, lev, mf, pf, risk, pub, c, u)
    } { case (id, name, sym, dd, lev, mf, pf, risk, pub, c, u) =>
      (id, (name, (sym, (dd, (lev, (mf, (pf, (risk, (pub, (c, u))))))))))
    }
  }

  private def componentToTuple(comp: StrategyComponent) = {
    // Iron types at runtime are just their base type, so comp.name is effectively a String
    (comp.id, (comp.name, (comp.indicator, (serializeParams(comp.params), (comp.isActive, comp.createdAt)))))
  }

  val componentCodec: Codec[StrategyComponent] = {
    (componentIdC *: text *: indicatorC *: text *: bool *: instantTz)
      .imap { case (id, (name, (ind, (params, (active, created))))) =>
        val rname = name.refineUnsafe[Not[Empty] & MaxLength[120]]
        StrategyComponent(id, rname, ind, parseParams(params), active, created)
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
            case k :: v :: Nil => {
              scala.util
                .Try(v.toDouble)
                .toOption
                .map(d => k.trim.stripPrefix("\"").stripSuffix("\"") -> d)
            }
            case _ => None
          }
        }
        .toMap
    }
  }

}
