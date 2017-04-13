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
import ir.bama.models.{SellAd, Seller}
import ir.bama.repositories.SellAdRepo
import ir.bama.utils.Range

import scala.concurrent.duration.{Duration, DurationLong}
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * @author ahmad
  */
@Singleton
class SellAdService @Inject()(adRepo: SellAdRepo, sellerService: SellerService, system: ActorSystem)
                             (implicit ec: ExecutionContext) extends BaseService[SellAd](adRepo) {

  import adRepo.dbConfig._
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
      case p: Persist =>
        val name = s"persister-${p.userId}"
        context.child(name) match {
          case Some(persister) => persister forward p
          case _ =>
            val currentSender = sender()
            sellerService.findIdAndTypeByUserId(p.userId).map {
              case Some((sellerId, sellerType)) =>
                try {
                  context.actorOf(Persister.props(sellerId, sellerType), name) tell(p, currentSender)
                } catch {
                  case _: InvalidActorNameException => self ! p
                }
              case _ => sender ! None
            }
        }
    }
  }

  private class Persister(sellerId: Long, sellerType: SellerType) extends Actor {

    private val someSeller: Option[Seller[_]] = Some(Seller.id(sellerId))

    override def receive: Receive = {
      case p: Persist =>
        val insertFuture = db.run {
          val startOfDay = Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)
          adRepo.countAds(sellerId, startOfDay, new Date()).flatMap { c =>
            val max = p.adsPerDay(sellerType)
            if (c < max) {
              adRepo.persist(p.ad.copy(seller = someSeller)).map(Right(_))
            } else {
              DBIO.successful(Left(s"Only $max ads per day is allowed."))
            }
          }
        }
        // we have to wait for result of the operation (i.e. block), to prevent concurrent inserts
        sender ! Some(Await.result(insertFuture, Duration.Inf))
    }

  }

  object Persister {
    def props(sellerId: Long, sellerType: SellerType) = Props(new Persister(sellerId, sellerType))
  }

  def listBySellerId(sellerId: Long, range: Option[Range]): Future[Seq[SellAd]] =
    db.run(adRepo.listBySellerId(sellerId, range))

}
