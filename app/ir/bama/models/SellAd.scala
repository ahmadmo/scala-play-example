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

package ir.bama.models

import java.time.LocalDateTime

import ir.bama.models.CarCategory.CarCategory
import ir.bama.models.CarChassis.CarChassis
import ir.bama.models.CarDifferential.CarDifferential
import ir.bama.models.CarFuelType.CarFuelType
import ir.bama.models.CarGearBox.CarGearBox
import ir.bama.models.CarStatus.CarStatus
import ir.bama.models.PaymentPeriod.PaymentPeriod
import ir.bama.models.PaymentType.PaymentType
import ir.bama.models.SellAdStatus.SellAdStatus
import ir.bama.utils.{Dates, Enums}
import play.api.libs.json._

/**
  * @author ahmad
  */
case class SellAd(id: Option[Long], seller: Option[Seller[_]], city: Option[City],
                  venue: String, phoneNumber: Option[String],
                  submissionDates: Option[Seq[LocalDateTime]], lastSubmissionDate: LocalDateTime,
                  count: Int, soldCount: Int, status: SellAdStatus,
                  payment: Payment, car: Car, stats: Option[SellAdStats]) extends Identifiable

object SellAd {
  implicit val dateFormat: Format[LocalDateTime] = Dates.dateFormat
  implicit val format: OFormat[SellAd] = Json.format[SellAd]
}

abstract class Payment(val `type`: PaymentType) {
  val initialPrice: Long
  val finalPrice: Long
}

object Payment {

  private val reads: Reads[Payment] = Reads[Payment] {
    case o: JsObject =>
      (o \ "type").toOption.map {
        PaymentType.format.reads(_) flatMap {
          case PaymentType.CREDIT => CreditPayment.format.reads(o)
          case PaymentType.INSTALLMENT => InstallmentPayment.format.reads(o)
        }
      } getOrElse JsError("Could not find property: type")
    case _ => JsError("Json object expected")
  }

  private val writes: Writes[Payment] = Writes[Payment] { o =>
    (o match {
      case x: CreditPayment => Json.toJson(x)(CreditPayment.format).as[JsObject] + ("initialPrice" -> JsNumber(x.initialPrice))
      case x: InstallmentPayment => Json.toJson(x)(InstallmentPayment.format).as[JsObject] ++ Json.obj(
        "initialPrice" -> JsNumber(x.initialPrice),
        "finalPrice" -> JsNumber(x.finalPrice)
      )
    }).as[JsObject] + ("type" -> Json.toJson(o.`type`))
  }

  implicit val format: Format[Payment] = Format(reads, writes)

}

object PaymentType extends Enumeration {

  val CREDIT = Value("CREDIT")
  val INSTALLMENT = Value("INSTALLMENT")
  type PaymentType = Value

  implicit val format: Format[PaymentType] = Enums.enumFormat(PaymentType)

}

object PaymentPeriod extends Enumeration {

  val DAILY = new Period("DAILY", 1)
  val WEEKLY = new Period("WEEKLY", 7)
  val MONTHLY = new Period("MONTHLY", 30)
  type PaymentPeriod = Value

  protected class Period(name: String, factor: Int) extends Val(name) {
    def toDays(count: Int): Int = count * factor
  }

  implicit def valueToPeriod(value: Value): Period = value.asInstanceOf[Period]

  implicit val format: Format[PaymentPeriod] = Enums.enumFormat(PaymentPeriod)

}

case class CreditPayment(finalPrice: Long) extends Payment(PaymentType.CREDIT) {
  override val initialPrice: Long = finalPrice
}

object CreditPayment {
  implicit val format: OFormat[CreditPayment] = Json.format[CreditPayment]
}

case class InstallmentPayment(prepayments: Option[Seq[Long]], period: PaymentPeriod, ticks: Int,
                              numberOfInstallments: Int, amountPerInstallment: Long)
  extends Payment(PaymentType.INSTALLMENT) {

  override lazy val initialPrice: Long = prepayments.map(_.sum).getOrElse(0L)
  override lazy val finalPrice: Long = initialPrice + numberOfInstallments * amountPerInstallment

}

object InstallmentPayment {

  implicit val format: OFormat[InstallmentPayment] = Json.format[InstallmentPayment]

  def apply(ip: Long, fp: Long, period: PaymentPeriod, ticks: Int,
            numberOfInstallments: Int, amountPerInstallment: Long): InstallmentPayment =
    new InstallmentPayment(None, period, ticks, numberOfInstallments, amountPerInstallment) {
      override lazy val initialPrice: Long = ip
      override lazy val finalPrice: Long = fp
    }

}

object SellAdStatus extends Enumeration {

  val SUBMITTED = Value("SUBMITTED")
  val RESUBMITTED = Value("RESUBMITTED")
  val CANCELLED = Value("CANCELLED")
  val SOLD_OUT = Value("SOLD_OUT")
  type SellAdStatus = Value

  implicit val format: Format[SellAdStatus] = Enums.enumFormat(SellAdStatus)

}

case class Car(model: Option[CarModel], year: Int,
               chassis: CarChassis, differential: CarDifferential,
               category: CarCategory, status: CarStatus, mileage: Int,
               gearBox: CarGearBox, fuelType: CarFuelType,
               bodyDescription: String, bodyColor: String, cabinColor: String,
               photos: Option[Seq[String]])

object Car {
  implicit val format: OFormat[Car] = Json.format[Car]
}

case class CarBrand(id: Option[Long], name: String)

object CarBrand {

  implicit val format: OFormat[CarBrand] = Json.format[CarBrand]

  def id(id: Long) = CarBrand(Some(id), null)

}

case class CarModel(id: Option[Long], brand: Option[CarBrand], name: String)

object CarModel {

  implicit val format: OFormat[CarModel] = Json.format[CarModel]

  def id(id: Long) = CarModel(Some(id), None, null)

}

object CarChassis extends Enumeration {

  val SEDAN = Value("SEDAN")
  val SUV = Value("SUV")
  val PICKUP = Value("PICKUP")
  val COUPE = Value("COUPE")
  val CONVERTIBLE = Value("CONVERTIBLE")
  val VAN = Value("VAN")
  type CarChassis = Value

  implicit val format: Format[CarChassis] = Enums.enumFormat(CarChassis)

}

object CarCategory extends Enumeration {

  val NORMAL = Value("NORMAL")
  val FREE_ZONE = Value("FREE_ZONE")
  val TEMPORARY = Value("TEMPORARY")
  val COLLECTIBLE = Value("COLLECTIBLE")
  type CarCategory = Value

  implicit val format: Format[CarCategory] = Enums.enumFormat(CarCategory)

}

object CarStatus extends Enumeration {

  val NEW = Value("NEW")
  val USED = Value("USED")
  val CARD_INDEX = Value("CARD_INDEX")
  val DRAFT = Value("DRAFT")
  type CarStatus = Value

  implicit val format: Format[CarStatus] = Enums.enumFormat(CarStatus)

}

object CarGearBox extends Enumeration {

  val MANUAL = Value("MANUAL")
  val AUTO = Value("AUTO")
  type CarGearBox = Value

  implicit val format: Format[CarGearBox] = Enums.enumFormat(CarGearBox)

}

object CarFuelType extends Enumeration {

  val GASOLINE = Value("GASOLINE")
  val HYBRID = Value("HYBRID")
  val BI_FUEL = Value("BI_FUEL")
  val DIESEL = Value("DIESEL")
  type CarFuelType = Value

  implicit val format: Format[CarFuelType] = Enums.enumFormat(CarFuelType)

}

object CarDifferential extends Enumeration {

  val FWD = Value("FWD")
  val RWD = Value("RWD")
  val `4WD` = Value("4WD")
  type CarDifferential = Value

  implicit val format: Format[CarDifferential] = Enums.enumFormat(CarDifferential)

}

case class SellAdStats(adId: Long, adViews: Int, phoneNumberViews: Int)

object SellAdStats {
  implicit val format: OFormat[SellAdStats] = Json.format[SellAdStats]
}
