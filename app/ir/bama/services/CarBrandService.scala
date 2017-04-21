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

import ir.bama.models.CarBrand
import ir.bama.repositories.CarBrandRepo

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author ahmad
  */
@Singleton
class CarBrandService @Inject()(brandRepo: CarBrandRepo)(implicit ec: ExecutionContext) extends BaseService[CarBrand, CarBrandRepo](brandRepo) {

  import repo.dbConfig._

  def searchByName(name: String, range: Option[Range]): Future[Seq[CarBrand]] =
    db.run(repo.searchByName(name, range))

}
