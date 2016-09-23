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
package controllers

import scala.concurrent.Future

import actors.TwitterStreamer

import javax.inject.Inject

import model.Tweet
import model.TweetFormat._

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WSClient
import play.api.mvc.Action
import play.api.mvc.Controller
import play.api.mvc.WebSocket

import util.Common

/**
 * Default app controller
 *
 * @author juanitodread
 *
 */
class Application @Inject() (ws: WSClient) extends Controller {
  final val logger = Logger(this.getClass)

  def index(track: Option[String]) = Action { implicit request =>
    logger.info(s"Entry to index - request: $request")

    Ok(views.html.index())
  }

  def sayHelloWebSocket = WebSocket.using[String] { request =>
    logger.info(s"Entry to sayHelloWebSocket - request: $request")

    // Concurrent.broadcast returns (Enumerator, Concurrent.Channel)
    val (out, channel) = Concurrent.broadcast[String]

    // log the message to stdout and send response back to client
    val in = Iteratee.foreach[String] {
      msg =>
        logger.info(s"Message received: $msg")

        // the Enumerator returned by Concurrent.broadcast subscribes to the channel and will
        // receive the pushed messages
        channel.push(s"Hello from server! I have received your message: $msg")
    }
    (in, out)
  }

  def tweetList(q: Option[String]) = Action.async { request =>
    logger.info(s"Entry to tweetList - request: $request")

    val results = 10
    val query = q.getOrElse("reactive")
    val url = "https://api.twitter.com/1.1/search/tweets.json"

    logger.info(s"Url: $url, Query: $query, Max results: $results")

    Common.oAuthAccess.map {
      case (key, token) =>
        val responseFuture = ws.url(url)
          .sign(OAuthCalculator(key, token))
          .withQueryString(
            "q" -> query,
            "count" -> results.toString
          ).get

        responseFuture.map { response =>
          val tweets = (response.json \ "statuses").validate[List[Tweet]]
            .getOrElse(List[Tweet]())
          Ok(Json.toJson(tweets))
        }
    } getOrElse {
      logger.error("Credentials not found")
      Future.successful(InternalServerError("Credentials not found"))
    }
  }

  def postTweet(message: String) = Action.async { request =>
    logger.info(s"Entry to postTweet - request: $request")

    message match {
      case msg if msg.length <= 140 => {

        Common.oAuthAccess.map {
          case (key, token) =>
            val url = "https://api.twitter.com/1.1/statuses/update.json"
            val tweet = Map("status" -> Seq(message))

            logger.info(s"Tweeting: $tweet to $url")

            val responseFuture = ws.url(url)
              .sign(OAuthCalculator(key, token))
              .post(tweet)

            responseFuture.map { response =>
              Ok(response.body)
            }
        } getOrElse {
          logger.error("Credentials not found")
          Future.successful(InternalServerError("Credentials not found"))
        }
      }
      case _ =>
        logger.error("Invalid length for tweet")
        Future.successful(BadRequest("Invalid length for tweet"))
    }
  }

  def tweeterStream(track: String) = Action.async { request =>
    logger.info(s"Entry to tweeterStream - request: $request")

    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    Common.oAuthAccess.map {
      case (key, token) =>

        // Create iteratee and enumerator
        val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

        // Send enumerator to our newTweetStream to transform to 
        // Enumerator of Tweet 
        val tweetStream = Common.newTweetStream(enumerator, true)
        tweetStream.run(Common.loggingTweetIteratee(logger))

        val responseFuture = ws.url(url)
          .sign(OAuthCalculator(key, token))
          .withQueryString("track" -> track)
          .get(response => iteratee)

        responseFuture.map { response =>
          Ok("Stream closed")
        }
    } getOrElse {
      logger.error("Credentials not found")
      Future.successful(InternalServerError("Credentials not found"))
    }
  }

  def tweeterActorStream(track: String) = WebSocket.acceptWithActor[String, JsValue] { request =>
    logger.info(s"Entry to tweeterActorStream - request: $request")
    out => TwitterStreamer.props(out, track)
  }

  def replicaTweeterActorStream(track: String) = Action { request =>
    logger.info(s"Entry to replicaTweeterActorStream - request: $request")
    Ok.feed(TwitterStreamer.subscribeNode(track))
  }

}