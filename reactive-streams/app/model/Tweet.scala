/**
 * reactive-streams
 *
 * Copyright 2016 juanitodread
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package model

import play.api.libs.json.Reads
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._
import play.api.libs.json.OWrites

case class Tweet(
  author: String,
  created: String,
  text: String
)

object TweetFormat {

  implicit val tweetWrites: OWrites[Tweet] = (
    (JsPath \ "author").write[String] and
    (JsPath \ "created").write[String] and
    (JsPath \ "text").write[String]
  )(unlift(Tweet.unapply))

  implicit val tweetReads: Reads[Tweet] = (
    (JsPath \ "user" \ "screen_name").read[String] and
    (JsPath \ "created_at").read[String] and
    (JsPath \ "text").read[String]
  )(Tweet.apply _)

}