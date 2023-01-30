package com.example.circuitbreakers

import akka.actor.typed.ActorRef
import com.example.circuitbreakers.Service.Response

import scala.util.Random

object Service {
  sealed trait Message
  case class Request(replyTo: ActorRef[Message]) extends Message
  case object Response extends Message
  case object Start extends Message
  case class Failure(ex: Throwable) extends Message
  case object TimeoutFailure extends Message
}

trait Service {

  // Max count and delays
  private val normalDelay = 100
  private val restartDelay = 3200 // Exercise: Test with < 3000 and > 3000

  def callWebService(): Response.type = {

    if (Random.nextDouble() <= 0.9) {
      Thread.sleep(normalDelay)
    } else {
      // Service shuts down, takes a while to come back up
      println("!! Service overloaded !! Restarting !!")
      Thread.sleep(restartDelay)
    }

    Service.Response
  }
}
