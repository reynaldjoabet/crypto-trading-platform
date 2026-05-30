package trading.domain

/** App errors. We split *business* errors (returned to clients with 4xx) from *infra* failures (logged + 5xx). Both
  * carry an `EC` code so dashboards can group them.
  */
sealed trait AppError(val code: String, val message: String) extends Throwable derives CanEqual {
  override def getMessage: String = s"[$code] $message"
}

object AppError {
  // -- 4xx --
  final case class Validation(field: String, reason: String) extends AppError("VALIDATION", s"$field: $reason")
  final case class NotFound(resource: String, id: String) extends AppError("NOT_FOUND", s"$resource $id not found")
  final case class Conflict(reason: String) extends AppError("CONFLICT", reason)
  final case class Forbidden(reason: String) extends AppError("FORBIDDEN", reason)
  final case class Unauthenticated(reason: String) extends AppError("UNAUTHENTICATED", reason)
  final case class KycRequired(level: String) extends AppError("KYC_REQUIRED", level)
  final case class InsufficientFunds(requested: BigDecimal, available: BigDecimal)
      extends AppError("INSUFFICIENT_FUNDS", s"requested=$requested available=$available")

  // -- 5xx --
  final case class Upstream(service: String, reason: String) extends AppError("UPSTREAM", s"$service: $reason")
  final case class Internal(reason: String) extends AppError("INTERNAL", reason)
}
