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

import javax.inject.{Inject, Singleton}

import ir.bama.models.CarBrand
import ir.bama.utils.Range
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class CarBrandRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)
                            (implicit ec: ExecutionContext) extends BaseRepo[CarBrand](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  class CarBrandTable(tag: Tag) extends Table[CarBrand](tag, "T_CAR_BRAND") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def name: Rep[String] = column[String]("C_NAME", O.Unique, O.SqlType("VARCHAR"), O.Length(255), NotNull)

    override def * : ProvenShape[CarBrand] = (id.?, name) <> ((CarBrand.apply _).tupled, CarBrand.unapply)

  }

  override type TableType = CarBrandTable
  override val query: TableQuery[CarBrandTable] = TableQuery[CarBrandTable]
  override protected val idColumn: (CarBrandTable) => Rep[Long] = _.id

  override def persist(brand: CarBrand): DBIO[Long] =
    super.persist(brand.copy(name = brand.name.toLowerCase))

  def searchByName(name: String, range: Option[Range]): DBIO[Seq[CarBrand]] =
    pagedQuery[CarBrandTable](query.filter {
      _.name.like(s"%${name.toLowerCase}%")
    }.sortBy(_.name asc), range).result

}
