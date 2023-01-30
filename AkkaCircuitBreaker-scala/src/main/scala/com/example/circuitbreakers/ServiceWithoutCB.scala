package com.example.circuitbreakers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}

class ServiceWithoutCB(implicit system: ActorSystem[Nothing]) extends Service {

  implicit val executionContext: ExecutionContext = system.executionContext

  def behavior(): Behavior[Service.Message] =
    Behaviors.receive { (ctx, message) =>
      message match {
        case Service.Request(replyTo) =>
          try {
            Await.result(Future(replyTo ! callWebService()), 200.millis)
          } catch {
            case _: TimeoutException =>
              replyTo ! Service.TimeoutFailure
            case e =>
              replyTo ! Service.Failure(ex = e)
          }
          Behaviors.same
      }
    }
}
