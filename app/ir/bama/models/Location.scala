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

package ir.bama.models

import play.api.libs.json.{Json, OFormat}

/**
  * @author ahmad
  */
trait Location extends Identifiable {
  val name: String
}

case class Province(id: Option[Long], name: String) extends Location

object Province {
  implicit val format: OFormat[Province] = Json.format[Province]
}

case class City(id: Option[Long], province: Option[Province], name: String) extends Location

object City {

  implicit val format: OFormat[City] = Json.format[City]

  def id(id: Long) = City(Some(id), None, null)

}
