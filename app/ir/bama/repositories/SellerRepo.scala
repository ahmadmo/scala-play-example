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

package ir.bama.repositories

import java.util.Date
import javax.inject.{Inject, Singleton}

import ir.bama.models.SellerType.SellerType
import ir.bama.models._
import ir.bama.utils.Range
import play.api.db.slick.DatabaseConfigProvider
import shapeless.syntax.std.tuple._
import slick.lifted.{ColumnOrdered, ForeignKeyQuery, PrimaryKey, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class SellerRepo @Inject()(dbConfigProvider: DatabaseConfigProvider, cityRepo: CityRepo, userRepo: UserRepo)
                          (implicit ec: ExecutionContext) extends BaseRepo[Seller[_]](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  implicit val sellerTypeMapper: BaseColumnType[SellerType] = enumMapper(SellerType)

  private type SellerRow = (Option[Long], SellerType, Long, String, Date, Long, String, Option[String],
    Option[Boolean], Option[String], Option[String])

  class SellerTable(tag: Tag) extends Table[Seller[_]](tag, "T_SELLER") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def sellerType: Rep[SellerType] = column[SellerType]("C_TYPE", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def userId: Rep[Long] = column[Long]("C_USER_ID", NotNull)

    def user: ForeignKeyQuery[_, User] = foreignKey("FK_USER_SELLER", userId, userRepo.query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def name: Rep[String] = column[String]("C_NAME", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def registrationDate: Rep[Date] = column[Date]("C_REGISTRATION_DATE", NotNull)

    def cityId: Rep[Long] = column[Long]("C_CITY_ID", NotNull)

    def city: ForeignKeyQuery[_, City] = foreignKey("FK_CITY_SELLER", cityId, cityRepo.query)(_.id)

    def address: Rep[String] = column[String]("C_ADDRESS", O.SqlType("TEXT"), NotNull)

    def photo: Rep[String] = column[String]("C_PHOTO", O.SqlType("VARCHAR"), O.Length(255), Nullable)

    def publicProfile: Rep[Boolean] = column[Boolean]("C_PUBLIC_PROFILE", Nullable)

    def manager: Rep[String] = column[String]("C_MANAGER", O.SqlType("VARCHAR"), O.Length(255), Nullable)

    def licenceNumber: Rep[String] = column[String]("C_LICENCE_NUMBER", O.SqlType("VARCHAR"), O.Length(255), Nullable)

    override def * : ProvenShape[Seller[_]] =
      (id.?, sellerType, userId, name, registrationDate, cityId, address,
        photo.?, publicProfile.?, manager.?, licenceNumber.?) <> (toSeller, fromSeller)

  }

  type PhoneNumber = (Long, Int, String)

  class PhoneNumberTable(tag: Tag) extends Table[PhoneNumber](tag, "T_SELLER_PHONE_NUMBER") {

    def sellerId: Rep[Long] = column[Long]("C_SELLER_ID", NotNull)

    def seller: ForeignKeyQuery[_, Seller[_]] = foreignKey("FK_SELLER_PHONE_NUMBER", sellerId, query)(_.id,
      onDelete = ForeignKeyAction.Cascade)

    def order: Rep[Int] = column[Int]("C_ORDER", NotNull)

    def phoneNumber: Rep[String] = column[String]("C_PHONE_NUMBER", O.SqlType("VARCHAR"), O.Length(20), NotNull)

    def pk: PrimaryKey = primaryKey("PK_SELLER_PHONE_NUMBER", (sellerId, phoneNumber))

    override def * : ProvenShape[PhoneNumber] = (sellerId, order, phoneNumber)

  }

  private val toSeller: SellerRow => Seller[_] = {
    case row@(id, sellerType, _, name, rd, _, address, photo, maybePublic, maybeManager, maybeLicence) =>
      sellerType match {
        case SellerType.PRIVATE => maybePublic match {
          case Some(publicProfile) => PrivateSeller(id, None, name, rd, None, address, photo, None, publicProfile)
          case _ => throw new IllegalStateException(s"Invalid row: $row")
        }
        case SellerType.DEALER => (maybeManager, maybeLicence) match {
          case (Some(manager), Some(licence)) => Dealer(id, None, name, rd, None, address, photo, None, manager, licence)
          case _ => throw new IllegalStateException(s"Invalid row: $row")
        }
        case _ => throw new IllegalStateException(s"Invalid row: $row")
      }
  }

  private val fromSeller: Seller[_] => Option[SellerRow] = { x =>
    val row = (x.id, x.`type`, x.user.flatMap(_.id).get, x.name, x.registrationDate, x.city.flatMap(_.id).get, x.address, x.photo)
    x match {
      case p: PrivateSeller => Some(row ++ (Some(p.publicProfile), None, None))
      case d: Dealer => Some(row ++ (None, Some(d.manager), Some(d.licenceNumber)))
      case _ => None
    }
  }

  override type TableType = SellerTable
  override val query: TableQuery[SellerTable] = TableQuery[SellerTable]
  override protected val idColumn: (SellerTable) => Rep[Long] = _.id

  val phoneNumbers: TableQuery[PhoneNumberTable] = TableQuery[PhoneNumberTable]

  override def persist(seller: Seller[_]): DBIO[Long] = {
    seller.user match {
      case Some(user) =>
        val insertSeller = userRepo.persist(user).flatMap { userId =>
          super.persist {
            seller.withUser(user.copy(id = Some(userId))).asInstanceOf[Seller[_]]
          }
        }
        seller.phoneNumbers match {
          case Some(numbers) =>
            insertSeller.flatMap { sellerId =>
              (phoneNumbers ++= numbers.zipWithIndex.map {
                case (number, idx) => (sellerId, idx, number)
              }).map(_ => sellerId)
            }.transactionally
          case _ => throw new IllegalStateException
        }
      case _ => throw new IllegalStateException
    }
  }

  override def load(id: Rep[Long]): DBIO[Option[Seller[_]]] =
    query.filter(s => s.id === id && isPublic(s)).map(s => (s, s.cityId)).result.headOption.flatMap {
      case Some(row) => refineSeller(row).map(Some(_))
      case _ => DBIO.successful(None)
    }

  def findIdAndTypeByUserId(userId: Long): DBIO[Option[(Long, SellerType)]] =
    query.filter(_.userId === userId).map(s => (s.id, s.sellerType)).result.headOption

  def loadByUserId(userId: Long): DBIO[Option[Seller[_]]] =
    query.filter(_.userId === userId).map(s => (s, s.cityId)).result.headOption.flatMap {
      case Some((seller, cityId)) => refineSeller(seller, userId, cityId).map(Some(_))
      case _ => DBIO.successful(None)
    }

  def updatePhoto(userId: Long, name: String): DBIO[Option[Long]] =
    query.filter(_.userId === userId).map(_.id).result.headOption.flatMap {
      case someId@Some(id) => query.filter(_.id === id).map(_.photo).update(name).map { c =>
        if (c == 1) someId else None
      }
      case _ => DBIO.successful(None)
    }

  def deletePhoto(userId: Long): DBIO[Option[(Long, Option[String])]] =
    query.filter(_.userId === userId).map(s => (s.id, s.photo.?)).result.headOption.flatMap {
      case row@Some((id, photo)) =>
        if (photo.isDefined) {
          query.filter(_.id === id).map(_.photo.?).update(None).map { c =>
            if (c == 1) row else None
          }
        } else {
          DBIO.successful(row)
        }
      case _ => DBIO.successful(None)
    }

  override def list(range: Option[Range]): DBIO[Seq[Seller[_]]] = listByQuery(query, range)

  def listByType(`type`: SellerType, range: Option[Range]): DBIO[Seq[Seller[_]]] =
    listByQuery(query.filter(_.sellerType === `type`), range)

  def listByProvinceId(provinceId: Long, range: Option[Range]): DBIO[Seq[Seller[_]]] =
    listByQueryAndProvinceId(query, provinceId, range)

  def listByTypeAndProvinceId(`type`: SellerType, provinceId: Long, range: Option[Range]): DBIO[Seq[Seller[_]]] =
    listByQueryAndProvinceId(query.filter(_.sellerType === `type`), provinceId, range)

  private def listByQueryAndProvinceId(query: Query[SellerTable, Seller[_], Seq], provinceId: Long, range: Option[Range]) = {
    val q = (for {
      s: SellerTable <- query
      c: cityRepo.CityTable <- cityRepo.query if c.id === s.cityId
    } yield (s, c.provinceId)).filter(_._2 === provinceId).map(_._1)
    listByQuery(q, range)
  }

  def listByCityId(cityId: Long, range: Option[Range]): DBIO[Seq[Seller[_]]] =
    listByQuery(query.filter(_.cityId === cityId), range)

  def listByTypeAndCityId(`type`: SellerType, cityId: Long, range: Option[Range]): DBIO[Seq[Seller[_]]] =
    listByQuery(query.filter(s => s.sellerType === `type` && s.cityId === cityId), range)

  private def listByQuery(query: Query[SellerTable, Seller[_], Seq], range: Option[Range],
                          sortBy: (SellerTable) => ColumnOrdered[_] = _.registrationDate.asc) =
    pagedQuery[SellerTable](query.filter(isPublic).sortBy(sortBy), range)
      .map(s => (s, s.cityId)).result
      .flatMap(rows => DBIO.sequence(rows.map(refineSeller)))
      .withPinnedSession

  private val isPublic: (SellerTable) => Rep[Boolean] = seller =>
    seller.sellerType === SellerType.DEALER || seller.publicProfile

  private val refineSeller: ((Seller[_], Long)) => DBIO[Seller[_]] = {
    case (seller, cityId) =>
      cityRepo.load(cityId).zip(phoneNumbersAction(seller.id.get)).map {
        case (Some(city), numbers) =>
          seller.withCity(city).asInstanceOf[Seller[_]]
            .withPhoneNumbers(numbers).asInstanceOf[Seller[_]]
        case _ => throw new IllegalStateException
      }
  }

  private def refineSeller(seller: Seller[_], userId: Long, cityId: Long): DBIO[Seller[_]] =
    userRepo.load(userId).zip(cityRepo.load(cityId)).zip(phoneNumbersAction(seller.id.get)).map {
      case ((Some(user), Some(city)), numbers) =>
        seller.withUser(user).asInstanceOf[Seller[_]]
          .withCity(city).asInstanceOf[Seller[_]]
          .withPhoneNumbers(numbers).asInstanceOf[Seller[_]]
      case _ => throw new IllegalStateException
    }

  private def phoneNumbersAction(sellerId: Long) =
    phoneNumbers.filter(_.sellerId === sellerId).sortBy(_.order asc).map(_.phoneNumber).result

}
