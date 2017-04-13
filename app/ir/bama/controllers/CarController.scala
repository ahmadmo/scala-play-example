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

import javax.inject.{Inject, Singleton}

import ir.bama.models.{CarBrand, CarModel}
import ir.bama.services.{CarBrandService, CarModelService}
import ir.bama.utils.OptionalRangeLike
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Controller}

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class CarController @Inject()(brandService: CarBrandService, modelService: CarModelService)
                             (implicit ec: ExecutionContext) extends Controller {

  val nameForm: Form[String] = Form(single(
    "name" -> nonEmptyText(2, 120)
  ))

  def addBrand: Action[JsValue] = Action.async(parse.json) { implicit request =>
    nameForm.map { name =>
      brandService.persist(CarBrand(None, name)).saved
    }
  }

  def listBrands(offset: Option[Int], length: Option[Int]): Action[AnyContent] = Action.async {
    brandService.list(offset ~ length).asJson
  }

  def searchBrands(name: String, offset: Option[Int], length: Option[Int]): Action[AnyContent] = Action.async {
    brandService.searchByName(name, offset ~ length).asJson
  }

  def addModel(brandId: Long): Action[JsValue] = Action.async(parse.json) { implicit request =>
    nameForm.map { name =>
      modelService.persist(CarModel(None, Some(CarBrand.id(brandId)), name)).saved
    }
  }

  def listModels(brandId: Long, offset: Option[Int], length: Option[Int]): Action[AnyContent] = Action.async {
    modelService.listByBrandId(brandId, offset ~ length).asJson
  }

  def searchModels(brandId: Long, name: String, offset: Option[Int], length: Option[Int]): Action[AnyContent] = Action.async {
    modelService.searchByBrandIdAndName(brandId, name, offset ~ length).asJson
  }

}
