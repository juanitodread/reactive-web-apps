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
package actors

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.ws.WS

import util.Common

/**
 * Actor to stream tweets to a web socket
 *
 * @author juanitodread
 *
 */
class TwitterStreamer(out: ActorRef, track: String) extends Actor {
  final val logger = TwitterStreamer.logger

  def receive = {
    case "subscribe" =>
      logger.info("Received subscription from a client")
      TwitterStreamer.subscribe(out, track)
  }
}

object TwitterStreamer {
  final val logger = Logger("TwitterStreamer")

  private var broadcastEnumerator: Option[Enumerator[JsValue]] = None

  def props(out: ActorRef, track: String) = Props(new TwitterStreamer(out, track))

  def connect(track: String): Unit = {
    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    Common.oAuthAccess.map {
      case (key, token) =>
        logger.info("Starting connection with Actor")
        logger.info(s"Track: $track")

        // Create iteratee and enumerator
        val (iteratee, enumerator) = Concurrent.joined[Array[Byte]]

        // Send enumerator to our newTweetStream to transform to 
        // Enumerator of Tweet 
        val tweetStream = Common.newTweetStream(enumerator)

        val (be, _) = Concurrent.broadcast(tweetStream)
        broadcastEnumerator = Some(be)

        WS.url(url)
          .withRequestTimeout(-1)
          .sign(OAuthCalculator(key, token))
          .postAndRetrieveStream(Map("track" -> Seq(track))) { response =>
            logger.info(s"Status: ${response.status}")
            iteratee
          }.map { _ =>
            logger.info("Twitter stream closed")
          }
    } getOrElse {
      logger.error("Credentials not found")
    }
  }

  def subscribe(out: ActorRef, track: String): Unit = {
    logger.info(s"Entry to subscribe: ActorRef: $out, track: $track")
    
    if (broadcastEnumerator.isEmpty) {
      connect(track)
    }

    val twitterClient = Iteratee.foreach[JsValue] { tweet => out ! tweet }

    broadcastEnumerator.foreach { enumerator =>
      enumerator run twitterClient
    }
  }
  
  def subscribeNode(track: String): Enumerator[JsValue] = {
    if(broadcastEnumerator.isEmpty) {
      connect(track)
    }
    broadcastEnumerator.getOrElse(Enumerator.empty[JsValue])
  }

}