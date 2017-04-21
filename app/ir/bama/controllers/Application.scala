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

import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Singleton}

import ir.bama.AppInitializer
import ir.bama.repositories._
import play.api.mvc._
import play.api.{Configuration, Logger}
import slick.jdbc.meta.MTable

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}

@Singleton
class Application @Inject()(provinceRepo: ProvinceRepo, cityRepo: CityRepo,
                            carBrandRepo: CarBrandRepo, carModelRepo: CarModelRepo,
                            userRepo: UserRepo, userLoginRepo: UserLoginRepo,
                            sellerRepo: SellerRepo, sellAdRepo: SellAdRepo,
                            configs: Configuration)
                           (implicit ec: ExecutionContext) extends Controller with AppInitializer {

  private val logger = Logger(getClass)

  private lazy val filesDir = Paths.get(configs.getString("app.dir.files").get)

  import provinceRepo.dbConfig._
  import profile.api._

  override def init(): Unit = {
    Files.createDirectories(filesDir)
    val dbSetup = db.run {
      MTable.getTables("%").flatMap { existing =>
        val names = existing.map(_.name.name).toSet
        val tablesToBeCreated = Seq(
          provinceRepo.query, cityRepo.query, carBrandRepo.query, carModelRepo.query,
          userRepo.query, userLoginRepo.query, sellerRepo.query, sellerRepo.phoneNumbers,
          sellAdRepo.query, sellAdRepo.submissionDates, sellAdRepo.stats, sellAdRepo.carPhotos, sellAdRepo.prepayments
        ).filterNot(t => names.contains(t.baseTableRow.tableName))
        DBIO.sequence(tablesToBeCreated.map(_.schema.create))
      }.transactionally
    }
    dbSetup.onComplete {
      case Success(_) => logger.info("Setup completed")
      case Failure(ex) => logger.error("Setup failed", ex)
    }
    Await.ready(dbSetup, Duration.Inf)
  }

  init()

  def index = Action {
    Ok("Your new application is ready.")
  }

}
