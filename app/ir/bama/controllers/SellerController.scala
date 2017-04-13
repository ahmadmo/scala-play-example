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

import java.nio.file.Paths
import java.util.{Date, UUID}
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import ir.bama.models.SellerType.SellerType
import ir.bama.models._
import ir.bama.services.SellerService
import ir.bama.utils.{PasswordLike, RangeLike}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class SellerController @Inject()(sellerService: SellerService, authController: AuthController, configs: Configuration)
                                (implicit mat: Materializer, ec: ExecutionContext) extends Controller {

  private lazy val filesDir = Paths.get(configs.getString("app.dir.files").get)
  private lazy val maxPhotoLength = configs.getBytes("controllers.seller.maxPhotoLength").get

  val privateSellerForm: Form[Boolean] = Form(single(
    "publicProfile" -> default(boolean, false)
  ))

  case class DealerData(manager: String, licenceNumber: String)

  val dealerForm: Form[DealerData] = Form(mapping(
    "manager" -> nonEmptyText(2, 120),
    "licenceNumber" -> nonEmptyText(10, 10)
  )(DealerData.apply)(DealerData.unapply))

  case class UserData(username: String, password: String) {
    def toUser: User = User(None, username, password.bcrypt)
  }

  case class SellerData(userData: UserData, name: String, cityId: Long, address: String, phoneNumbers: Seq[String]) {

    def toPrivateSeller(publicProfile: Boolean): PrivateSeller = PrivateSeller(
      None, Some(userData.toUser), name, new Date(), Some(City.id(cityId)), address, None,
      Some(phoneNumbers.distinct), publicProfile)

    def toDealer(dealerData: DealerData): Dealer = Dealer(
      None, Some(userData.toUser), name, new Date(), Some(City.id(cityId)), address, None,
      Some(phoneNumbers.distinct), dealerData.manager, dealerData.licenceNumber)

  }

  val sellerForm: Form[SellerData] = Form(
    mapping(
      "user" -> mapping(
        "username" -> nonEmptyText(3, 120),
        "password" -> nonEmptyText(8, 120)
      )(UserData.apply)(UserData.unapply),
      "name" -> nonEmptyText(2, 120),
      "city" -> single("id" -> longNumber),
      "address" -> nonEmptyText(maxLength = 1000),
      "phoneNumbers" -> seq(Mappings.phoneNumber)
        .verifying("phoneNumbers cannot be empty", _.nonEmpty)
    )(SellerData.apply)(SellerData.unapply))

  implicit val sellerTypeFormatter: RichFormatter[SellerType] = Formats.enumFormat(SellerType)

  def register: Action[JsValue] = Action.async(parse.json) { implicit request =>
    sellerTypeFormatter.bind("type", (request.body \ "type").asOpt[String]) match {
      case Right(sellerType) => sellerType match {
        case SellerType.PRIVATE => (sellerForm, privateSellerForm).map {
          case (sellerData, publicProfile) => sellerService.persist(sellerData.toPrivateSeller(publicProfile)).saved
        }
        case SellerType.DEALER => (sellerForm, dealerForm).map {
          case (sellerData, dealerData) => sellerService.persist(sellerData.toDealer(dealerData)).saved
        }
      }
      case Left(errors) => errors.asJson(Results.BadRequest).future
    }
  }

  def uploadPhoto: Action[Either[MaxSizeExceeded, MultipartFormData[Files.TemporaryFile]]] =
    authController.authenticated.async(parse.maxLength(maxPhotoLength, parse.multipartFormData)) { request =>
      request.body match {
        case Left(_) => Map("maxLength" -> maxPhotoLength).asJson(Results.EntityTooLarge).future
        case Right(data) => data.file("photo").map { photo =>
          photo.contentType.filter(_.startsWith("image/")).map { c =>
            if (c.endsWith("png")) ".png" else ".jpg"
          }.map { extension =>
            val name = UUID.randomUUID().toString + extension
            val file = photo.ref.moveTo(filesDir.resolve(name).toFile)
            sellerService.updatePhoto(request.login.userId, name).savedOrElse {
              // shouldn't happen
              file.delete()
              Results.InternalServerError
            }
          } getOrElse BadRequest("Image file expected").future
        } getOrElse BadRequest("Missing File").future
      }
    }

  def deletePhoto: Action[AnyContent] = authController.authenticated.async { request =>
    sellerService.deletePhoto(request.login.userId).map {
      case Some((sellerId, maybePhoto)) =>
        maybePhoto.foreach { photo =>
          java.nio.file.Files.delete(filesDir.resolve(photo))
        }
        sellerId.saved
      case _ => InternalServerError /* shouldn't happen */
    }
  }

  def load(id: Long): Action[AnyContent] = Action.async {
    sellerService.load(id).asJson
  }

  def loadByUser: Action[AnyContent] = authController.authenticated.async { request =>
    sellerService.loadByUserId(request.login.userId).asJson
  }

  def list(offset: Int, length: Int): Action[AnyContent] = Action.async {
    sellerService.list(offset ~ length).asJson
  }

  def listByType(sellerType: String, offset: Int, length: Int): Action[AnyContent] = Action.async {
    (sellerType match {
      case "private" => Some(SellerType.PRIVATE)
      case "dealer" => Some(SellerType.DEALER)
      case _ => None
    }) match {
      case Some(t) => sellerService.listByType(t, offset ~ length).asJson
      case _ => BadRequest("Invalid Seller Type").future
    }
  }

  def listByLocation(location: String, locationId: Long, offset: Int, length: Int): Action[AnyContent] = Action.async {
    (location match {
      case "province" => Some {
        sellerService.listByProvinceId(locationId, offset ~ length)
      }
      case "city" => Some {
        sellerService.listByCityId(locationId, offset ~ length)
      }
      case _ => None
    }) match {
      case Some(future) => future.asJson
      case _ => BadRequest("Invalid Location").future
    }
  }

  def listByTypeAndLocation(sellerType: String, location: String, locationId: Long, offset: Int, length: Int): Action[AnyContent] = Action.async {
    (sellerType match {
      case "private" => Some(SellerType.PRIVATE)
      case "dealer" => Some(SellerType.DEALER)
      case _ => None
    }) match {
      case Some(t) =>
        (location match {
          case "province" => Some {
            sellerService.listByTypeAndProvinceId(t, locationId, offset ~ length)
          }
          case "city" => Some {
            sellerService.listByTypeAndCityId(t, locationId, offset ~ length)
          }
          case _ => None
        }) match {
          case Some(future) => future.asJson
          case _ => BadRequest("Invalid Location").future
        }
      case _ => BadRequest("Invalid Seller Type").future
    }
  }

}
