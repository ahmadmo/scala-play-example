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

package ir.bama.utils

import play.api.libs.json._

/**
  * @author ahmad
  */
object Enums {

  def fromJs[E <: Enumeration](js: JsValue)(enum: E): Either[String, E#Value] = {
    js.asOpt[String].map { s =>
      try {
        Right(enum.withName(s))
      } catch {
        case _: NoSuchElementException => Left(s"Enumeration expected of type: ${enum.getClass.getSimpleName}")
      }
    } getOrElse Left("String value expected")
  }

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = Reads[E#Value](fromJs(_)(enum).fold(JsError(_), JsSuccess(_)))

  def enumWrites[E <: Enumeration]: Writes[E#Value] = Writes[E#Value](v => JsString(v.toString))

  def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = Format(enumReads(enum), enumWrites)

}
