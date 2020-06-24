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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

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
   * -- Then finds the available {@code TimeRange} sandwiched between already-occupied
   * {@code TimeRange}, for mandatory and optional attendees.
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

    List<TimePoint> orderedTimePoints = getOrderedTimePointsFromTimeRanges(occupiedTimeRanges);
    List<TimeRange> availableTimeRanges = new LinkedList<>();

    // Potentially add the {@code TimeRange} before the first occupied {@code TimeRange}.
    checkDurationAndAddAvailableTimeRange(requestedDuration,
                                          TimeRange.START_OF_DAY,
                                          orderedTimePoints.get(0).getTime(),
                                          availableTimeRanges);
    findAndAddGapsInOccupiedTimeRanges(requestedDuration, orderedTimePoints, availableTimeRanges);
    // Potentially add the {@code TimeRange} after the last occupied {@code TimeRange}.
    checkDurationAndAddAvailableTimeRange(requestedDuration,
                                          orderedTimePoints.get(orderedTimePoints.size() - 1)
                                              .getTime(),
                                          TimeRange.END_OF_DAY + 1,
                                          availableTimeRanges);
    return availableTimeRanges;
  }

  /**
   * Finds the start and end {@code TimePoint} of {@code timeRanges}, and sorts them based on
   * ascending chronological order.
   */
  private List<TimePoint> getOrderedTimePointsFromTimeRanges(Collection<TimeRange> timeRanges) {
    List<TimePoint> timePoints = new ArrayList<>(2 * timeRanges.size());
    for (TimeRange timeRange : timeRanges) {
      timePoints.add(new TimePoint(timeRange.start(), true));
      timePoints.add(new TimePoint(timeRange.end(), false));
    }
    Collections.sort(timePoints, TimePoint.ORDER_BY_TIME);
    return timePoints;
  }

  /**
   * Finds available {@code TimeRange} by finding and skipping continuously occupied ranges of
   * time. Available time occurs in the gaps between continuously occupied ranges of time.
   *
   * @param requestedDuration The requested length of the available {@code TimeRange} to find.
   * @param orderedTimePoints A chronologically ordered list of {@code TimePoint}, that are either
   *    the start or end points of all occupied {@code TimeRange}. This list is expected to be
   *    non-empty and its size is an even number (since each {@code TimeRange} corresponds to
   *    exactly one start and one end {@code TimePoint}).
   * @param availableTimeRanges A list of available {@code TimeRange} that avoid the already
   *     occupied {@code TimeRange}.
   */
  private void findAndAddGapsInOccupiedTimeRanges(long requestedDuration,
      List<TimePoint> orderedTimePoints,
      Collection<TimeRange> availableTimeRanges) {
    int unendedTimeRange = 0;
    for (int index = 0; index < orderedTimePoints.size(); index++) {
      // Construct a continuously occupied range of time, which needs to be skipped. Such
      // continuously occupied range of time would contain an equal number of start and end
      // {@code TimePoint}. This occupied range may look like (TR stands for {@code TimeRange}):
      //    [start of TR1, start of TR2, end of TR1, start of TR3, end of TR3, end of TR2]
      // Available time occurs after the continuously occupied ranges of time.
      unendedTimeRange += orderedTimePoints.get(index).isTimeRangeStart() ? +1 : -1;
      if (unendedTimeRange == 0 && index + 1 < orderedTimePoints.size()) {
        int availableTimeRangeStart = orderedTimePoints.get(index).getTime();
        int availableTimeRangeEnd = orderedTimePoints.get(index + 1).getTime();
        checkDurationAndAddAvailableTimeRange(requestedDuration,
                                              availableTimeRangeStart, availableTimeRangeEnd,
                                              availableTimeRanges);
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
