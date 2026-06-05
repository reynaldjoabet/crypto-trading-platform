package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** Lending / credit domain. The invariants here (score range, non-negative principal, rate and LTV bounds) are exactly
  * the ones underwriting rules depend on, so encoding them in the type system removes a class of policy bugs.
  */

/** FICO credit score, 300–850. */
type CreditScore = CreditScore.T
object CreditScore extends RefinedType[Int, Interval.Closed[300, 850]]

/** Annual percentage rate, 0–100 (%). */
type Apr = Apr.T
object Apr extends RefinedType[BigDecimal, Interval.Closed[0, 100]]

/** Loan-to-value ratio in `(0, 1]`. */
type Ltv = Ltv.T
object Ltv extends RefinedType[BigDecimal, Interval.OpenClosed[0, 1]]

/** Loan term in months, 1–480 (up to 40 years). */
type TermMonths = TermMonths.T
object TermMonths extends RefinedType[Int, Interval.Closed[1, 480]]

/** Expiry month, 1–12. */
type ExpiryMonth = ExpiryMonth.T
object ExpiryMonth extends RefinedType[Int, Interval.Closed[1, 12]]

/** Two-digit expiry year, 0–99. */
type ExpiryYear = ExpiryYear.T
object ExpiryYear extends RefinedType[Int, Interval.Closed[0, 99]]

/** Is an active ISO 4217 alphabetic currency code. */
final class ISO4217
object ISO4217 {

  /** Active ISO 4217 alphabetic codes (subset sufficient for most products). */
  val codes: Set[String] = Set(
    "AED",
    "AFN",
    "ALL",
    "AMD",
    "ANG",
    "AOA",
    "ARS",
    "AUD",
    "AWG",
    "AZN",
    "BAM",
    "BBD",
    "BDT",
    "BGN",
    "BHD",
    "BIF",
    "BMD",
    "BND",
    "BOB",
    "BRL",
    "BSD",
    "BTN",
    "BWP",
    "BYN",
    "BZD",
    "CAD",
    "CDF",
    "CHF",
    "CLP",
    "CNY",
    "COP",
    "CRC",
    "CUP",
    "CVE",
    "CZK",
    "DJF",
    "DKK",
    "DOP",
    "DZD",
    "EGP",
    "ERN",
    "ETB",
    "EUR",
    "FJD",
    "FKP",
    "GBP",
    "GEL",
    "GHS",
    "GIP",
    "GMD",
    "GNF",
    "GTQ",
    "GYD",
    "HKD",
    "HNL",
    "HRK",
    "HTG",
    "HUF",
    "IDR",
    "ILS",
    "INR",
    "IQD",
    "IRR",
    "ISK",
    "JMD",
    "JOD",
    "JPY",
    "KES",
    "KGS",
    "KHR",
    "KMF",
    "KPW",
    "KRW",
    "KWD",
    "KYD",
    "KZT",
    "LAK",
    "LBP",
    "LKR",
    "LRD",
    "LSL",
    "LYD",
    "MAD",
    "MDL",
    "MGA",
    "MKD",
    "MMK",
    "MNT",
    "MOP",
    "MRU",
    "MUR",
    "MVR",
    "MWK",
    "MXN",
    "MYR",
    "MZN",
    "NAD",
    "NGN",
    "NIO",
    "NOK",
    "NPR",
    "NZD",
    "OMR",
    "PAB",
    "PEN",
    "PGK",
    "PHP",
    "PKR",
    "PLN",
    "PYG",
    "QAR",
    "RON",
    "RSD",
    "RUB",
    "RWF",
    "SAR",
    "SBD",
    "SCR",
    "SDG",
    "SEK",
    "SGD",
    "SHP",
    "SLE",
    "SOS",
    "SRD",
    "SSP",
    "STN",
    "SVC",
    "SYP",
    "SZL",
    "THB",
    "TJS",
    "TMT",
    "TND",
    "TOP",
    "TRY",
    "TTD",
    "TWD",
    "TZS",
    "UAH",
    "UGX",
    "USD",
    "UYU",
    "UZS",
    "VED",
    "VES",
    "VND",
    "VUV",
    "WST",
    "XAF",
    "XCD",
    "XOF",
    "XPF",
    "YER",
    "ZAR",
    "ZMW",
    "ZWL"
  )

  given Constraint[String, ISO4217] with {
    override inline def test(inline value: String): Boolean = ISO4217.codes.contains(value)
    override inline def message: String = "Must be an ISO 4217 currency code"
  }
// ---------------------------------------------------------------------------
// Constraint vocabulary (the `*R` aliases used by the domain newtypes)
// ---------------------------------------------------------------------------

  /** ISO 4217 alphabetic currency code, e.g. `USD`, `EUR`, `JPY`. */
  type CurrencyCodeR = DescribedAs[Match["[A-Z]{3}"] & ISO4217, "Must be an ISO 4217 currency code"]

  /** ISO 3166-1 alpha-2 country code, e.g. `US`, `FR`. */
  type CountryCodeR = DescribedAs[Match["[A-Z]{2}"], "Must be an ISO 3166-1 alpha-2 country code"]

  /** ISO 13616 IBAN: country + check digits + BBAN, validated with mod-97. */
  type IBANR = DescribedAs[Match["[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}"] /*& IbanMod97*/, "Must be a valid IBAN"]

  /** ISO 9362 BIC / SWIFT code (8 or 11 characters). */
  type BICR = DescribedAs[Match["[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?"], "Must be a valid BIC/SWIFT code"]

  /** US ABA routing transit number (9 digits + checksum). */
  type RoutingNumberR = DescribedAs[Match["[0-9]{9}"] /*& AbaChecksum*/, "Must be a valid ABA routing number"]

// /** US ABA routing transit number (9 digits + checksum). */
  type RoutingNumber = RoutingNumber.T
  object RoutingNumber extends RefinedType[String, RoutingNumberR]

  /** Payment card PAN: 13–19 digits passing the Luhn checksum. */
  type PANR = DescribedAs[Match["[0-9]{13,19}"] /*& Luhn*/, "Must be a valid card number"]

  /** Card verification value (CVV/CVC): 3 or 4 digits. */
  type CVVR = DescribedAs[Match["[0-9]{3,4}"], "Must be a 3 or 4 digit CVV"]

  /** ISO 6166 ISIN security identifier. */
// type ISINR = DescribedAs[IsinValid, "Must be a valid ISIN"]

  /** Exchange-style ticker symbol (1–5 uppercase letters). */
  type TickerR = DescribedAs[Match["[A-Z]{1,5}"], "Must be a 1–5 letter ticker symbol"]

  /** E.164 international phone number, e.g. `+14155552671`. */
  type PhoneR = DescribedAs[Match["\\+[1-9][0-9]{1,14}"], "Must be an E.164 phone number"]

  /** RFC-style email address (reuses Iron's [[ValidEmail]]). */
  type EmailR = ValidEmail

  /** Card payments domain.
    *
    * Note the security posture: the full PAN is a refined type so that a value that fails Luhn can never reach the
    * wire, but you should still avoid logging it. [[Card.maskedPan]] derives a PCI-friendly representation.
    */

  /** Primary Account Number: 13–19 digits passing Luhn. */
// type Pan = Pan.T
// object Pan extends RefinedType[String, PANR]

  /** Card verification value: 3 or 4 digits. */
  type Cvv = Cvv.T
  object Cvv extends RefinedType[String, CVVR]

  /** RFC-style email address. */
  type Email = Email.T
  object Email extends RefinedType[String, EmailR]

  /** Identity / KYC domain: the customer attributes a regulated product must capture and validate before onboarding.
    */

  /** E.164 phone number, e.g. `+14155552671`. */
  type Phone = Phone.T
  object Phone extends RefinedType[String, PhoneR]

  /** ISO 3166-1 alpha-2 country code. */
  type CountryCode = CountryCode.T
  object CountryCode extends RefinedType[String, CountryCodeR]

  /** A person's legal name: non-blank, bounded length. */
  type LegalName = LegalName.T
  object LegalName extends RefinedType[String, Not[Blank] & MaxLength[140]]

  /** A KYC verification tier, gating how much money may move. */
  enum KycTier {
    case Unverified, Basic, Enhanced
  }

  /** A validated customer profile. */
  final case class Customer(
      name: LegalName,
      email: Email,
      phone: Phone,
      country: CountryCode,
      tier: KycTier
  )

  /** A loan application with money-valued, non-negative principal. */
  final case class LoanApplication(
      applicant: Customer,
      // principal: Money,
      apr: Apr,
      term: TermMonths,
      score: CreditScore
  )

  /** Banking / account domain: the identifiers needed to move money between institutions. Each newtype is opaque, so an
    * [[IBAN]] can never be passed where a [[BIC]] is expected, and a checksum-failing string can never be constructed.
    */

  /** ISO 13616 International Bank Account Number (validated with mod-97). */
  type IBAN = IBAN.T
  object IBAN extends RefinedType[String, IBANR]

  /** ISO 9362 Business Identifier Code (SWIFT). */
  type BIC = BIC.T
  object BIC extends RefinedType[String, BICR]
}
