/*
 * Copyright 2017 Ahmad Mozafarnia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ir.bama

import com.typesafe.config.ConfigValue
import ir.bama.services.ServiceError
import play.api.data.Forms.nonEmptyText
import play.api.data.format.Formatter
import play.api.data.validation.Constraints
import play.api.data.{Form, FormError, Mapping}
import play.api.http.ContentTypes
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Results._
import play.api.mvc.{Codec, Request, Result, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * @author ahmad
  */
package object controllers {

  // @formatter:off

  implicit val utf8: Codec = Codec.utf_8
  val json: String = ContentTypes.withCharset(ContentTypes.JSON)

  implicit val formErrorWrites: Writes[FormError] = Writes[FormError] { error =>
    error.args.foldLeft(Json.obj(
      "key" -> error.key,
      "message" -> error.message // TODO: get error message from messages api
    )) {
      case (js, (key: String, value: JsValue)) => js ++ Json.obj(key -> value)
      case (js, _) => js
    }
  }

  case class Err(message: String, args: Seq[(String, JsValue)] = Nil) {
    def this(message: String, arg: (String, JsValue)) = this(message, Seq(arg))
  }

  object Err {
    def apply(message: String, arg: (String, JsValue)): Err = new Err(message, arg)
  }

  implicit val errWrites: Writes[Err] = Writes[Err] { error =>
    error.args.foldLeft(Json.obj(
      "message" -> error.message // TODO: get error message from messages api
    )) {
      case (js, (key, value)) => js ++ Json.obj(key -> value)
    }
  }

  implicit class FutureLike[A](val x: A) extends AnyVal {
    def future: Future[A] = Future.successful(x)
  }

  implicit class ResultLike[A](val x: A) extends AnyVal {
    def asJson(implicit tjs: Writes[A]): Result = asJson(Ok)
    def asJson(status: Status)(implicit tjs: Writes[A]): Result = status(Json.toJson(x)).as(json)
  }

  implicit class MaybeResultLike[A](val x: Option[A]) extends AnyVal {
    def asJson(implicit tjs: Writes[A]): Result = asJson(Ok)
    def asJson(status: Status)(implicit tjs: Writes[A]): Result = x.map { o =>
      status(Json.toJson(o)).as(json)
    } getOrElse NotFound
  }

  implicit class SimpleSaveResultLike(val id: Long) extends AnyVal {
    def saved: Result = saved(Ok)
    def saved(status: Status): Result = status(Json.obj("id" -> id)).as(json)
  }

  implicit class MaybeSimpleSaveResultLike(val maybeId: Option[Long]) extends AnyVal {
    def saved: Result = saved(Ok)
    def saved(status: Status): Result = savedOrElse(status, NotFound)
    def savedOrElse(alternative: => Status): Result = savedOrElse(Ok, alternative)
    def savedOrElse(status: Status, alternative: => Status): Result = maybeId.map { id =>
      status(Json.obj("id" -> id)).as(json)
    } getOrElse alternative
  }

  implicit class ComplexSaveResultLike(val e: services.PersistenceResult) extends AnyVal {
    def result: Result = maybeSuccess(_.saved)
    def maybeSuccess(block: (Option[Long]) => Result): Result = foldResult(block, error => Results.Status(error.statusCode))
    def onError(block: (ServiceError) => Unit): Result = handleError { error =>
      block(error)
      Results.Status(error.statusCode)
    }
    def handleError(block: (ServiceError) => Status): Result = foldResult(_.saved, block)
    def foldResult(maybeSuccess: (Option[Long]) => Result, onError: (ServiceError) => Status): Result = e match {
      case Left(error) => Err(error.message).asJsonError(onError(error))
      case Right(id) => maybeSuccess(id)
    }
  }

  implicit class ErrResultLike(val x: Err) extends AnyVal {
    def asJsonError: Result = asJsonError(BadRequest)
    def asJsonError(status: Status): Result = Seq(x).asJsonError(status)
  }

  implicit class ErrSeqResultLike(val xs: Seq[Err]) extends AnyVal {
    def asJsonError: Result = asJsonError(BadRequest)
    def asJsonError(status: Status): Result = Map("errors" -> xs).asJson(status)
  }

  implicit class FormErrorResultLike(val x: FormError) extends AnyVal {
    def asJsonError: Result = asJsonError(BadRequest)
    def asJsonError(status: Status): Result = Seq(x).asJsonError(status)
  }

  implicit class FormErrorSeqResultLike(val xs: Seq[FormError]) extends AnyVal {
    def asJsonError: Result = asJsonError(BadRequest)
    def asJsonError(status: Status): Result = Map("errors" -> xs).asJson(status)
  }

  implicit class FutureResultLike[A](val f: Future[A]) extends AnyVal {
    def asJson(implicit tjs: Writes[A], ec: ExecutionContext): Future[Result] = f.map(_.asJson)
    def asJson(status: Status)(implicit tjs: Writes[A], ec: ExecutionContext): Future[Result] = f.map(_.asJson(status))
  }

  implicit class FutureMaybeResultLike[A](val f: Future[Option[A]]) extends AnyVal {
    def asJson(implicit tjs: Writes[A], ec: ExecutionContext): Future[Result] = f.map(_.asJson)
    def asJson(status: Status)(implicit tjs: Writes[A], ec: ExecutionContext): Future[Result] = f.map(_.asJson(status))
  }

  implicit class FutureSimpleSaveResultLike(val f: Future[Long]) extends AnyVal {
    def asyncResult(implicit ec: ExecutionContext): Future[Result] = f.map(_.saved)
    def asyncResult(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.saved(status))
  }

  implicit class FutureMaybeSimpleSaveResultLike(val f: Future[Option[Long]]) extends AnyVal {
    def asyncResult(implicit ec: ExecutionContext): Future[Result] = f.map(_.saved)
    def asyncResult(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.saved(status))
    def savedOrElse(alternative: => Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.savedOrElse(alternative))
    def savedOrElse(status: Status, alternative: => Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.savedOrElse(status, alternative))
  }

  implicit class FutureComplexSaveResultLike(val f: Future[services.PersistenceResult]) extends AnyVal {
    def asyncResult(implicit ec: ExecutionContext): Future[Result] = f.map(_.result)
    def maybeSuccess(block: (Option[Long]) => Result)(implicit ec: ExecutionContext): Future[Result] = f.map(_.maybeSuccess(block))
    def onError(block: (ServiceError) => Unit)(implicit ec: ExecutionContext): Future[Result] = f.map(_.onError(block))
    def handleError(block: (ServiceError) => Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.handleError(block))
    def foldResultAsync(maybeSuccess: (Option[Long]) => Result, onError: (ServiceError) => Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.foldResult(maybeSuccess, onError))
  }

  implicit class FutureErrResultLike(val f: Future[Err]) extends AnyVal {
    def asJsonError(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError)
    def asJsonError(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError(status))
  }

  implicit class FutureErrSeqResultLike(val f: Future[Seq[Err]]) extends AnyVal {
    def asJsonError(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError)
    def asJsonError(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError(status))
  }

  implicit class FutureFormErrorResultLike(val f: Future[FormError]) extends AnyVal {
    def asJsonError(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError)
    def asJsonError(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError(status))
  }

  implicit class FutureFormErrorSeqResultLike(val f: Future[Seq[FormError]]) extends AnyVal {
    def asJsonError(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError)
    def asJsonError(status: Status)(implicit ec: ExecutionContext): Future[Result] = f.map(_.asJsonError(status))
  }

  implicit class FormLike[A](val form: Form[A]) extends AnyVal {
    def map(data: JsValue)(block: (A) => Future[Result]): Future[Result] = fold(form.bind(data), block)
    def map(data: Map[String, Seq[String]])(block: (A) => Future[Result]): Future[Result] = fold(form.bindFromRequest(data), block)
    def map(block: (A) => Future[Result])(implicit r: Request[_]): Future[Result] = fold(form.bindFromRequest, block)
    private def fold(form: Form[A], block: (A) => Future[Result]) = form.fold(
      formWithErrors => formWithErrors.errors.asJsonError.future,
      block.apply
    )
  }

  implicit class CombinedFormLike[A, B](val forms: (Form[A], Form[B])) extends AnyVal {
    def map(data: JsValue)(block: (A, B) => Future[Result]): Future[Result] = fold(forms._1.bind(data), forms._2.bind(data), block)
    def map(data: Map[String, Seq[String]])(block: (A, B) => Future[Result]): Future[Result] = fold(forms._1.bindFromRequest(data), forms._2.bindFromRequest(data), block)
    def map(block: (A, B) => Future[Result])(implicit r: Request[_]): Future[Result] = fold(forms._1.bindFromRequest, forms._2.bindFromRequest, block)
    private def fold(lf: Form[A], rf: Form[B], block: (A, B) => Future[Result]) = {
      (
        lf.fold(formWithErrors => Left(formWithErrors.errors), Right(_)),
        rf.fold(formWithErrors => Left(formWithErrors.errors), Right(_))
      ) match {
        case (Left(le), Left(re)) => (le ++ re).asJsonError.future
        case (Left(le), Right(_)) => le.asJsonError.future
        case (Right(_), Left(re)) => re.asJsonError.future
        case (Right(a), Right(b)) => block(a, b)
      }
    }
  }

  trait RichFormatter[T] extends Formatter[T] {
    def bind(key: String, value: Option[String]): Either[Seq[FormError], T]
  }

  object Formats {

    def enumFormat[E <: Enumeration](enum: E): RichFormatter[E#Value] = new RichFormatter[E#Value] {
      override val format = Some(("format.enumeration", Nil))
      override def bind(key: String, value: Option[String]): Either[Seq[FormError], E#Value] =
        value.map { v =>
          try {
            Right(enum.withName(v))
          } catch {
            case _: NoSuchElementException => Left(Seq(FormError(key, "error.enumeration")))
          }
        } getOrElse Left(Seq(FormError(key, "error.required")))
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], E#Value] = bind(key, data.get(key))
      def unbind(key: String, value: E#Value) = Map(key -> value.toString)
    }

  }

  object Mappings {

    val phoneNumber: Mapping[String] = nonEmptyText.verifying(Constraints.pattern(
      regex = "^(?:\\+98|0)\\s?\\d{3}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}$".r,
      error = "Invalid Phone Number"))

  }

  implicit class ConfigValueLike(val c: ConfigValue) extends AnyVal {
    def asOpt[T: ClassTag](implicit ct: ClassTag[T]): Option[T] =
      Option(c).map(value => ct.runtimeClass.cast(value.unwrapped()).asInstanceOf[T])
    def as[T: ClassTag]: T = asOpt[T].get
    def asEnumOpt[E <: Enumeration](enum: E): Option[E#Value] = {
      asOpt[String].map(s => enum.withName(s.toUpperCase))
    }
    def asEnum[E <: Enumeration](enum: E): E#Value = asEnumOpt(enum).get
  }

  // @formatter:on

}
