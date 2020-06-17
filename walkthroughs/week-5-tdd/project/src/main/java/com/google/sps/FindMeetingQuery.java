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
  * Finds the available {@code TimeRange} for a set of attendees to satisfy a meeting
  * {@code request}, given some existing {@code events} and their respective attendees.
  * The resultant {@code TimeRange} do not overlap with one another and cover all possible
  * gaps between the existing {@code events}. These {@code TimeRange} have the same length as
  * or are longer than the requested meeting duration.
  * First extracts the existing {@code TimeRange} that correspond to {@code Event} having
  * overlapping attendees with the {@code request}; we only need to avoid conflicting with
  * those {@code TimeRange} that involve attendees contained in the {@code request}.
  * Then finds the available {@code TimeRange} sandwiched between an existing, occupied
  * {@code TimeRange} and the next {@code TimeRange}.
  *
  * @param events Existing events that each occupy some {@code TimeRange}.
  * @param request A meeting request for some mandatory and optional attendees and a duration.
  * @return All available {@code TimeRange} that satisfy the meeting request.
  */
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    Set<String> attendees = new HashSet<>(request.getAttendees());
    long requestedDuration = request.getDuration();

    if (Long.compare(requestedDuration, TimeRange.WHOLE_DAY.duration()) > 0) {
      return Arrays.asList();
    } else if (events.isEmpty() || attendees.isEmpty()) {
      return Arrays.asList(TimeRange.WHOLE_DAY);
    }

    Collection<TimeRange> occupiedTimeRanges =
        getConcernedAttendeesTimeRangesFromEvents(events, attendees);

    return getAvailabeTimeRanges(occupiedTimeRanges, requestedDuration);
  }

  /**
  * Finds the available {@code TimeRange} having at least a length of {@code requestedDuration},
  * given some already occupied {@code TimeRange}.
  * The resultant list of available {@code TimeRange} is sorted in ascending chronological order.
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
    while (true) {
      if (endingTimeRange.overlaps(startingTimeRange)) {
        if (startOrderedTimeRangesIterator.hasNext()) {
          startingTimeRange = startOrderedTimeRangesIterator.next();
        } else {
          break;
        }
        continue;
      }
      // There is another occupied {@code TimeRange} between the current ending {@code TimeRange}
      // and the next occupied {@code TimeRange}.
      // (Since there exists a {@code TimeRange} that occurs later than the current ending
      //  {@code TimeRange}, we can be certain that invoking .next() won't go out of range.)
      TimeRange nextEndingTimeRange = endOrderedTimeRangesIterator.next();
      if (nextEndingTimeRange.end() <= startingTimeRange.start()) {
        endingTimeRange = nextEndingTimeRange;
        continue;
      } else {
        endOrderedTimeRangesIterator.previous();
      }

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
        break;
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
  * Finds the existing {@code TimeRange} that correspond to {@code Event} having
  * overlapping attendees with a set of attendees that we are concerned with.
  */
  private Collection<TimeRange> getConcernedAttendeesTimeRangesFromEvents(
      Collection<Event> events,
      Set<String> attendees) {
    Collection<TimeRange> relevantTimeRanges = new HashSet<>();
    for (Event event : events) {
      Set<String> overlappingAttendees = new HashSet<>(event.getAttendees());
      overlappingAttendees.retainAll(attendees);
      if (!overlappingAttendees.isEmpty()) {
        relevantTimeRanges.add(event.getWhen());
      }
    }
    return relevantTimeRanges;
  }

  /**
  * Sorts a collection of {@code TimeRange} based on the specified {@code sortOrder}.
  */
  private List<TimeRange> getOrderedTimeRanges(Collection<TimeRange> timeRanges,
      Comparator<TimeRange> sortOrder) {
    List<TimeRange> orderedTimeRanges = new LinkedList<>(timeRanges);
    Collections.sort(orderedTimeRanges, sortOrder);
    return orderedTimeRanges;
  }

  /**
  * If a [start, end) represented {@code TimeRange} has a sufficient duration, adds it to the
  * set of available {@code TimeRange}.
  */
  private void checkDurationAndAddAvailableTimeRange(long requestedDuration, int start, int end,
      Collection<TimeRange> availableTimeRanges) {
    if (Long.compare(requestedDuration, end - start) <= 0) {
      availableTimeRanges.add(TimeRange.fromStartEnd(start, end, false));
    }
  }
}
