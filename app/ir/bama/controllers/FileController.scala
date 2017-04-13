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

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import play.api.Configuration
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class FileController @Inject()(configs: Configuration)(implicit mat: Materializer, ec: ExecutionContext) extends Controller {

  private lazy val filesDir = Paths.get(configs.getString("app.dir.files").get)

  def serveFile(name: String) = Action {
    val path = filesDir.resolve(name)
    if (Files.exists(path)) {
      Ok.chunked(FileIO.fromPath(path))
    } else {
      NotFound
    }
  }

}
