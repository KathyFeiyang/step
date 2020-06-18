// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.lang.Math;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public final class FindMeetingQuery {

  /**
  * Finds the available {@code TimeRange} for a set of mandatory and optional attendees to
  * satisfy a meeting {@code request}, given some existing {@code events} and their respective
  * attendees. If we can find {@code TimeRange} for both mandatory and optional attendees, we
  * return only those {@code TimeRange}; if not, we return all {@code TimeRange} that satisfy
  * the mandatory attendees. In the special case where there are no mandatory attendees, we
  * find the {@code TimeRange} that satisfy all optional attendees.
  * The resultant {@code TimeRange} do not overlap with one another and cover all possible
  * gaps between the existing {@code events}. These {@code TimeRange} have the same length as
  * or are longer than the requested meeting duration.
  * -- First extracts the existing {@code TimeRange} that correspond to {@code Event} having
  * overlapping attendees with the {@code request}; we only need to avoid conflicting with
  * those {@code TimeRange} that involve attendees contained in the {@code request}.
  * -- Then finds the available {@code TimeRange} sandwiched between an existing, ending
  * {@code TimeRange} and an existing starting {@code TimeRange}.
  * -- Finally finds the overlapping, available {@code TimeRange} shared by mandatory and
  * optional attendees.
  *
  * @param events Existing events that each occupy some {@code TimeRange}.
  * @param request A meeting request for some mandatory and optional attendees and a duration.
  * @return All available {@code TimeRange} that satisfy the meeting request.
  */
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    Collection<String> attendees = request.getAttendees();
    Collection<String> optionalAttendees = request.getOptionalAttendees();
    long requestedDuration = request.getDuration();

    if (requestedDuration > TimeRange.WHOLE_DAY.duration()) {
      return Arrays.asList();
    } else if (events.isEmpty() || (attendees.isEmpty() && optionalAttendees.isEmpty())) {
      return Arrays.asList(TimeRange.WHOLE_DAY);
    }

    Collection<TimeRange> occupiedTimeRanges =
        getConcernedAttendeesTimeRangesFromEvents(events, attendees);
    Collection<TimeRange> optionalOccupiedTimeRanges =
        getConcernedAttendeesTimeRangesFromEvents(events, optionalAttendees);

    List<TimeRange> availableTimeRangesForMandatoryAttendees =
        getAvailabeTimeRanges(occupiedTimeRanges, requestedDuration);
    List<TimeRange> availableTimeRangesForOptionalAttendees =
        getAvailabeTimeRanges(optionalOccupiedTimeRanges, requestedDuration);
    if (attendees.isEmpty()) {
      return availableTimeRangesForOptionalAttendees;
    }

    List<TimeRange> availableTimeRangesForMandatoryOptionalAttendees =
        getOverlappingAvailabeTimeRanges(requestedDuration,
                                         availableTimeRangesForMandatoryAttendees,
                                         availableTimeRangesForOptionalAttendees);

    return availableTimeRangesForMandatoryOptionalAttendees.isEmpty() ?
           availableTimeRangesForMandatoryAttendees :
           availableTimeRangesForMandatoryOptionalAttendees;
  }

  /**
  * Finds the existing {@code TimeRange} that correspond to {@code Event} having
  * overlapping attendees with a set of attendees that we are concerned with.
  */
  private Collection<TimeRange> getConcernedAttendeesTimeRangesFromEvents(
      Collection<Event> events,
      Collection<String> attendees) {
    Collection<TimeRange> concernedAttendeesTimeRanges = new HashSet<>();
    for (Event event : events) {
      if (!Collections.disjoint(attendees, event.getAttendees())) {
        concernedAttendeesTimeRanges.add(event.getWhen());
      }
    }
    return concernedAttendeesTimeRanges;
  }

  /**
  * Finds the available {@code TimeRange} having at least a length of {@code requestedDuration},
  * given some already occupied {@code TimeRange}.
  *
  * @param occupiedTimeRanges A collection of {@code TimeRange} that are considered occupied and
  *   need to be avoided.
  * @param requestedDuration The requested length of the available {@code TimeRange} to find.
  * @return A list of all available {@code TimeRange} sorted in ascending chronological order.
  */
  private List<TimeRange> getAvailabeTimeRanges(Collection<TimeRange> occupiedTimeRanges,
      long requestedDuration) {
    if (occupiedTimeRanges.isEmpty()) {
      return Arrays.asList(TimeRange.WHOLE_DAY);
    }

    List<TimeRange> availableTimeRanges = new LinkedList<>();
    List<TimeRange> startOrderedTimeRanges = getOrderedTimeRanges(occupiedTimeRanges,
                                                                  TimeRange.ORDER_BY_START);
    List<TimeRange> endOrderedTimeRanges = getOrderedTimeRanges(occupiedTimeRanges,
                                                                TimeRange.ORDER_BY_END);

    // Potentially add the {@code TimeRange} before the first occupied {@code TimeRange}.
    TimeRange firstStartingTimeRange = startOrderedTimeRanges.get(0);
    checkDurationAndAddAvailableTimeRange(requestedDuration,
                                          TimeRange.START_OF_DAY, firstStartingTimeRange.start(),
                                          availableTimeRanges);

    // Find available {@code TimeRange} by looking at the available time between the end of one
    // occupied {@code TimeRange} and the start of the next occupied {@code TimeRange}:
    //   |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
    ListIterator<TimeRange> startOrderedTimeRangesIterator = startOrderedTimeRanges.listIterator();
    ListIterator<TimeRange> endOrderedTimeRangesIterator = endOrderedTimeRanges.listIterator();
    TimeRange endingTimeRange = endOrderedTimeRangesIterator.next();
    TimeRange startingTimeRange = startOrderedTimeRangesIterator.next();

    findGapsInOccupiedTimeRanges:
    while (true) {

      while (endingTimeRange.overlaps(startingTimeRange)) {
        if (startOrderedTimeRangesIterator.hasNext()) {
          startingTimeRange = startOrderedTimeRangesIterator.next();
        } else {
          break findGapsInOccupiedTimeRanges;
        }
      }

      // There is another occupied {@code TimeRange} between the current ending {@code TimeRange}
      // and the next occupied {@code TimeRange}.
      // (Since there exists a {@code TimeRange} that occurs later than the current ending
      // {@code TimeRange}, we can be certain that invoking .next() won't go out of range.)
      TimeRange nextEndingTimeRange = endOrderedTimeRangesIterator.next();
      while (nextEndingTimeRange.end() <= startingTimeRange.start()) {
        endingTimeRange = nextEndingTimeRange;
        if (endOrderedTimeRangesIterator.hasNext()) {
          nextEndingTimeRange = endOrderedTimeRangesIterator.next();
        } else {
          break;
        }
      }
      endOrderedTimeRangesIterator.previous();

      // At this point, we have:
      //    |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
      int availableTimeRangeStart = endingTimeRange.end();
      int availableTimeRangeEnd = startingTimeRange.start();
      checkDurationAndAddAvailableTimeRange(requestedDuration,
                                            availableTimeRangeStart, availableTimeRangeEnd,
                                            availableTimeRanges);

      if (startOrderedTimeRangesIterator.hasNext() && endOrderedTimeRangesIterator.hasNext()) {
        startingTimeRange = startOrderedTimeRangesIterator.next();
        endingTimeRange = endOrderedTimeRangesIterator.next();
      } else {
        break findGapsInOccupiedTimeRanges;
      }
    }

    // Potentially add the {@code TimeRange} after the last occupied {@code TimeRange}.
    TimeRange lastEndingTimeRange = endOrderedTimeRanges.get(endOrderedTimeRanges.size() - 1);
    checkDurationAndAddAvailableTimeRange(requestedDuration,
                                          lastEndingTimeRange.end(), TimeRange.END_OF_DAY + 1,
                                          availableTimeRanges);

    return availableTimeRanges;
  }

  /**
  * Sorts {@code timeRanges} based on the specified {@code sortOrder}.
  */
  private List<TimeRange> getOrderedTimeRanges(Collection<TimeRange> timeRanges,
      Comparator<TimeRange> sortOrder) {
    List<TimeRange> orderedTimeRanges = new LinkedList<>(timeRanges);
    Collections.sort(orderedTimeRanges, sortOrder);
    return orderedTimeRanges;
  }

  /**
  * Finds the overlapping, available {@code TimeRange} of mandatory and optional attendees that
  * have at least a length of the requested duration.
  *
  * @param requestedDuration The requested length of the overlapping, available {@code TimeRange}
  *   to find.
  * @param availableTimeRangesForMandatoryAttendees A list of available {@code TimeRange} for the
  *   mandatory attendees. This list is expected to be sorted in ascending chronological order.
  * @param availableTimeRangesForOptionalAttendees A list of available {@code TimeRange} for the
  *   optional attendees. This list is expected to be sorted in ascending chronological order.
  * @return A list of all available {@code TimeRange} for both mandatory and optional attendees,
  *   sorted in ascending chronological order.
  */
  private List<TimeRange> getOverlappingAvailabeTimeRanges(long requestedDuration,
      List<TimeRange> availableTimeRangesForMandatoryAttendees,
      List<TimeRange> availableTimeRangesForOptionalAttendees) {
    if (availableTimeRangesForMandatoryAttendees.isEmpty()) {
      return Arrays.asList();
    } else if (availableTimeRangesForOptionalAttendees.isEmpty()) {
      return availableTimeRangesForMandatoryAttendees;
    }

    List<TimeRange> availableTimeRangesForMandatoryOptionalAttendees = new LinkedList<>();
    ListIterator<TimeRange> mandatoryAttendeesIterator =
        availableTimeRangesForMandatoryAttendees.listIterator();
    ListIterator<TimeRange> optionalAttendeesIterator =
        availableTimeRangesForOptionalAttendees.listIterator();
    TimeRange mandatoryAttendeesTimeRange = mandatoryAttendeesIterator.next();
    TimeRange optionalAttendeesTimeRange = optionalAttendeesIterator.next();

    findOverlappingAvailableTimeRanges:
    while (true) {

      while (!mandatoryAttendeesTimeRange.overlaps(optionalAttendeesTimeRange)) {
        if (mandatoryAttendeesTimeRange.end() <= optionalAttendeesTimeRange.start()) {
          if (mandatoryAttendeesIterator.hasNext()) {
            mandatoryAttendeesTimeRange = mandatoryAttendeesIterator.next();
          } else {
            break findOverlappingAvailableTimeRanges;
          }
        } else {
          if (optionalAttendeesIterator.hasNext()) {
            optionalAttendeesTimeRange = optionalAttendeesIterator.next();
          } else {
            break findOverlappingAvailableTimeRanges;
          }
        }
      }

      while (optionalAttendeesTimeRange.contains(mandatoryAttendeesTimeRange)) {
        availableTimeRangesForMandatoryOptionalAttendees.add(mandatoryAttendeesTimeRange);
        // Because the available {@code TimeRange} within a list do not overlap with each other,
        // we can be certain that this {@code TimeRange} for mandatory attendees will not overlap
        // with any other {@code TimeRange} for optional attendees.
        if (mandatoryAttendeesIterator.hasNext()) {
          mandatoryAttendeesTimeRange = mandatoryAttendeesIterator.next();
        } else {
          break findOverlappingAvailableTimeRanges;
        }
      }
      while (mandatoryAttendeesTimeRange.contains(optionalAttendeesTimeRange)) {
        availableTimeRangesForMandatoryOptionalAttendees.add(optionalAttendeesTimeRange);
        if (optionalAttendeesIterator.hasNext()) {
          optionalAttendeesTimeRange = optionalAttendeesIterator.next();
        } else {
          break findOverlappingAvailableTimeRanges;
        }
      }

      // At this point, we have two potential scenarios:
      // 1. |----mandatory attendees {@code TimeRange}----|
      //                       |----optional attendees {@code TimeRange}-----|
      //                       |--------------------------|
      // 2. |----optional attendees {@code TimeRange}-----|
      //                       |----mandatory attendees {@code TimeRange}----|
      //                       |--------------------------|
      int overlappingTimeRangeStart = Math.max(mandatoryAttendeesTimeRange.start(),
                                               optionalAttendeesTimeRange.start());
      int overlappingTimeRangeEnd = Math.min(mandatoryAttendeesTimeRange.end(),
                                               optionalAttendeesTimeRange.end());
      checkDurationAndAddAvailableTimeRange(requestedDuration,
                                            overlappingTimeRangeStart, overlappingTimeRangeEnd,
                                            availableTimeRangesForMandatoryOptionalAttendees);
      if (mandatoryAttendeesTimeRange.start() < optionalAttendeesTimeRange.start()) {
        // Scenario 1: move on to the next mandatory attendees' available {@code TimeRange}.
        if (mandatoryAttendeesIterator.hasNext()) {
          mandatoryAttendeesTimeRange = mandatoryAttendeesIterator.next();
        } else {
          break findOverlappingAvailableTimeRanges;
        }
      } else {
        // Scenario 2: move on to the next optional attendees' available {@code TimeRange}.
        if (optionalAttendeesIterator.hasNext()) {
          optionalAttendeesTimeRange = optionalAttendeesIterator.next();
        } else {
          break findOverlappingAvailableTimeRanges;
        }
      }
    }

    return availableTimeRangesForMandatoryOptionalAttendees;
  }

  /**
  * If a [start, end) represented {@code TimeRange} has a sufficient duration, adds it to the
  * set of available {@code TimeRange}.
  */
  private void checkDurationAndAddAvailableTimeRange(long requestedDuration, int start, int end,
      Collection<TimeRange> availableTimeRanges) {
    if (requestedDuration <= end - start) {
      availableTimeRanges.add(TimeRange.fromStartEnd(start, end, false));
    }
  }
}
