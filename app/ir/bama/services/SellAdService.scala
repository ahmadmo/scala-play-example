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

import javax.inject.{Inject, Singleton}

import ir.bama.models.{SellAd, Seller}
import ir.bama.repositories.{SellAdRepo, SellerRepo}
import ir.bama.utils.Range

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author ahmad
  */
@Singleton
class SellAdService @Inject()(adRepo: SellAdRepo, sellerRepo: SellerRepo)(implicit ec: ExecutionContext) extends BaseService[SellAd](adRepo) {

  import adRepo.dbConfig._
  import profile.api._

  def persist(userId: Long, ad: SellAd): Future[Option[Long]] = db.run {
    val action = sellerRepo.findIdByUserId(userId).flatMap {
      case Some(sellerId) => adRepo.persist(ad.copy(seller = Some(Seller.id(sellerId)))).map(Some(_))
      case _ => DBIO.successful(None)
    }
    action
  }

  def listBySellerId(sellerId: Long, range: Option[Range]): Future[Seq[SellAd]] =
    db.run(adRepo.listBySellerId(sellerId, range))

}
