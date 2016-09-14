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
package util

import model.Tweet

import model.TweetFormat._

import play.api.Logger
import play.api.Play
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.RequestToken
import play.extras.iteratees.Encoding
import play.extras.iteratees.JsonIteratees

/**
 * Utility class
 *
 * @author juanitodread
 *
 */
object Common {

  private def twitterCredentials() = for {
    apiKey <- Play.application.configuration.getString("twitter.apiKey")
    apiSecret <- Play.application.configuration.getString("twitter.apiSecret")
    token <- Play.application.configuration.getString("twitter.token")
    tokenSecret <- Play.application.configuration.getString("twitter.tokenSecret")
  } yield (apiKey, apiSecret, token, tokenSecret)

  def oAuthAccess() = twitterCredentials map {
    case (apiKey, apiSecret, token, tokenSecret) =>
      (ConsumerKey(apiKey, apiSecret), RequestToken(token, tokenSecret))
  }

  def loggingIteratee(logger: Logger) = Iteratee.foreach[Array[Byte]] { array =>
    logger.info(array.map(x => x.toChar).mkString)
  }

  def loggingTweetIteratee(logger: Logger) = Iteratee.foreach[Tweet] { tweet =>
    logger.info(tweet.toString)
  }

  def newTweetStream(enumerator: Enumerator[Array[Byte]]): Enumerator[Tweet] = {
    enumerator &>
      Encoding.decode() &>
      Enumeratee.grouped(JsonIteratees.jsValue) &>
      Enumeratee.map { js =>
        js.validate[Tweet].getOrElse(Tweet("", "", ""))
      }
  }

}