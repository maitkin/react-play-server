package plugins

import akka.actor._
import play.api.Logger
import java.util.concurrent._
import java.util.concurrent.TimeUnit._
import scala.collection.mutable.Map
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.Date


case class SetTimer (duration: Long, sendTicks: Boolean, actor: ActorRef)
case class TimerTick(uuid: String, remaining: Long, elapsed: Long, isPaused: Boolean) {
  override def toString() : String = {
    "Remaining %d Elapsed %d".format(remaining/1000, elapsed/1000)
  }
}
case class Timer(
  actor : ActorRef,
  running: Boolean = false,
  pauseTime: Long = 0,
  pauseStartTime : Long = 0,
  isPaused: Boolean = false,
  endTime : Long = 0,
  startTime : Long = 0,
  isSet : Boolean = false,
  sendTicks : Boolean = false,
  lastTick : Long = 0
)
case class Poll()
case class PauseTimer(uuid: String)
case class ResumeTimer(uuid: String)
case class TimerAlarm(uuid: String, elapsed: Long)

class TimerPlugin extends Actor {

  val scheduler = Executors.newSingleThreadScheduledExecutor()
  val timers = Map.empty[String, Timer]

  scheduler.scheduleWithFixedDelay(new Thread{
    override def run() : Unit = {
      self ! Poll
    }
  }, 0, 500, TimeUnit.MILLISECONDS)


  def receive = {
    
    case (a : SetTimer) => {
      val now = new java.util.Date().getTime
      val uuid = java.util.UUID.randomUUID.toString
      timers(uuid) =
        Timer(
          isSet = true,
          startTime = now,
          endTime = now + a.duration,
          running = true,
          actor = a.actor,
          sendTicks = a.sendTicks
        )
      sender ! uuid
    }

    case (PauseTimer(uuid)) => {
      if (timers.contains(uuid)) {
        val a = timers(uuid)
        if (!a.isPaused && a.running) {
          timers(uuid) = a.copy(isPaused = true, pauseStartTime = new Date().getTime)
          timers(uuid).actor ! TimerTick(uuid, calcRemaining(timers(uuid)), calcElapsed(timers(uuid)), true)
        }
      }
    }

    case (ResumeTimer(uuid)) => {
      if (timers.contains(uuid)) {
        val a = timers(uuid)
        if (a.isPaused & a.running) {
          val paused_time = a.pauseTime + new Date().getTime() - a.pauseStartTime
          timers(uuid) = a.copy(isPaused = false, pauseTime = paused_time)
        }
      }
    }

    case (Poll) => {      
      checkTimers()
    }

    case msg => {
      Logger.warn("Unhandled message: " + msg)
    }

  }

  def calcRemaining(timer : Timer) : Long = {
    return timer.endTime - (new Date().getTime - timer.pauseTime)
  }

  def calcElapsed(timer : Timer) : Long = {
    return new Date().getTime - timer.startTime
  }

  def checkTimers() {

    for ( (uuid,timer) <- timers) {

      if (timer.running) {

        var curr_time : Long = new java.util.Date().getTime();
        var rel_curr_time : Long = curr_time - timer.pauseTime; // factor in pauses

        if (!timer.isPaused) {

          var time_remaining : Long = timer.endTime - rel_curr_time;
          var run_time = curr_time - timer.startTime;

          if (timer.isSet) {

            if (timer.sendTicks) {

              // only send a tick if one's transpired
              if ((curr_time - timer.lastTick) >= 999) {
                timer.actor ! TimerTick(uuid = uuid, remaining = time_remaining, elapsed = run_time, false)
                timers(uuid) = timer.copy(lastTick = curr_time)
              }
            }

            if (rel_curr_time >= timer.endTime) {
              timer.actor ! TimerTick(uuid = uuid, remaining = 0, elapsed = run_time, isPaused = false)
              timer.actor ! TimerAlarm(uuid = uuid, elapsed = run_time)
              timers(uuid) = timer.copy(isSet = false, running = false)           
              Logger.info("Alarm for "+uuid)
            }
          }
        }
      }
    }
  }
}



