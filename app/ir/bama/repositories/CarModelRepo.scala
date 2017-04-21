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

import ir.bama.models.{CarBrand, CarModel}
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.{ForeignKeyQuery, Index, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class CarModelRepo @Inject()(dbConfigProvider: DatabaseConfigProvider, brandRepo: CarBrandRepo)
                            (implicit ec: ExecutionContext) extends BaseRepo[CarModel](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  private type CarModelRow = (Option[Long], Long, String)

  class CarModelTable(tag: Tag) extends Table[CarModel](tag, "T_CAR_MODEL") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def brandId: Rep[Long] = column[Long]("C_BRAND_ID", NotNull)

    def brand: ForeignKeyQuery[_, CarBrand] = foreignKey("FK_BRAND_MODEL", brandId, brandRepo.query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def name: Rep[String] = column[String]("C_NAME", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def idx: Index = index("unique_name_car_model", (brandId, name), unique = true)

    override def * : ProvenShape[CarModel] = (id.?, brandId, name) <> (toModel, fromModel)

  }

  private val toModel: CarModelRow => CarModel = {
    case (id, _, name) => CarModel(id, None, name)
  }

  private val fromModel: CarModel => Option[CarModelRow] = {
    case CarModel(id, Some(brand), name) => Some(id, brand.id.get, name)
  }

  override type TableType = CarModelTable
  override val query: TableQuery[CarModelTable] = TableQuery[CarModelTable]
  override protected val idColumn: (CarModelTable) => Rep[Long] = _.id

  override def persist(model: CarModel): DBIO[Long] =
    super.persist(model.copy(name = model.name.toLowerCase))

  override def load(id: Rep[Long]): DBIO[Option[CarModel]] =
    (for {
      m: CarModelTable <- query if m.id === id
      b: brandRepo.CarBrandTable <- brandRepo.query if b.id === m.brandId
    } yield (m, b)).result.headOption.map(_.map(refineModel))

  override def list(range: Option[Range]): DBIO[Seq[CarModel]] =
    (for {
      m: CarModelTable <- pagedQuery(range)
      b: brandRepo.CarBrandTable <- brandRepo.query if b.id === m.brandId
    } yield (m, b)).result.map(_.map(refineModel))

  private val refineModel: ((CarModel, CarBrand)) => CarModel = {
    case (m, b) => m.copy(brand = Some(b))
  }

  def listByBrandId(brandId: Long, range: Option[Range]): DBIO[Seq[CarModel]] =
    pagedQuery[CarModelTable](query.filter(_.brandId === brandId), range).result

  def searchByBrandIdAndName(brandId: Long, name: String, range: Option[Range]): DBIO[Seq[CarModel]] =
    pagedQuery[CarModelTable](query.filter { model =>
      model.brandId === brandId && model.name.like(s"%${name.toLowerCase}%")
    }.sortBy(_.name asc), range).result

}
