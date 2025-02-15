package tcs.mcs

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.AltAzCoord
import csw.params.core.models.Id
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.time.core.models.UTCTime
import tcs.shared.SimulationUtil

import scala.concurrent.ExecutionContextExecutor

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Tcshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */

object McsAssemblyHandlers {
  private val pkAssemblyPrefix                = Prefix(TCS, "PointingKernelAssembly")
  private val pkMountDemandPosEventKey        = EventKey(pkAssemblyPrefix, EventName("MountDemandPosition"))
  private val pkDemandPosKey: Key[AltAzCoord] = KeyType.AltAzCoordKey.make("pos")
  private val pkEventKeys                     = Set(pkMountDemandPosEventKey)

  // Actor to receive Assembly events
  private object EventHandlerActor {
    private val currentPosKey: Key[AltAzCoord] = KeyType.AltAzCoordKey.make("current")
    private val demandPosKey: Key[AltAzCoord]  = KeyType.AltAzCoordKey.make("demand")
    private val mcsTelPosEventName             = EventName("MountPosition")

    def make(cswCtx: CswContext): Behavior[Event] = {
      Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
    }
  }

  private class EventHandlerActor(ctx: ActorContext[Event], cswCtx: CswContext, maybeCurrentPos: Option[AltAzCoord] = None)
      extends AbstractBehavior[Event](ctx) {
    import EventHandlerActor._
    import cswCtx._
    private val log       = loggerFactory.getLogger
    private val publisher = cswCtx.eventService.defaultPublisher
    private var count     = 0

    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          if (e.eventKey == pkMountDemandPosEventKey) {
            // Note from doc: Mount accepts demands at 100Hz and enclosure accepts demands at 20Hz
            // Assuming we are receiving MountDemandPosition events at 100hz, we want to publish at 1hz.
            count = (count + 1) % 100
            if (count == 0) {
              val altAzCoordDemand = e(pkDemandPosKey).head
              maybeCurrentPos match {
                case Some(currentPos) =>
                  val newPos = getNextPos(altAzCoordDemand, currentPos)
                  val newEvent = SystemEvent(cswCtx.componentInfo.prefix, mcsTelPosEventName)
                    .madd(currentPosKey.set(newPos), demandPosKey.set(altAzCoordDemand))
                  publisher.publish(newEvent)
                  new EventHandlerActor(ctx, cswCtx, Some(newPos))
                case None =>
                  new EventHandlerActor(ctx, cswCtx, Some(altAzCoordDemand))
              }
            }
            else Behaviors.same
          }
          else Behaviors.same
        case x =>
          log.error(s"Expected SystemEvent but got $x")
          Behaviors.same
      }
    }

    // Simulate converging on the target
    private def getNextPos(targetPos: AltAzCoord, currentPos: AltAzCoord): AltAzCoord = {
      // The max slew for az is 2.5 deg/sec.  Max for el is 1.0 deg/sec
      val azSpeed = 2.5 // deg/sec
      val elSpeed = 1.0 // deg/sec
      val rate    = 1.0 // hz
      val factor  = 2.0 // Speedup factor for test/demo
      AltAzCoord(
        targetPos.tag,
        SimulationUtil.move(elSpeed * factor, rate, targetPos.alt, currentPos.alt),
        SimulationUtil.move(azSpeed * factor, rate, targetPos.az, currentPos.az)
      )
    }
  }

}

class McsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
  import McsAssemblyHandlers._
  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  override def initialize(): Unit = {
    log.info("Initializing MCS assembly...")
    val subscriber   = cswCtx.eventService.defaultSubscriber
    val eventHandler = ctx.spawn(EventHandlerActor.make(cswCtx), "McsAssemblyEventHandler")
    subscriber.subscribeActorRef(pkEventKeys, eventHandler)
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = Completed(runId)

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
