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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FindMeetingQuery {

  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    Collection<TimeRange> availableTimeRanges = new ArrayList<>();
    Set<String> attendees = new HashSet<>(request.getAttendees());
    long duration = request.getDuration();

    if (Long.compare(duration, TimeRange.WHOLE_DAY.duration()) > 0) {
      // If the requested duration is longer than the {@code WHOLE_DAY}, there can't be any
      // {@code TimeRange} that satisfies the {@code request}.
      return Arrays.asList();
    } else if (events.isEmpty() || attendees.isEmpty()) {
      // If there are no existing {@code Event} or mandatory attendees, the {@code WHOLE_DAY}
      // satisfies the request.
      return Arrays.asList(TimeRange.WHOLE_DAY);
    }

    // Find existing {@code TimeRange} that correspond to {@code Event} having overlapping
    // attendees with the {@code request}.
    Collection<TimeRange> relevantTimeRanges = new HashSet<>();
    for (Event event : events) {
      // Make a shallow copy of the original set of attendees.
      Set<String> overlappingAttendees = new HashSet<>(event.getAttendees());
      overlappingAttendees.retainAll(attendees);
      if (!overlappingAttendees.isEmpty()) {
        relevantTimeRanges.add(event.getWhen());
      }
    }
    if (relevantTimeRanges.isEmpty()) {
      // If there are no existing {@code Event} that contain overlapping attendees with the meeting
      // request, the {@code WHOLE_DAY} satisfies the {@code request}.
      return Arrays.asList(TimeRange.WHOLE_DAY);
    }

    // Sort the relevant {@code TimeRange} based on their start and end times respectively.
    List<TimeRange> startOrderedTimeRanges = new ArrayList<>(relevantTimeRanges);
    List<TimeRange> endOrderedTimeRanges = new ArrayList<>(relevantTimeRanges);
    Collections.sort(startOrderedTimeRanges, TimeRange.ORDER_BY_START);
    Collections.sort(endOrderedTimeRanges, TimeRange.ORDER_BY_END);

    // Add the available {@code TimeRange} before the first existing {@code TimeRange}, if
    // said available {@code TimeRange} is long enough.
    TimeRange firstStartingTimeRange = startOrderedTimeRanges.get(0);
    if (Long.compare(duration, firstStartingTimeRange.start() - TimeRange.START_OF_DAY) <= 0) {
      availableTimeRanges.add(TimeRange.fromStartEnd(TimeRange.START_OF_DAY,
                                                     firstStartingTimeRange.start(),
                                                     false));
    }

    // Find available {@code TimeRange} by looking at the available time between one ending
    // {@code TimeRange} and the immediately following starting {@code TimeRange}.
    int availableTimeRangeStart;
    int availableTimeRangeEnd;
    int startOrderedPointer = 0;
    int endOrderedPointer = 0;
    while (startOrderedPointer < startOrderedTimeRanges.size() &&
        endOrderedPointer < endOrderedTimeRanges.size()) {
      TimeRange endingTimeRange = endOrderedTimeRanges.get(endOrderedPointer);
      TimeRange startingTimeRange = startOrderedTimeRanges.get(startOrderedPointer);

      // There is no available time after the current ending {@code TimeRange} and before the
      // immediately following starting {@code TimeRange}.
      if (endingTimeRange.overlaps(startingTimeRange)) {
        startOrderedPointer++;
        continue;
      }
      // There is another {@code TimeRange} that ends later than the current ending
      // {@code TimeRange} + before the immediately following, starting {@code TimeRange}.
      // (Since there exists a {@code TimeRange} starting later than the current ending
      //  {@code TimeRange}, there must be other {@code TimeRange} ending even later. Therefore,
      //  the {@code endOrderedPointer} is guaranteed to not go out of range.)
      TimeRange nextEndingTimeRange = endOrderedTimeRanges.get(endOrderedPointer + 1);
      if (nextEndingTimeRange.end() <= startingTimeRange.start()) {
        endOrderedPointer++;
        continue;
      }

      // At this point, we have:
      //    |----ending {@code TimeRange}----| available time |----starting {@code TimeRange}----|
      availableTimeRangeStart = endingTimeRange.end();
      availableTimeRangeEnd = startingTimeRange.start();
      int availableDuration = availableTimeRangeEnd - availableTimeRangeStart;
      if (Long.compare(duration, availableDuration) <= 0) {
        availableTimeRanges.add(
            TimeRange.fromStartDuration(availableTimeRangeStart, availableDuration)
        );
      }
      endOrderedPointer++;
    }

    // Add the available {@code TimeRange} that comes after the last existing {@code TimeRange},
    // if said available {@code TimeRange} is long enough.
    TimeRange lastEndingTimeRange = endOrderedTimeRanges.get(endOrderedTimeRanges.size() - 1);
    if (Long.compare(duration, TimeRange.END_OF_DAY - lastEndingTimeRange.end() + 1) <= 0) {
      availableTimeRanges.add(TimeRange.fromStartEnd(lastEndingTimeRange.end(),
                                                     TimeRange.END_OF_DAY,
                                                     true));
    }

    return availableTimeRanges;
  }
}
