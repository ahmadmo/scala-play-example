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

import ir.bama.models.CarCategory.CarCategory
import ir.bama.models.CarChassis.CarChassis
import ir.bama.models.CarDifferential.CarDifferential
import ir.bama.models.CarFuelType.CarFuelType
import ir.bama.models.CarGearBox.CarGearBox
import ir.bama.models.CarStatus.CarStatus
import ir.bama.models.PaymentPeriod.PaymentPeriod
import ir.bama.models.PaymentType.PaymentType
import ir.bama.models.SellAdStatus.SellAdStatus
import ir.bama.models._
import ir.bama.utils.{Range, RangeLike}
import play.api.db.slick.DatabaseConfigProvider
import shapeless.syntax.std.tuple._
import slick.lifted.{ColumnOrdered, ForeignKeyQuery, PrimaryKey, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class SellAdRepo @Inject()(dbConfigProvider: DatabaseConfigProvider, sellerRepo: SellerRepo,
                           cityRepo: CityRepo, modelRepo: CarModelRepo)
                          (implicit ec: ExecutionContext) extends BaseRepo[SellAd](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  implicit val sellAdStatusMapper: BaseColumnType[SellAdStatus] = enumMapper(SellAdStatus)

  private type SellAdRow = (Option[Long], Long, Long, String, Option[String], Date, Int, Int, SellAdStatus, PaymentRow, CarRow)

  class SellAdTable(tag: Tag) extends Table[SellAd](tag, "T_SELL_AD") with PaymentTable with CarTable {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def sellerId: Rep[Long] = column[Long]("C_SELLER_ID", NotNull)

    def seller: ForeignKeyQuery[_, Seller[_]] = foreignKey("FK_SELLER_SELL_AD", sellerId, sellerRepo.query)(_.id,
      onDelete = ForeignKeyAction.Cascade)

    def cityId: Rep[Long] = column[Long]("C_CITY_ID", NotNull)

    def city: ForeignKeyQuery[_, City] = foreignKey("FK_CITY_SELL_AD", cityId, cityRepo.query)(_.id)

    def venue: Rep[String] = column[String]("C_VENUE", O.SqlType("TEXT"), NotNull)

    def phoneNumber: Rep[String] = column[String]("C_PHONE_NUMBER", O.SqlType("VARCHAR"), O.Length(20), Nullable)

    def lastSubmissionDate: Rep[Date] = column[Date]("C_LAST_SUBMISSION_DATE", NotNull)

    def count: Rep[Int] = column[Int]("C_COUNT", NotNull)

    def soldCount: Rep[Int] = column[Int]("C_SOLD_COUNT", NotNull)

    def adStatus: Rep[SellAdStatus] = column[SellAdStatus]("C_AD_STATUS", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    override def * : ProvenShape[SellAd] =
      (id.?, sellerId, cityId, venue, phoneNumber.?,
        lastSubmissionDate, count, soldCount, adStatus,
        paymentProjection, carProjection) <> (toSellAd, fromSellAd)

  }

  implicit val paymentTypeMapper: BaseColumnType[PaymentType] = enumMapper(PaymentType)
  implicit val paymentPeriodMapper: BaseColumnType[PaymentPeriod] = enumMapper(PaymentPeriod)

  private type PaymentRow = (PaymentType, Long, Long, Option[PaymentPeriod], Option[Int], Option[Int], Option[Long])

  sealed trait PaymentTable {

    this: Table[_] =>

    def paymentType: Rep[PaymentType] = column[PaymentType]("C_PAYMENT_TYPE", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def initialPrice: Rep[Long] = column[Long]("C_INITIAL_PRICE", NotNull)

    def finalPrice: Rep[Long] = column[Long]("C_FINAL_PRICE", NotNull)

    def paymentPeriod: Rep[PaymentPeriod] = column[PaymentPeriod]("C_PAYMENT_PERIOD", O.SqlType("VARCHAR"), O.Length(255), Nullable)

    def ticks: Rep[Int] = column[Int]("C_TICKS", Nullable)

    def numberOfPayments: Rep[Int] = column[Int]("C_NUMBER_OF_PAYMENTS", Nullable)

    def amountPerPayment: Rep[Long] = column[Long]("C_AMOUNT_PER_PAYMENT", Nullable)

    def paymentProjection: ProvenShape[PaymentRow] =
      (paymentType, initialPrice, finalPrice,
        paymentPeriod.?, ticks.?, numberOfPayments.?, amountPerPayment.?)

  }

  implicit val carChassisMapper: BaseColumnType[CarChassis] = enumMapper(CarChassis)
  implicit val carDifferentialMapper: BaseColumnType[CarDifferential] = enumMapper(CarDifferential)
  implicit val carCategoryMapper: BaseColumnType[CarCategory] = enumMapper(CarCategory)
  implicit val carStatusMapper: BaseColumnType[CarStatus] = enumMapper(CarStatus)
  implicit val carGearBoxMapper: BaseColumnType[CarGearBox] = enumMapper(CarGearBox)
  implicit val carFuelTypeMapper: BaseColumnType[CarFuelType] = enumMapper(CarFuelType)

  private type CarRow = (Long, Int, CarChassis, CarDifferential, CarCategory, CarStatus,
    Int, CarGearBox, CarFuelType,
    String, String, String)

  sealed trait CarTable {

    this: Table[_] =>

    def modelId: Rep[Long] = column[Long]("C_CAR_MODEL_ID", NotNull)

    def model: ForeignKeyQuery[_, CarModel] = foreignKey("FK_CAR_MODEL_SELL_AD", modelId, modelRepo.query)(_.id)

    def year: Rep[Int] = column[Int]("C_CAR_YEAR", NotNull)

    def chassis: Rep[CarChassis] = column[CarChassis]("C_CAR_CHASSIS", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def differential: Rep[CarDifferential] = column[CarDifferential]("C_CAR_DIFFERENTIAL", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def category: Rep[CarCategory] = column[CarCategory]("C_CAR_CATEGORY", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def carStatus: Rep[CarStatus] = column[CarStatus]("C_CAR_STATUS", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def mileage: Rep[Int] = column[Int]("C_CAR_MILEAGE", NotNull)

    def gearBox: Rep[CarGearBox] = column[CarGearBox]("C_CAR_GEAR_BOX", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def fuelType: Rep[CarFuelType] = column[CarFuelType]("C_CAR_FUEL_TYPE", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def bodyDescription: Rep[String] = column[String]("C_CAR_BODY_DESC", O.SqlType("TEXT"), NotNull)

    def bodyColor: Rep[String] = column[String]("C_CAR_BODY_COLOR", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def cabinColor: Rep[String] = column[String]("C_CAR_CABIN_COLOR", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def carProjection: ProvenShape[CarRow] =
      (modelId, year, chassis, differential, category, carStatus,
        mileage, gearBox, fuelType, bodyDescription, bodyColor, cabinColor)

  }

  type SubmissionDate = (Long, Date)

  class SubmissionDateTable(tag: Tag) extends Table[SubmissionDate](tag, "T_SUBMISSION_DATE") {

    def adId: Rep[Long] = column[Long]("C_AD_ID", NotNull)

    def ad: ForeignKeyQuery[_, SellAd] = foreignKey("FK_AD_SUBMISSION_DATE", adId, query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def when: Rep[Date] = column[Date]("C_WHEN", NotNull)

    def pk: PrimaryKey = primaryKey("PK_SUBMISSION_DATE", (adId, when))

    override def * : ProvenShape[SubmissionDate] = (adId, when)

  }

  class StatsTable(tag: Tag) extends Table[SellAdStats](tag, "T_STATS") {

    def adId: Rep[Long] = column[Long]("C_AD_ID", O.PrimaryKey, NotNull)

    def ad: ForeignKeyQuery[_, SellAd] = foreignKey("FK_AD_STATS", adId, query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def adViews: Rep[Int] = column[Int]("C_AD_VIEWS", NotNull)

    def phoneNumberViews: Rep[Int] = column[Int]("C_PHONE_NUMBER_VIEWS", NotNull)

    override def * : ProvenShape[SellAdStats] =
      (adId, adViews, phoneNumberViews) <> ((SellAdStats.apply _).tupled, SellAdStats.unapply)

  }

  type CarPhoto = (Long, Int, String)

  class CarPhotoTable(tag: Tag) extends Table[CarPhoto](tag, "T_CAR_PHOTO") {

    def adId: Rep[Long] = column[Long]("C_AD_ID", NotNull)

    def ad: ForeignKeyQuery[_, SellAd] = foreignKey("FK_AD_CAR_PHOTO", adId, query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def order: Rep[Int] = column[Int]("C_ORDER", NotNull)

    def photo: Rep[String] = column[String]("C_PHOTO", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def pk: PrimaryKey = primaryKey("PK_CAR_PHOTO", (adId, photo))

    override def * : ProvenShape[CarPhoto] = (adId, order, photo)

  }

  type PrePaid = (Long, Int, Long)

  class PrePaidTable(tag: Tag) extends Table[PrePaid](tag, "T_PRE_PAID") {

    def adId: Rep[Long] = column[Long]("C_AD_ID", NotNull)

    def ad: ForeignKeyQuery[_, SellAd] = foreignKey("FK_AD_PRE_PAID", adId, query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def order: Rep[Int] = column[Int]("C_ORDER", NotNull)

    def amount: Rep[Long] = column[Long]("C_AMOUNT", NotNull)

    def pk: PrimaryKey = primaryKey("PK_PRE_PAID", (adId, order))

    override def * : ProvenShape[PrePaid] = (adId, order, amount)

  }

  private val toPayment: PaymentRow => Payment = {
    case row@(paymentType, initialPrice, finalPrice, maybePeriod, maybeTicks, maybePayments, maybeAmount) =>
      paymentType match {
        case PaymentType.CREDIT => CreditPayment(finalPrice)
        case PaymentType.INSTALLMENT => (maybePeriod, maybeTicks, maybePayments, maybeAmount) match {
          case (Some(period), Some(ticks), Some(payments), Some(amount)) =>
            InstallmentPayment(initialPrice, finalPrice, period, ticks, payments, amount)
          case _ => throw new IllegalStateException(s"Invalid row: $row")
        }
        case _ => throw new IllegalStateException(s"Invalid row: $row")
      }
  }

  private val fromPayment: Payment => PaymentRow = { payment =>
    val row = (payment.`type`, payment.initialPrice, payment.finalPrice)
    payment match {
      case _: CreditPayment => row ++ (None, None, None, None)
      case x: InstallmentPayment => row ++ (Some(x.period), Some(x.ticks), Some(x.numberOfPayments), Some(x.amountPerPayment))
      case _ => throw new IllegalStateException
    }
  }

  private val toCar: CarRow => Car = row =>
    (Car.apply _).tupled(row.replaceType[Long](None)._2 :+ None)

  private val fromCar: Car => CarRow = car =>
    (car.model.flatMap(_.id).get, car.year, car.chassis, car.differential, car.category,
      car.status, car.mileage, car.gearBox, car.fuelType, car.bodyDescription, car.bodyColor, car.cabinColor)

  private val toSellAd: SellAdRow => SellAd = {
    case (id, _, _, venue, phoneNumber, lastSubmissionDate, count, soldCount, adStatus, paymentRow, carRow) =>
      SellAd(id, None, None, venue, phoneNumber, None, lastSubmissionDate, count, soldCount, adStatus, toPayment(paymentRow), toCar(carRow), None)
  }

  private val fromSellAd: SellAd => Option[SellAdRow] = { ad =>
    Some((ad.id, ad.seller.flatMap(_.id).get, ad.city.flatMap(_.id).get, ad.venue, ad.phoneNumber,
      ad.lastSubmissionDate, ad.count, ad.soldCount, ad.status, fromPayment(ad.payment), fromCar(ad.car)))
  }

  override type TableType = SellAdTable
  override val query: TableQuery[SellAdTable] = TableQuery[SellAdTable]
  override protected val idColumn: (SellAdTable) => Rep[Long] = _.id

  val submissionDates: TableQuery[SubmissionDateTable] = TableQuery[SubmissionDateTable]
  val stats: TableQuery[StatsTable] = TableQuery[StatsTable]
  val carPhotos: TableQuery[CarPhotoTable] = TableQuery[CarPhotoTable]
  val prePaids: TableQuery[PrePaidTable] = TableQuery[PrePaidTable]

  override def persist(ad: SellAd): DBIO[Long] = {
    val insertAd = super.persist(ad).flatMap { adId =>
      val insertStats = stats += SellAdStats(adId, 0, 0)
      val insertDates = submissionDates += (adId, ad.lastSubmissionDate)
      val insertCarPhotos = carPhotos ++= (ad.car.photos match {
        case Some(names) => names.zipWithIndex.map {
          case (name, idx) => (adId, idx, name)
        }
        case _ => Seq.empty
      })
      (insertStats zip insertDates zip insertCarPhotos).map(_ => adId)
    }
    ad.payment match {
      case _: CreditPayment => insertAd.transactionally
      case x: InstallmentPayment =>
        insertAd.flatMap { adId =>
          (prePaids ++= (x.prePaids match {
            case Some(amounts) => amounts.zipWithIndex.map {
              case (amount, idx) => (adId, idx, amount)
            }
            case _ => Seq.empty
          })).map(_ => adId)
        }.transactionally
      case _ => throw new IllegalStateException
    }
  }

  def load(adId: Long, maybeSellerId: Option[Long]): DBIO[Option[(SellAd, Boolean)]] = {
    val singleQuery = maybeSellerId match {
      case Some(sellerId) => query.filter { ad =>
        ad.id === adId && (ad.sellerId === sellerId || ad.adStatus.inSet(Seq(SellAdStatus.SUBMITTED, SellAdStatus.RESUBMITTED)))
      }
      case _ => query.filter { ad =>
        ad.id === adId && ad.adStatus.inSet(Seq(SellAdStatus.SUBMITTED, SellAdStatus.RESUBMITTED))
      }
    }
    singleQuery.map { ad =>
      (ad, ad.sellerId, ad.cityId, ad.modelId)
    }.result.headOption.flatMap {
      case Some((ad, sellerId, cityId, modelId)) =>
        val owner = maybeSellerId.contains(sellerId)
        val qSeller = sellerRepo.load(sellerId, filterPublic = false)
        val qCity = cityRepo.load(cityId)
        val qCarModel = modelRepo.load(modelId)
        val qCarPhotos = carPhotosAction(adId, None)
        val joinQuery = (qSeller zip qCity) zip (qCarModel zip qCarPhotos)
        (if (owner) {
          val qStats = stats.filter(_.adId === adId).result.headOption
          val qDates = submissionDatesAction(adId)
          joinQuery.zip(qStats zip qDates).map {
            case (((someSeller@Some(_), someCity@Some(_)), (someModel@Some(_), names)), (someStats@Some(_), dates)) =>
              ad.copy(
                seller = someSeller, city = someCity, stats = someStats,
                car = ad.car.copy(model = someModel, photos = Some(names)),
                submissionDates = Some(dates))
            case _ => throw new IllegalStateException
          }
        } else joinQuery.map {
          case ((someSeller@Some(_), someCity@Some(_)), (someModel@Some(_), names)) =>
            ad.copy(
              seller = someSeller, city = someCity,
              car = ad.car.copy(model = someModel, photos = Some(names)))
          case _ => throw new IllegalStateException
        }).flatMap { refinedAd =>
          ad.payment match {
            case _: CreditPayment => DBIO.successful(refinedAd)
            case x: InstallmentPayment => prePaidsAction(adId).map { prePaidAmounts =>
              refinedAd.copy(payment = x.copy(prePaids = Some(prePaidAmounts)))
            }
          }
        }.map(refinedAd => Some(refinedAd, owner))
      case _ => DBIO.successful(None)
    }
  }

  def countAds(sellerId: Long, fromDate: Date, toDate: Date): DBIO[Int] =
    query.filter { ad =>
      ad.sellerId === sellerId && ad.lastSubmissionDate.between(fromDate, toDate)
    }.length.result

  def list(maybeSellerId: Option[Long], range: Option[Range]): DBIO[Seq[(SellAd, Boolean)]] =
    listByQuery(query, maybeSellerId, range)

  def listBySellerId(sellerId: Long, maybeSellerId: Option[Long], range: Option[Range]): DBIO[Seq[(SellAd, Boolean)]] =
    listByQuery(query.filter(_.sellerId === sellerId), maybeSellerId, range)

  private def listByQuery(query: Query[SellAdTable, SellAd, Seq], maybeSellerId: Option[Long], range: Option[Range],
                          sortByFields: Seq[(SellAdTable) => ColumnOrdered[_]] = Seq(_.lastSubmissionDate desc, _.id asc)) = {
    val listQuery = sortByFields.foldLeft {
      maybeSellerId match {
        case Some(sellerId) => query.filter { ad =>
          ad.sellerId === sellerId || ad.adStatus.inSet(Seq(SellAdStatus.SUBMITTED, SellAdStatus.RESUBMITTED))
        }
        case _ => query.filter { ad =>
          ad.adStatus.inSet(Seq(SellAdStatus.SUBMITTED, SellAdStatus.RESUBMITTED))
        }
      }
    } {
      case (q, f) => q.sortBy(f)
    }
    pagedQuery[SellAdTable](listQuery, range).map { ad =>
      (ad, ad.sellerId, ad.cityId, ad.modelId)
    }.result.flatMap { rows =>
      DBIO.sequence {
        rows.map {
          case (ad, sellerId, cityId, modelId) =>
            val adId = ad.id.get
            val owner = maybeSellerId.contains(sellerId)
            val qCity = cityRepo.load(cityId)
            val qCarModel = modelRepo.load(modelId)
            val qCarPhotos = carPhotosAction(adId, 0 ~ 1)
            val joinQuery = qCity zip qCarModel zip qCarPhotos
            (if (owner) {
              val qStats = stats.filter(_.adId === adId).result.headOption
              (joinQuery zip qStats).map {
                case (((someCity@Some(_), someModel@Some(_)), names), someStats@Some(_)) =>
                  ad.copy(
                    city = someCity, stats = someStats,
                    car = ad.car.copy(model = someModel, photos = Some(names)))
                case _ => throw new IllegalStateException
              }
            } else joinQuery.map {
              case ((someCity@Some(_), someModel@Some(_)), names) =>
                ad.copy(city = someCity, car = ad.car.copy(model = someModel, photos = Some(names)))
              case _ => throw new IllegalStateException
            }).map(refinedAd => (refinedAd, owner))
        }
      }
    }
  }

  private def submissionDatesAction(adId: Long) =
    submissionDates.filter(_.adId === adId).sortBy(d => (d.when asc, d.adId asc)).map(_.when).result

  private def carPhotosAction(adId: Long, range: Option[Range]) =
    pagedQuery[CarPhotoTable](carPhotos.filter(_.adId === adId).sortBy(_.order asc), range).map(_.photo).result

  private def prePaidsAction(adId: Long) =
    prePaids.filter(_.adId === adId).sortBy(_.order asc).map(_.amount).result

}
