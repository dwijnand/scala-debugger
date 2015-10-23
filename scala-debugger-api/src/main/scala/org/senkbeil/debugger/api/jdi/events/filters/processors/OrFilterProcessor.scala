package org.senkbeil.debugger.api.jdi.events.filters.processors

import com.sun.jdi.event.Event
import org.senkbeil.debugger.api.jdi.events.JDIEventArgument
import org.senkbeil.debugger.api.jdi.events.filters.{OrFilter, AndFilter, JDIEventFilterProcessor}

/**
 * Represents a processor for the 'or' filter.
 *
 * @param orFilter The 'or' filter to use when processing
 */
class OrFilterProcessor(
  val orFilter: OrFilter
) extends JDIEventFilterProcessor {
  private val filters = orFilter.filters

  /**
   * Processes the provided event with the filter logic.
   *
   * @param event Provided to all internal filters
   *
   * @return True if the event passes through the filter, otherwise false
   */
  override def process(event: Event): Boolean = {
    val results = filters.map(_.toProcessor)
      .map(_.asInstanceOf[JDIEventFilterProcessor])
      .map(_.process(event))

    if (results.nonEmpty) results.find(_ == true).nonEmpty
    else true
  }

  /**
   * Resets the internal state of the filter.
   */
  override def reset(): Unit = {}

  override val argument: JDIEventArgument = orFilter
}
