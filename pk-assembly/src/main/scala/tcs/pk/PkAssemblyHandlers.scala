package tcs.pk

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.models.Id
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Tcshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class PkAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val lifecycleActor                = ctx.spawn(LifecycleActor.make(cswCtx), "LifecycleActor")
  private val eventHandlerActor             = ctx.spawn(EventHandlerActor.make(cswCtx), "EventHandlerActor")
  private val commandHandlerActor           = ctx.spawn(CommandHandlerActor.make(cswCtx, online = true, eventHandlerActor), "CommandHandlerActor")

  override def initialize(): Unit = {
    log.info("Initializing pk assembly...")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.debug("Inside JPkAssemblyHandlers: onSubmit()")

    commandHandlerActor.tell(CommandHandlerActor.SubmitCommand(runId, controlCommand))
    CommandResponse.Started(runId)

  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}