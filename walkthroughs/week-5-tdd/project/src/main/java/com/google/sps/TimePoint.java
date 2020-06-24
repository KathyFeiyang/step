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

import java.util.Comparator;

/**
 * Class representing either the start or end time point for a {@code TimeRange}.
 */
public class TimePoint {
  /**
    * A comparator for sorting {@TimePoint} based on the time they represent.
    */
  public static final Comparator<TimePoint> ORDER_BY_TIME = new Comparator<TimePoint>() {
    @Override
    public int compare(TimePoint a, TimePoint b) {
      return Integer.compare(a.time, b.time);
    }
  };
  private final int time;
  private final boolean isTimeRangeStart;

  public TimePoint(int inputTime, boolean inputIsTimeRangeStart) {
    this.time = inputTime;
    this.isTimeRangeStart = inputIsTimeRangeStart;
  }

  /**
    * Returns the time represented by the time point.
    */
  public int time() {
    return time;
  }

  /**
    * Returns whether this time point is the start of a {@code TimeRange}. A time point can
    * either be the start or the end of a {@code TimeRange}.
    */
  public boolean isTimeRangeStart() {
    return isTimeRangeStart;
  }
}
