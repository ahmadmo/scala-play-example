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

import ir.bama.models.SellerType.SellerType
import ir.bama.utils.{Dates, Enums}
import play.api.libs.json._
import shapeless.{MkFieldLens, Witness, lens}

/**
  * @author ahmad
  */
abstract class Seller[T](val `type`: SellerType)
                        (implicit mkUserLens: MkFieldLens.Aux[T, Witness.`'user`.T, Option[User]],
                         mkCityLens: MkFieldLens.Aux[T, Witness.`'city`.T, Option[City]],
                         mkPhoneNumbersLens: MkFieldLens.Aux[T, Witness.`'phoneNumbers`.T, Option[Seq[String]]])
  extends Identifiable {

  self: T =>

  val user: Option[User]
  val name: String
  val registrationDate: LocalDateTime
  val city: Option[City]
  val address: String
  val photo: Option[String]
  val phoneNumbers: Option[Seq[String]]

  // TODO: use Quicklens instead
  private val userLens = lens[T] >> 'user
  private val cityLens = lens[T] >> 'city
  private val phoneNumbersLens = lens[T] >> 'phoneNumbers

  final def withUser(user: User): T = userLens.modify(self)(_ => Some(user))

  final def withCity(city: City): T = cityLens.modify(self)(_ => Some(city))

  final def withPhoneNumbers(numbers: Seq[String]): T = phoneNumbersLens.modify(self)(_ => Some(numbers))

}

object Seller {

  def id(id: Long): Seller[_] =
    PrivateSeller(Some(id), None, null, null, None, null, None, None, publicProfile = false)

  def phoneNumbers(numbers: Option[Seq[String]]): Seller[_] =
    PrivateSeller(None, None, null, null, None, null, None, numbers, publicProfile = false)

  private val reads: Reads[Seller[_]] = Reads[Seller[_]] {
    case o: JsObject =>
      (o \ "type").toOption.map {
        SellerType.format.reads(_) flatMap {
          case SellerType.PRIVATE => PrivateSeller.format.reads(o)
          case SellerType.DEALER => Dealer.format.reads(o)
        }
      } getOrElse JsError("Could not find property: type")
    case _ => JsError("Json object expected")
  }

  private val writes: Writes[Seller[_]] = Writes[Seller[_]] { o =>
    (o match {
      case x: PrivateSeller => Json.toJson(x)(PrivateSeller.format)
      case x: Dealer => Json.toJson(x)(Dealer.format)
    }).as[JsObject] + ("type" -> Json.toJson(o.`type`))
  }

  implicit val format: Format[Seller[_]] = Format(reads, writes)

}

object SellerType extends Enumeration {

  val PRIVATE = Value("PRIVATE")
  val DEALER = Value("DEALER")
  type SellerType = Value

  implicit val format: Format[SellerType] = Enums.enumFormat(SellerType)

}

case class PrivateSeller(id: Option[Long], user: Option[User], name: String, registrationDate: LocalDateTime,
                         city: Option[City], address: String, photo: Option[String],
                         phoneNumbers: Option[Seq[String]], publicProfile: Boolean)
  extends Seller[PrivateSeller](SellerType.PRIVATE)

object PrivateSeller {
  implicit val dateFormat: Format[LocalDateTime] = Dates.dateFormat
  implicit val format: OFormat[PrivateSeller] = Json.format[PrivateSeller]
}

case class Dealer(id: Option[Long], user: Option[User], name: String, registrationDate: LocalDateTime,
                  city: Option[City], address: String, photo: Option[String],
                  phoneNumbers: Option[Seq[String]], manager: String, licenceNumber: String)
  extends Seller[Dealer](SellerType.DEALER)

object Dealer {
  implicit val dateFormat: Format[LocalDateTime] = Dates.dateFormat
  implicit val format: OFormat[Dealer] = Json.format[Dealer]
}
