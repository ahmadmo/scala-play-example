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

package ir.bama.services

import java.time.{LocalDate, ZoneId}
import java.util.Date
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorSystem, InvalidActorNameException, Props}
import akka.pattern.ask
import akka.util.Timeout
import ir.bama.models.SellerType.SellerType
import ir.bama.models.{Dealer, PrivateSeller, SellAd, Seller}
import ir.bama.repositories.SellAdRepo
import ir.bama.utils.Range

import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * @author ahmad
  */
@Singleton
class SellAdService @Inject()(adRepo: SellAdRepo, sellerService: SellerService, system: ActorSystem)
                             (implicit ec: ExecutionContext) extends BaseService[SellAd, SellAdRepo](adRepo) {

  import repo.dbConfig._
  import profile.api._

  private val dispatcher = system.actorOf(Props(new PersistenceDispatcher), "ad-persistence-dispatcher")
  private implicit val timeout = Timeout(30 seconds)

  type PersistResult = Option[Either[String, Long]]
  type AdsPerDay = Map[SellerType, Int]

  private case class Persist(userId: Long, ad: SellAd, adsPerDay: AdsPerDay)

  def persist(userId: Long, ad: SellAd, adsPerDay: AdsPerDay): Future[PersistResult] =
    (dispatcher ? Persist(userId, ad, adsPerDay)).mapTo[PersistResult]

  private class PersistenceDispatcher extends Actor {
    override def receive: Receive = {
      case msg@Persist(userId, _, _) =>
        val name = s"persister-$userId"
        context.child(name) match {
          case Some(persister) => persister forward msg
          case _ =>
            val currentSender = sender()
            sellerService.findIdAndTypeByUserId(userId).map {
              case Some((sellerId, sellerType)) =>
                try {
                  context.actorOf(Persister.props(sellerId, sellerType), name) tell(msg, currentSender)
                } catch {
                  case _: InvalidActorNameException => self ! msg
                }
              case _ => sender ! None
            }
        }
    }
  }

  private class Persister(sellerId: Long, sellerType: SellerType) extends Actor {

    private val someSeller: Option[Seller[_]] = Some(Seller.id(sellerId))

    override def receive: Receive = {
      case Persist(_, ad, adsPerDay) =>
        val insertFuture = db.run {
          val startOfDay = Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)
          repo.countAds(sellerId, startOfDay, new Date()).flatMap { c =>
            val max = adsPerDay(sellerType)
            if (c < max) {
              repo.persist(ad.copy(seller = someSeller)).map(Right(_))
            } else {
              DBIO.successful(Left(s"Only $max ads per day is allowed."))
            }
          }
        }
        // we have to wait for result of the operation (i.e. block), to prevent concurrent inserts
        sender ! Some(Await.result(insertFuture, Duration.Inf))
    }

  }

  private object Persister {
    def props(sellerId: Long, sellerType: SellerType) = Props(new Persister(sellerId, sellerType))
  }

  def load(adId: Long, maybeUserId: Option[Long]): Future[Option[(SellAd, Boolean)]] = db.run {
    findSellerId(maybeUserId) { maybeSellerId =>
      repo.load(adId, maybeSellerId)
    }.map {
      _.flatMap {
        case result@(ad, owner) =>
          ad.seller.map {
            case x: PrivateSeller =>
              if (x.publicProfile || owner) result else {
                val maybeSeller: Option[Seller[_]] = if (ad.phoneNumber.isEmpty) Some(Seller.phoneNumbers(x.phoneNumbers)) else None
                (ad.copy(seller = maybeSeller), owner)
              }
            case _: Dealer => result
          }
      }
    }
  }

  def list(maybeUserId: Option[Long], range: Option[Range]): Future[Seq[(SellAd, Boolean)]] = db.run {
    findSellerId(maybeUserId) { maybeSellerId =>
      repo.list(maybeSellerId, range)
    }
  }

  def listBySellerId(sellerId: Long, maybeUserId: Option[Long], range: Option[Range]): Future[Seq[(SellAd, Boolean)]] = db.run {
    findSellerId(maybeUserId) { maybeSellerId =>
      repo.listBySellerId(sellerId, maybeSellerId, range)
    }
  }

  private def findSellerId[A](maybeUserId: Option[Long])(block: (Option[Long]) => DBIO[A]) =
    maybeUserId match {
      case Some(userId) => sellerService.repo.findIdByUserId(userId).flatMap(block)
      case _ => block(None)
    }

  def incrementViews(adId: Long): Future[Boolean] =
    db.run(repo.incrementViews(adId))

  def incrementPhoneNumberViews(adId: Long): Future[Boolean] =
    db.run(repo.incrementPhoneNumberViews(adId))

}
