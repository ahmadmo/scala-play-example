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

import java.text.ParseException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import play.api.libs.json._

/**
  * @author ahmad
  */
object Dates {

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def fromJs(js: JsValue): Either[String, LocalDateTime] = {
    js match {
      case JsString(s) =>
        try {
          Right(LocalDateTime.parse(s, formatter))
        } catch {
          case _: ParseException => Left("Unknown date format")
        }
      case _ => Left("String value expected")
    }
  }

  val dateReads: Reads[LocalDateTime] = Reads[LocalDateTime](fromJs(_).fold(JsError(_), JsSuccess(_)))
  val dateWrites: Writes[LocalDateTime] = Writes[LocalDateTime] { v =>
    if (v == null) JsNull else JsString(formatter.format(v))
  }
  val dateFormat: Format[LocalDateTime] = Format(dateReads, dateWrites)

}
