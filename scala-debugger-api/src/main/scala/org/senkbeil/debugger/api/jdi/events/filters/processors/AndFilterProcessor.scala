package org.senkbeil.debugger.api.jdi.events.filters.processors

import com.sun.jdi.event.Event
import org.senkbeil.debugger.api.jdi.events.JDIEventArgument
import org.senkbeil.debugger.api.jdi.events.filters.{AndFilter, JDIEventFilterProcessor}

/**
 * Represents a processor for the 'and' filter.
 *
 * @param andFilter The 'and' filter to use when processing
 */
class AndFilterProcessor(
  val andFilter: AndFilter
) extends JDIEventFilterProcessor {
  private val filters = andFilter.filters

  /**
   * Processes the provided event with the filter logic.
   *
   * @param event Provided to all internal filters
   *
   * @return True if the event passes through the filter, otherwise false
   */
  override def process(event: Event): Boolean = {
    filters.map(_.toProcessor).map(_.asInstanceOf[JDIEventFilterProcessor])
      .map(_.process(event)).forall(_ == true)
  }

  /**
   * Resets the internal state of the filter.
   */
  override def reset(): Unit = {}

  override val argument: JDIEventArgument = andFilter
}
