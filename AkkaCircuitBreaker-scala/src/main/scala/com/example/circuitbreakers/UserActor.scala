package com.example.circuitbreakers

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

class UserActor(service: ActorRef[Service.Message])(implicit
                                            system: ActorSystem[Nothing]
) {

  implicit val executionContext: ExecutionContext = system.executionContext

  // Max time the user will stick around waiting for a response
  implicit val userWaitTimeout: Timeout = Timeout(3.seconds)



  def behavior(delay: FiniteDuration = 1 seconds): Behavior[Service.Message] = {
    Behaviors.receive { (ctx, message) =>
      val self = ctx.self
      message match {
        case Service.Start =>
          // println("Start from user")
          sendRequest(delay = delay, self = self)
          Behaviors.same
        case Service.Response =>
          println("Got a quick response, I'm a happy actor")
          Behaviors.same
        // This timeout happens if the service does not respond after a while
        case Service.TimeoutFailure =>
          println("Got bored of waiting, I'm outta here!")
          Behaviors.same
        // This failure happens quickly if the Circuit Breakers are enabled
        case Service.Failure(_: Exception) =>
          sendRequest(7.seconds, ctx.self)
          Behaviors.same
        case other =>
          println(s"Got another message")
          Behaviors.same
      }
    }
  }

  private def sendRequest(
                           delay: FiniteDuration = 1.second,
                           self: ActorRef[Service.Message]
                         ) = {
    self ! Await.result(service ? ((ref: ActorRef[Service.Message]) => Service.Request(ref)), 10.seconds)
  }

}
