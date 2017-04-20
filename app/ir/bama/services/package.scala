package ir.bama

import play.api.http.Status

/**
  * @author ahmad
  */
package object services {

  // @formatter:off

  type PersistenceResult = Either[ServiceError, Option[Long]]

  case class ServiceError(message: String, statusCode: Int = Status.BAD_REQUEST)

  implicit def error(message: String): ServiceError = ServiceError(message)

  implicit class ErrorLike(val message: String) extends AnyVal {
    def error: ServiceError = ServiceError(message)
    def error(statusCode: Int): ServiceError = ServiceError(message, statusCode)
  }

  // @formatter:on

}
