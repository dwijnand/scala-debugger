package org.scaladebugger.api.profiles.swappable.monitors

import org.scaladebugger.api.lowlevel.JDIArgument
import org.scaladebugger.api.pipelines.Pipeline.IdentityPipeline
import org.scaladebugger.api.profiles.swappable.SwappableDebugProfile
import org.scaladebugger.api.profiles.traits.monitors.MonitorContendedEnterProfile

import scala.util.Try

/**
 * Represents a swappable profile for monitor contended enter events that
 * redirects the invocation to another profile.
 */
trait SwappableMonitorContendedEnterProfile extends MonitorContendedEnterProfile {
  this: SwappableDebugProfile =>

  override def onMonitorContendedEnterWithData(
    extraArguments: JDIArgument*
  ): Try[IdentityPipeline[MonitorContendedEnterEventAndData]] = {
    withCurrentProfile.onMonitorContendedEnterWithData(extraArguments: _*)
  }
}