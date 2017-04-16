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

import java.time.{LocalDate, LocalDateTime}
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorSystem, InvalidActorNameException, Props}
import akka.pattern.ask
import akka.util.Timeout
import ir.bama.models.SellerType.SellerType
import ir.bama.models._
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

  private val dispatcher = system.actorOf(Props(new ActionDispatcher), "ad-action-dispatcher")
  private implicit val timeout = Timeout(30 seconds)

  private val visibleStatuses = Seq(SellAdStatus.SUBMITTED, SellAdStatus.RESUBMITTED)

  type PersistenceResult = Option[Either[String, Long]]
  type Limits = Map[SellerType, (Int, Int, Option[Int])]

  private case class Submit(userId: Long, ad: SellAd, limits: Limits)

  private case class Resubmit(userId: Long, adId: Long, limits: Limits)

  private case class Cancel(userId: Long, adId: Long)

  def submit(userId: Long, ad: SellAd, limits: Limits): Future[PersistenceResult] =
    (dispatcher ? Submit(userId, ad, limits)).mapTo[PersistenceResult]

  def resubmit(userId: Long, adId: Long, limits: Limits): Future[PersistenceResult] =
    (dispatcher ? Resubmit(userId, adId, limits)).mapTo[PersistenceResult]

  def cancel(userId: Long, adId: Long): Future[PersistenceResult] =
    (dispatcher ? Cancel(userId, adId)).mapTo[PersistenceResult]

  private class ActionDispatcher extends Actor {

    override def receive: Receive = {
      case msg@Submit(userId, _, _) => dispatch(msg, userId)
      case msg@Resubmit(userId, _, _) => dispatch(msg, userId)
      case msg@Cancel(userId, _) => dispatch(msg, userId)
    }

    private def dispatch(msg: Any, userId: Long) = {
      val name = s"runner-$userId"
      context.child(name) match {
        case Some(runner) => runner forward msg
        case _ =>
          val currentSender = sender()
          sellerService.findIdAndTypeByUserId(userId).map {
            case Some((sellerId, sellerType)) =>
              try {
                context.actorOf(ActionRunner.props(sellerId, sellerType), name) tell(msg, currentSender)
              } catch {
                case _: InvalidActorNameException => self ! msg
              }
            case _ => currentSender ! None
          }
      }
    }

  }

  private class ActionRunner(sellerId: Long, sellerType: SellerType) extends Actor {

    private val someSeller: Option[Seller[_]] = Some(Seller.id(sellerId))

    override def receive: Receive = {
      case Submit(_, ad, limits) => submit(ad, limits)
      case Resubmit(_, adId, limits) => resubmit(adId, limits)
      case Cancel(_, adId) => cancel(adId)
    }

    private def submit(ad: SellAd, limits: Limits) = {
      val limit = limits(sellerType)
      reply {
        db.run {
          val start = LocalDate.now().minusDays(limit._1 - 1).atStartOfDay()
          repo.countAds(sellerId, start, LocalDateTime.now()).flatMap { c =>
            if (c < limit._2) {
              repo.persist(ad.copy(seller = someSeller)).map(Right(_))
            } else {
              DBIO.successful(Left(
                s"You cannot submit more than ${limit._2} ad(s) in the period of ${limit._1} day(s). Please try again later."))
            }
          }.map(Some(_))
        }
      }
    }

    private def resubmit(adId: Long, limits: Limits) = {
      val limit = limits(sellerType)
      reply {
        db.run {
          repo.findSellerIdAndStatusById(adId).flatMap {
            case Some((seId, status)) =>
              if (seId == sellerId) {
                (if (visibleStatuses.contains(status)) {
                  val start = LocalDate.now().minusDays(limit._1 - 1).atStartOfDay()
                  repo.countSubmissions(adId, start, LocalDateTime.now()).flatMap {
                    case (total, inRange) =>
                      if (limit._3.exists(total < _)) {
                        if (inRange < limit._2) {
                          repo.resubmit(adId).map(_ => Right(adId))
                        } else {
                          DBIO.successful(Left(
                            s"You cannot resubmit an ad more than ${limit._2} times in the period of ${limit._1} day(s). Please try again later."))
                        }
                      } else {
                        DBIO.successful(Left(s"You cannot resubmit an ad more than ${limit._3} times."))
                      }
                  }
                } else {
                  DBIO.successful(Left("This ad is not is visible status."))
                }).map(Some(_))
              } else {
                DBIO.successful(Left("You are not the owner of this ad."))
              }
            case _ => DBIO.successful(None)
          }
        }
      }
    }

    private def cancel(adId: Long) = reply {
      db.run {
        repo.findSellerIdById(adId).flatMap {
          case Some(seId) =>
            (if (seId == sellerId) {
              repo.cancel(adId).map(_ => Right(adId))
            } else {
              DBIO.successful(Left("You are not the owner of this ad."))
            }).map(Some(_))
          case None => DBIO.successful(None)
        }
      }
    }

    private def reply[T](opFuture: Future[T]) = {
      // we have to wait for result of the operation (i.e. block), to prevent concurrent inserts/updates
      sender ! Await.result(opFuture, Duration.Inf)
    }

  }

  private object ActionRunner {
    def props(sellerId: Long, sellerType: SellerType) = Props(new ActionRunner(sellerId, sellerType))
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
      repo.list(maybeSellerId, visibleStatuses, range)
    }
  }

  def listBySellerId(sellerId: Long, maybeUserId: Option[Long], range: Option[Range]): Future[Seq[(SellAd, Boolean)]] = db.run {
    findSellerId(maybeUserId) { maybeSellerId =>
      repo.listBySellerId(sellerId, maybeSellerId, visibleStatuses, range)
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
