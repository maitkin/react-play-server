package controllers

import plugins._
import play.api.mvc._
import play.api.libs.streams._
import akka.actor._
import javax.inject._
import akka.stream._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Logger
import play.api.libs.json._

class WebSocketCtrl @Inject() (implicit system: ActorSystem, materializer: Materializer) {

  /* Create an instance of the timer to use with web-clients */
  val timer = system.actorOf( Props[TimerPlugin], "timer-plugin")

  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef(out => MyWebSocketActor.props(out,timer))
  }
}

object MyWebSocketActor {
  def props(out: ActorRef, timer: ActorRef) = Props(new MyWebSocketActor(out,timer))
}


class MyWebSocketActor(out: ActorRef, timer: ActorRef) extends Actor {

  // Respond to both TimerPlugin and WebSocket origin messages
  def receive = {

    case (tick : TimerTick) => {
      out ! ("{ \"event\": \"timer-tick\", \"id\": \""+tick.uuid+"\", \"remaining\": \""+tick.remaining+"\" , \"isPaused\": \""+tick.isPaused+"\" }")
    }

    case (alarm : TimerAlarm) => {
      out ! ("{ \"event\": \"timer-alarm\", \"id\": \""+alarm.uuid+"\", \"elapsed\": \""+alarm.elapsed+"\" }")
    }

    case msg: String => {
      val json = Json.parse(msg)
      (json \ "action").as[String] match {
        case "set-timer" => {
          val duration = (json \ "value").as[String]
          implicit val timeout = Timeout(5 seconds)
          val future = timer ? SetTimer(duration.toLong, true, self)
          val uuid = Await.result(future, timeout.duration).asInstanceOf[String]          
        }
        case "pause-timer" => {
          val uuid = (json \ "value").as[String]          
          timer ! PauseTimer(uuid)
        }
        case "resume-timer" => {
          val uuid = (json \ "value").as[String]          
          timer ! ResumeTimer(uuid)
        }
        case _ =>  { Logger.warn("Unhandled message") }
      }
    }
  }
}
