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
   * attendees. If we can find available {@code TimeRange} for both mandatory and optional
   * attendees, we return only those {@code TimeRange}. If not, we return all {@code TimeRange}
   * that satisfy the mandatory attendees. In the special case where there are no mandatory
   * attendees, we find the {@code TimeRange} that satisfy all optional attendees.
   * The resultant {@code TimeRange} do not overlap with one another and cover all possible
   * empty gaps in the existing {@code events}. These {@code TimeRange} have the same length as
   * or are longer than the requested meeting duration.
   * -- First extracts the existing {@code TimeRange} of those {@code Event} that include any
   * attendees mentioned in the {@code request}. We only need to avoid conflicting with
   * those {@code TimeRange} that involve attendees mentioned in the {@code request}.
   * -- Then finds the available {@code TimeRange} sandwiched between two already-occupied
   * {@code TimeRange}, for mandatory and optional attendees respectively.
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
   * Finds the {@code TimeRange} of {@code events} that include the given {@code attendees}.
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

    List<TimeRange> startOrderedTimeRanges = getOrderedTimeRanges(occupiedTimeRanges,
                                                                  TimeRange.ORDER_BY_START);
    List<TimeRange> endOrderedTimeRanges = getOrderedTimeRanges(occupiedTimeRanges,
                                                                TimeRange.ORDER_BY_END);
    List<TimeRange> availableTimeRanges = new LinkedList<>();

    // Potentially add the {@code TimeRange} before the first occupied {@code TimeRange}.
    TimeRange firstStartingTimeRange = startOrderedTimeRanges.get(0);
    checkDurationAndAddAvailableTimeRange(requestedDuration,
                                          TimeRange.START_OF_DAY, firstStartingTimeRange.start(),
                                          availableTimeRanges);
    findAndAddGapsInOccupiedTimeRanges(requestedDuration,
                                       startOrderedTimeRanges, endOrderedTimeRanges,
                                       availableTimeRanges);
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
   * Finds available {@code TimeRange} by looking at the available time between the end of one
   * occupied {@code TimeRange} and the start of the next occupied {@code TimeRange}:
   *   |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
   *
   * @param requestedDuration The requested length of the available {@code TimeRange} to find.
   * @param startOrderedTimeRanges A start-time ordered list of occupied {@TimeRange}. This list
   *     is expected to be non-empty.
   * @param endOrderedTimeRanges An end-time ordered list of occupied {@TimeRange}. This list is
   *     expected to be non-empty.
   * @param availableTimeRanges A list of available {@code TimeRange} that avoid the already
   *     occupied {@code TimeRange}.
   */
  private void findAndAddGapsInOccupiedTimeRanges(long requestedDuration,
      List<TimeRange> startOrderedTimeRangesList,
      List<TimeRange> endOrderedTimeRangesList,
      Collection<TimeRange> availableTimeRanges) {
    TimeRange[] startOrderedTimeRanges = startOrderedTimeRangesList.toArray(new TimeRange[0]);
    TimeRange[] endOrderedTimeRanges = endOrderedTimeRangesList.toArray(new TimeRange[0]);
    int startOrderedPointer = 0;
    int endOrderedPointer = 0;

    while (true) {

      while (endOrderedTimeRanges[endOrderedPointer]
          .overlaps(startOrderedTimeRanges[startOrderedPointer])) {
        if (startOrderedPointer + 1 < startOrderedTimeRanges.length) {
          startOrderedPointer++;
        } else {
          return;
        }
      }

      // Find the latest occupied {@code TimeRange} that occurs before the starting
      // {@code TimeRange}.
      // (Since there exists a {@code TimeRange} that occurs later than the current ending
      // {@code TimeRange}, we can be certain that {@code endOrderedPointer + 1} won't go out
      // of range.
      while (endOrderedTimeRanges[endOrderedPointer + 1].end() <=
          startOrderedTimeRanges[startOrderedPointer].start()) {
        endOrderedPointer++;
        if (!(endOrderedPointer + 1 < endOrderedTimeRanges.length)) {
          break;
        }
      }

      // At this point, we have:
      //    |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
      int availableTimeRangeStart = endOrderedTimeRanges[endOrderedPointer].end();
      int availableTimeRangeEnd = startOrderedTimeRanges[startOrderedPointer].start();
      checkDurationAndAddAvailableTimeRange(requestedDuration,
                                            availableTimeRangeStart, availableTimeRangeEnd,
                                            availableTimeRanges);

      if (startOrderedPointer + 1 < startOrderedTimeRanges.length &&
          endOrderedPointer + 1 < endOrderedTimeRanges.length) {
        startOrderedPointer++;
        endOrderedPointer++;
      } else {
        return;
      }
    }
  }

  /**
   * Finds the overlapping, available {@code TimeRange} for mandatory and optional attendees. 
   * If no {@code TimeRange} satisfies all mandatory and optional attendees, returns those
   * {@code TimeRange} that satisfies mandatory attendees.
   *
   * @param requestedDuration The requested length of the overlapping, available {@code TimeRange}
   *     to find.
   * @param availableTimeRangesForMandatoryAttendees A list of available {@code TimeRange} for the
   *     mandatory attendees. This list is expected to be sorted in ascending chronological order.
   * @param availableTimeRangesForOptionalAttendees A list of available {@code TimeRange} for the
   *     optional attendees. This list is expected to be sorted in ascending chronological order.
   * @return A list of all available {@code TimeRange} for both mandatory and optional attendees,
   *     sorted in ascending chronological order.
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
    findAndAddOverlappingAvailableTimeRanges(requestedDuration,
                                             availableTimeRangesForMandatoryAttendees,
                                             availableTimeRangesForOptionalAttendees,
                                             availableTimeRangesForMandatoryOptionalAttendees);
    return availableTimeRangesForMandatoryOptionalAttendees;
  }

  /**
   * Finds and adds the overlapping {@code TimeRange} for mandatory and optional attendees. These
   * {@code TimeRange} have at least a length of the requested duration.
   */
  private void findAndAddOverlappingAvailableTimeRanges(long requestedDuration,
      List<TimeRange> timeRangesForMandatoryAttendeesList,
      List<TimeRange> timeRangesForOptionalAttendeesList,
      List<TimeRange> timeRangesForMandatoryOptionalAttendees) {
    TimeRange[] timeRangesForMandatoryAttendees =
        timeRangesForMandatoryAttendeesList.toArray(new TimeRange[0]);
    TimeRange[] timeRangesForOptionalAttendees =
        timeRangesForOptionalAttendeesList.toArray(new TimeRange[0]);
    int mandatoryAttendeesPointer = 0;
    int optionalAttendeesPointer = 0;

    while (true) {

      while (!timeRangesForMandatoryAttendees[mandatoryAttendeesPointer]
          .overlaps(timeRangesForOptionalAttendees[optionalAttendeesPointer])) {
        if (timeRangesForMandatoryAttendees[mandatoryAttendeesPointer].end() <=
            timeRangesForOptionalAttendees[optionalAttendeesPointer].start()) {
          if (mandatoryAttendeesPointer + 1 < timeRangesForMandatoryAttendees.length) {
            mandatoryAttendeesPointer++;
          } else {
            return;
          }
        } else {
          if (optionalAttendeesPointer + 1 < timeRangesForOptionalAttendees.length) {
            optionalAttendeesPointer++;
          } else {
            return;
          }
        }
      }

      while (timeRangesForOptionalAttendees[optionalAttendeesPointer]
          .contains(timeRangesForMandatoryAttendees[mandatoryAttendeesPointer])) {
        timeRangesForMandatoryOptionalAttendees.add(timeRangesForMandatoryAttendees[mandatoryAttendeesPointer]);
        // Because the available {@code TimeRange} within a list do not overlap with each other,
        // we can be certain that this {@code TimeRange} for mandatory attendees will not overlap
        // with any other {@code TimeRange} for optional attendees.
        if (mandatoryAttendeesPointer + 1 < timeRangesForMandatoryAttendees.length) {
          mandatoryAttendeesPointer++;
        } else {
          return;
        }
      }
      while (timeRangesForMandatoryAttendees[mandatoryAttendeesPointer]
          .contains(timeRangesForOptionalAttendees[optionalAttendeesPointer])) {
        timeRangesForMandatoryOptionalAttendees.add(timeRangesForOptionalAttendees[optionalAttendeesPointer]);
        if (optionalAttendeesPointer + 1 < timeRangesForOptionalAttendees.length) {
          optionalAttendeesPointer++;
        } else {
          return;
        }
      }

      // At this point, we have two potential scenarios:
      // 1. |----mandatory attendees {@code TimeRange}----|
      //                       |----optional attendees {@code TimeRange}-----|
      //   Overlap:            |--------------------------|
      // 2. |----optional attendees {@code TimeRange}-----|
      //                       |----mandatory attendees {@code TimeRange}----|
      //   Overlap:            |--------------------------|
      int overlappingTimeRangeStart = Math.max(
          timeRangesForMandatoryAttendees[mandatoryAttendeesPointer].start(),
          timeRangesForOptionalAttendees[optionalAttendeesPointer].start());
      int overlappingTimeRangeEnd = Math.min(
          timeRangesForMandatoryAttendees[mandatoryAttendeesPointer].end(),
          timeRangesForOptionalAttendees[optionalAttendeesPointer].end());
      checkDurationAndAddAvailableTimeRange(requestedDuration,
                                            overlappingTimeRangeStart, overlappingTimeRangeEnd,
                                            timeRangesForMandatoryOptionalAttendees);
      if (timeRangesForMandatoryAttendees[mandatoryAttendeesPointer].start() <
          timeRangesForOptionalAttendees[optionalAttendeesPointer].start()) {
        // Scenario 1: move on to the next mandatory attendees' available {@code TimeRange}.
        if (mandatoryAttendeesPointer + 1 < timeRangesForMandatoryAttendees.length) {
          mandatoryAttendeesPointer++;
        } else {
          return;
        }
      } else {
        // Scenario 2: move on to the next optional attendees' available {@code TimeRange}.
        if (optionalAttendeesPointer + 1 < timeRangesForOptionalAttendees.length) {
          optionalAttendeesPointer++;
        } else {
          return;
        }
      }
    }
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
