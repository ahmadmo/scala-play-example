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

package ir.bama.controllers

import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import com.typesafe.config.ConfigObject
import ir.bama.models.CarCategory.CarCategory
import ir.bama.models.CarChassis.CarChassis
import ir.bama.models.CarDifferential.CarDifferential
import ir.bama.models.CarFuelType.CarFuelType
import ir.bama.models.CarGearBox.CarGearBox
import ir.bama.models.CarStatus.CarStatus
import ir.bama.models.PaymentPeriod.PaymentPeriod
import ir.bama.models.PaymentType.PaymentType
import ir.bama.models.SellerType.SellerType
import ir.bama.models.{PaymentType, SellerType, _}
import ir.bama.services.SellAdService
import ir.bama.utils.RangeLike
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{Form, FormError, Mapping}
import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc._

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class SellAdController @Inject()(adService: SellAdService, authController: AuthController, configs: Configuration)
                                (implicit mat: Materializer, ec: ExecutionContext) extends Controller {

  private lazy val filesDir = Paths.get(configs.getString("app.dir.files").get)
  private lazy val maxPhotos = configs.getInt("controllers.ad.maxPhotos").get
  private lazy val maxPhotoSize = configs.getBytes("controllers.ad.maxPhotoSize").get

  import scala.collection.JavaConverters._

  private lazy val limits: Map[String, Map[SellerType, (Int, Int, Option[Int])]] = {
    val ls = for (entry <- configs.getList("controllers.ad.limits").get.asScala) yield {
      val obj = entry.asInstanceOf[ConfigObject]
      (
        obj.get("func").as[String],
        (
          obj.get("seller").asEnum(SellerType),
          (
            obj.get("period").as[Integer].intValue(), obj.get("count").as[Integer].intValue(),
            obj.get("max").asOpt[Integer].map(_.intValue())
          )
        )
      )
    }
    ls.groupBy(_._1).map(x => (x._1, x._2.map(_._2).toMap))
  }

  sealed trait PaymentData {
    def toPayment: Payment
  }

  case class CreditPaymentData(finalPrice: Long) extends PaymentData {
    override def toPayment: Payment = CreditPayment(finalPrice)
  }

  val creditPaymentMapping: Mapping[CreditPaymentData] = mapping(
    "finalPrice" -> longNumber
  )(CreditPaymentData.apply)(CreditPaymentData.unapply)

  case class InstallmentPaymentData(prePaids: Seq[Long], period: PaymentPeriod, ticks: Int,
                                    numberOfPayments: Int, amountPerPayment: Long) extends PaymentData {
    override def toPayment: Payment = InstallmentPayment(Some(prePaids), period, ticks, numberOfPayments, amountPerPayment)
  }

  implicit val paymentPeriodFormatter: Formatter[PaymentPeriod] = Formats.enumFormat(PaymentPeriod)

  val installmentPaymentMapping: Mapping[InstallmentPaymentData] = mapping(
    "prePaids" -> seq(longNumber(min = 1)),
    "period" -> default(of[PaymentPeriod], PaymentPeriod.MONTHLY),
    "ticks" -> default(number(min = 1), 1),
    "numberOfPayments" -> number(min = 1),
    "amountPerPayment" -> longNumber(min = 1)
  )(InstallmentPaymentData.apply)(InstallmentPaymentData.unapply)

  case class CarData(modelId: Long, year: Int,
                     chassis: CarChassis, differential: CarDifferential,
                     category: CarCategory, status: CarStatus, mileage: Int,
                     gearBox: CarGearBox, fuelType: CarFuelType,
                     bodyDescription: String, bodyColor: String, cabinColor: String) {
    def toCar: Car = Car(Some(CarModel.id(modelId)), year, chassis, differential, category, status, mileage, gearBox, fuelType,
      bodyDescription, bodyColor, cabinColor, None)
  }

  implicit val carChassisFormatter: Formatter[CarChassis] = Formats.enumFormat(CarChassis)
  implicit val carDifferentialFormatter: Formatter[CarDifferential] = Formats.enumFormat(CarDifferential)
  implicit val carCategoryFormatter: Formatter[CarCategory] = Formats.enumFormat(CarCategory)
  implicit val carStatusFormatter: Formatter[CarStatus] = Formats.enumFormat(CarStatus)
  implicit val carGearBoxFormatter: Formatter[CarGearBox] = Formats.enumFormat(CarGearBox)
  implicit val carFuelTypeFormatter: Formatter[CarFuelType] = Formats.enumFormat(CarFuelType)

  val carMapping: Mapping[CarData] = mapping(
    "model" -> single("id" -> longNumber),
    "year" -> number(min = 1),
    "chassis" -> default(of[CarChassis], CarChassis.SEDAN),
    "differential" -> default(of[CarDifferential], CarDifferential.FWD),
    "category" -> default(of[CarCategory], CarCategory.NORMAL),
    "status" -> default(of[CarStatus], CarStatus.NEW),
    "mileage" -> default(number(min = 0), 0),
    "gearBox" -> default(of[CarGearBox], CarGearBox.MANUAL),
    "fuelType" -> default(of[CarFuelType], CarFuelType.GASOLINE),
    "bodyDescription" -> nonEmptyText(maxLength = 1000),
    "bodyColor" -> nonEmptyText(maxLength = 255),
    "cabinColor" -> nonEmptyText(maxLength = 255)
  )(CarData.apply)(CarData.unapply)

  case class SellAdData[P <: PaymentData](cityId: Long, venue: String, phoneNumber: Option[String], count: Int, paymentData: P, carData: CarData) {
    def toSellAd: SellAd = SellAd(None, None, Some(City.id(cityId)), venue, phoneNumber, None, LocalDateTime.now(), count, 0, SellAdStatus.SUBMITTED,
      paymentData.toPayment, carData.toCar, None)
  }

  def sellAdForm[P <: PaymentData](paymentMapping: Mapping[P]): Form[SellAdData[P]] = Form(
    mapping(
      "city" -> single("id" -> longNumber),
      "venue" -> nonEmptyText(maxLength = 1000),
      "phoneNumber" -> optional(Mappings.phoneNumber),
      "count" -> default(number(min = 1), 1),
      "payment" -> paymentMapping,
      "car" -> carMapping
    )(SellAdData.apply)(SellAdData.unapply))

  val paymentTypeFormatter: RichFormatter[PaymentType] = Formats.enumFormat(PaymentType)

  def submit: Action[Either[MaxSizeExceeded, MultipartFormData[Files.TemporaryFile]]] =
    authController.authenticated.async(parse.maxLength(maxPhotos * maxPhotoSize, parse.multipartFormData)) { implicit request =>
      request.body match {
        case Left(_) =>
          Err("Max size exceeded.", "maxSize" -> JsNumber(maxPhotos * maxPhotoSize))
            .asJsonError(Results.EntityTooLarge).future
        case Right(data: MultipartFormData[Files.TemporaryFile]) =>
          if (data.files.size > maxPhotos) {
            Err(s"Only $maxPhotos photos allowed.").asJsonError.future
          } else data.asFormUrlEncoded.get("data").filter(_.nonEmpty).map(_.head).map(Json.parse).map { json =>
            paymentTypeFormatter.bind("payment.type", (json \ "payment" \ "type").asOpt[String]) match {
              case Left(errors) => errors.asJsonError(Results.BadRequest).future
              case Right(paymentType) =>
                val paymentMapping = paymentType match {
                  case PaymentType.CREDIT => creditPaymentMapping
                  case PaymentType.INSTALLMENT => installmentPaymentMapping
                }
                sellAdForm(paymentMapping).map(json) { adData =>
                  handleFiles(data.files) match {
                    case Left(errors) => errors.asJsonError(Results.BadRequest).future
                    case Right(files) =>
                      val ad = adData.toSellAd
                      val names = files.map(_._1)
                      adService.submit(request.login.userId, ad.copy(car = ad.car.copy(photos = Some(names))), limits("submit")).onError { _ =>
                        files.foreach(_._2.delete())
                      }
                  }
                }
            }
          }.getOrElse {
            Err("Missing required parameter.", Seq("parameter" -> JsString("data"))).asJsonError.future
          }
      }
    }

  private def handleFiles(files: Seq[MultipartFormData.FilePart[Files.TemporaryFile]]): Either[Seq[FormError], Seq[(String, File)]] = {
    val errors = files.foldLeft(mutable.ListBuffer.empty[FormError]) {
      case (buf, file) =>
        if (!file.contentType.exists(_.startsWith("image/"))) {
          buf += FormError(file.key, "Image file expected.")
        }
        if (file.ref.file.length() > maxPhotoSize) {
          buf += FormError(file.key, "Max size exceeded.", Seq("maxLength" -> JsNumber(maxPhotoSize)))
        }
        buf
    }
    if (errors.isEmpty) Right {
      files.map { file =>
        val extension = if (file.contentType.exists(_.endsWith("png"))) ".png" else ".jpg"
        val name = UUID.randomUUID().toString + extension
        (name, file.ref.moveTo(filesDir.resolve(name).toFile))
      }
    } else Left {
      errors
    }
  }

  def resubmit(id: Long): Action[AnyContent] = authController.authenticated.async { request =>
    adService.resubmit(request.login.userId, id, limits("resubmit")).asyncResult
  }

  def cancel(id: Long): Action[AnyContent] = authController.authenticated.async { request =>
    adService.cancel(request.login.userId, id).asyncResult
  }

  def load(id: Long): Action[AnyContent] = authController.maybeAuthenticated.async { request =>
    adService.load(id, request.login.map(_.userId)).map(_.map(refineResult)).asJson
  }

  def list(offset: Int, length: Int): Action[AnyContent] = authController.maybeAuthenticated.async { request =>
    adService.list(request.login.map(_.userId), offset ~ length).map(_.map(refineResult)).asJson
  }

  def listBySellerId(sellerId: Long, offset: Int, length: Int): Action[AnyContent] = authController.maybeAuthenticated.async { request =>
    adService.listBySellerId(sellerId, request.login.map(_.userId), offset ~ length).map(_.map(refineResult)).asJson
  }

  private val refineResult: ((SellAd, Boolean)) => Map[String, JsValue] = {
    case (ad, owner) => Map("ad" -> Json.toJson(ad), "owner" -> JsBoolean(owner))
  }

  def incrementViews(id: Long): Action[AnyContent] = Action.async {
    adService.incrementViews(id).map { success =>
      if (success) id.saved else Err("Entity not found.").asJsonError(Results.NotFound)
    }
  }

  def incrementPhoneNumberViews(id: Long): Action[AnyContent] = Action.async {
    adService.incrementPhoneNumberViews(id).map { success =>
      if (success) id.saved else Err("Entity not found.").asJsonError(Results.NotFound)
    }
  }

}
