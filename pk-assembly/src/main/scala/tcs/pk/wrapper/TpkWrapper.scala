package tcs.pk.wrapper

import akka.actor.typed.ActorRef
import tcs.pk.EventHandlerActor

/**
 * This is a wrapper class for TPK and will act as an endpoint. It helps in
 * calling TPK New Target and Offset methods so that specific demands can be
 * generated by TPK System
 *
 */
object TpkWrapper {
  System.loadLibrary("pk-jni")
}

class TpkWrapper(var eventHandlerActor: ActorRef[EventHandlerActor.EventMessage]) {
  // XXX TODO FIXME
  private var tpkEndpoint    = new TpkC
  private var publishDemands = false

  /**
   * Callback which is register with the C++ code and call from the fast
   * loop
   *
   */
  class DemandsCallback extends IDemandsCB {
    private val ci   = 32.5
    private val ciz  = 90 - ci
    //noinspection ScalaUnusedSymbol
    private val phir = Math.PI * ci / 180
    private val tci  = Math.tan(ci)
    private val cci  = Math.cos(ci)
    private val PI2  = Math.PI * 2

    override def newDemands(mcsAz: Double, mcsEl: Double, ecsAz: Double, ecsEl: Double, m3Rotation: Double, m3Tilt: Double): Unit = { // Convert eAz, eEl into base & cap coordinates
      // XXX TODO FIXME
      var azShift = .0
      var base1   = .0
      var cap1    = .0
      var base2   = .0
      var cap2    = .0
      val ecsEl2  = if ((ecsEl > PI2) || (ecsEl < 0)) 0 else ecsEl
      val ecsAz2  = if ((ecsAz > PI2) || (ecsAz < 0)) 0 else ecsAz
      cap1 = Math.acos(Math.tan(ecsEl2 - ciz) / tci)
      cap2 = PI2 - cap1
      if (ecsEl2 == PI2) azShift = 0
      else azShift = Math.atan(Math.sin(cap1) / cci * (1 - Math.cos(cap1)))
      if ((ecsAz2 + azShift) > PI2) base1 = (ecsAz2 + azShift) - PI2
      else base1 = ecsAz2 + azShift
      if (ecsAz2 < azShift) base2 = PI2 + ecsAz2 - azShift
      else base2 = ecsAz2 - azShift
      base1 = 180 * base1 / Math.PI
      cap1 = 180 * cap1 / Math.PI
      // Below condition will help in preventing TPK Default Demands
      // from getting published and Demand Publishing will start only
      // once New target or Offset Command is being received
      if (publishDemands) {
        publishMcsDemand(mcsAz, mcsEl)
        publishEcsDemand(base1, cap1)
        publishM3Demand(m3Rotation, m3Tilt)
      }
    }
  }

  /**
   * This will help registering and Initializing TPK, once this method is
   * invoked TPK will start generation default Demands
   */
  def initiate(): Unit = {
    val cb = new DemandsCallback
    tpkEndpoint = new TpkC
    tpkEndpoint._register(cb)
    tpkEndpoint.init()
  }

  /**
   * New target from Ra, Dec in degrees. Target applies to Mount and
   * Enclosure
   */
  def newTarget(ra: Double, dec: Double): Unit = {
    publishDemands = true
    tpkEndpoint.newTarget(ra, dec)
  }

  /**
   * This helps in publishing MCS specific Az and El being generated by TPK as
   * Demand
   */
  def publishMcsDemand(az: Double, el: Double): Unit = {
    eventHandlerActor.tell(EventHandlerActor.M3Demand(az, el))
  }

  /**
   * This helps in publishing ECS specific Base and Cap being generated by TPK as
   * Demand
   */
  def publishEcsDemand(base: Double, cap: Double): Unit = {
    eventHandlerActor.tell(EventHandlerActor.EncDemand(base, cap))
  }

  /**
   * This helps in publishing M3 specific Rotation and tilt being generated by TPK as
   * Demand
   */
  def publishM3Demand(rotation: Double, tilt: Double): Unit = {
    eventHandlerActor.tell(EventHandlerActor.M3Demand(rotation, tilt))
  }
}
