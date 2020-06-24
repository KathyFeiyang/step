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

    Collection<String> allAttendees = new HashSet<>(attendees);
    allAttendees.addAll(optionalAttendees);
    Collection<TimeRange> allOccupiedTimeRanges =
        getConcernedAttendeesTimeRangesFromEvents(events, allAttendees);
    List<TimeRange> availableTimeRangesForAllAttendees =
        getAvailableTimeRanges(allOccupiedTimeRanges, requestedDuration);
    if (!availableTimeRangesForAllAttendees.isEmpty()) {
      return availableTimeRangesForAllAttendees;
    }

    if (!attendees.isEmpty()) {
      Collection<TimeRange> occupiedTimeRanges =
          getConcernedAttendeesTimeRangesFromEvents(events, attendees);
      return getAvailableTimeRanges(occupiedTimeRanges, requestedDuration);
    } else {
      Collection<TimeRange> optionalOccupiedTimeRanges =
          getConcernedAttendeesTimeRangesFromEvents(events, optionalAttendees);
      return getAvailableTimeRanges(optionalOccupiedTimeRanges, requestedDuration);
    }
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
  private List<TimeRange> getAvailableTimeRanges(Collection<TimeRange> occupiedTimeRanges,
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
      List<TimeRange> startOrderedTimeRanges,
      List<TimeRange> endOrderedTimeRanges,
      Collection<TimeRange> availableTimeRanges) {
    int startOrderedIndex = 0;
    int endOrderedIndex = 0;

    while (true) {

      while (endOrderedTimeRanges.get(endOrderedIndex)
          .overlaps(startOrderedTimeRanges.get(startOrderedIndex))) {
        // Make sure that the immediate time slot after the end of the ending
        // {@code TimeRange} is actually empty and not occupied by any other
        // {@code TimeRange}.
        if (endOrderedTimeRanges.get(endOrderedIndex).end() >=
            startOrderedTimeRanges.get(startOrderedIndex).end()) {
          if (startOrderedIndex + 1 < startOrderedTimeRanges.size()) {
            startOrderedIndex++;
          } else {
            return;
          }
        } else {
          endOrderedIndex = endOrderedTimeRanges.indexOf(
              startOrderedTimeRanges.get(startOrderedIndex));
          if (endOrderedIndex + 1 < endOrderedTimeRanges.size()) {
            endOrderedIndex++;
            continue;
          } else {
            return;
          }
        }
      }

      // Find the latest occupied {@code TimeRange} that occurs before the starting
      // {@code TimeRange}.
      // (Since there exists a {@code TimeRange} that occurs later than the current ending
      // {@code TimeRange}, we can be certain that {@code endOrderedIndex + 1} won't go out
      // of range.
      while (endOrderedTimeRanges.get(endOrderedIndex + 1).end() <=
          startOrderedTimeRanges.get(startOrderedIndex).start()) {
        endOrderedIndex++;
        if (!(endOrderedIndex + 1 < endOrderedTimeRanges.size())) {
          break;
        }
      }

      // At this point, we have:
      //    |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
      int availableTimeRangeStart = endOrderedTimeRanges.get(endOrderedIndex).end();
      int availableTimeRangeEnd = startOrderedTimeRanges.get(startOrderedIndex).start();
      checkDurationAndAddAvailableTimeRange(requestedDuration,
                                            availableTimeRangeStart, availableTimeRangeEnd,
                                            availableTimeRanges);

      if (startOrderedIndex + 1 < startOrderedTimeRanges.size() &&
          endOrderedIndex + 1 < endOrderedTimeRanges.size()) {
        startOrderedIndex++;
        endOrderedIndex++;
      } else {
        return;
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
