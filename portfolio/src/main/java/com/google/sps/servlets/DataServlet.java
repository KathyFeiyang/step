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

// Copyright (c) 2015 Jeff Ichnowski
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
//
//     * Redistributions of source code must retain the above
//       copyright notice, this list of conditions and the following
//       disclaimer.
//
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials
//       provided with the distribution.
//
//     * Neither the name of the OWASP nor the names of its
//       contributors may be used to endorse or promote products
//       derived from this software without specific prior written
//       permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
// HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
// OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.owasp.encoder.Encode;

@WebServlet("/data")
public class DataServlet extends HttpServlet {
  private UserComment comment;
  private static final int DEFAULT_MAX_COMMENTS = 10;
  private int currentPageId = 1;
  private boolean invalidMaxComments = false;
  private boolean invalidPageId = false;
  private boolean isLatestInputDangerous = false;

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Query comment history from Datastore.
    Query commentHistoryQuery = new Query("UserComment").addSort("timestamp", SortDirection.DESCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery commentHistory = datastore.prepare(commentHistoryQuery);
    List<Entity> commentHistoryList = commentHistory.asList(FetchOptions.Builder.withDefaults());
    int totalComments = commentHistoryList.size();

    // Obtain the maximum number of comments to display. If the number is invalid, take the default value.
    String maxCommentsToDisplayStr = request.getParameter("maxCommentsToDisplay");
    int maxCommentsToDisplay;
    try {
      maxCommentsToDisplay = Integer.parseInt(maxCommentsToDisplayStr);
      if (maxCommentsToDisplay < 0) {
        maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
        invalidMaxComments = true;
      }
    } catch (NumberFormatException e) {
      maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
      // Upon initialization, the field is empty; this special case doesn't indicate erroneous user input.
      if (!maxCommentsToDisplayStr.equals("")) {
        invalidMaxComments = true;
      }
    }

    // Obtain the page ID of comment history to display.
    String pageIdParam = request.getParameter("pageId");
    int totalPages = (int) Math.ceil(((double) totalComments) / maxCommentsToDisplay);
    try {
      currentPageId = Integer.parseInt(pageIdParam);
      // If the numeric input of page ID is invalid, set to 1.
      if (currentPageId < 1) {
        currentPageId = 1;
        invalidPageId = true;
      } else if (currentPageId > totalPages) {
        // If there are no comments, set the page ID to 1.
        if (totalPages == 0) {
          currentPageId = 1;
        } else {
          currentPageId = totalPages;
          invalidPageId = true;
        }
      }
    } catch (NumberFormatException e) {
      switch (pageIdParam) {
        case "first":
          currentPageId = 1;
          break;
        case "last":
          currentPageId = Math.max(totalPages, 1);
          break;
        case "prev":
          currentPageId = Math.max(1, currentPageId - 1);
          break;
        case "next":
          currentPageId = Math.min(Math.max(totalPages, 1), currentPageId + 1);
          break;
        default:
          invalidPageId = true;
      }
    }
    // In the special case where the number of comments to display is 0, page navigation
    // remains on the first page.
    if (maxCommentsToDisplay == 0) {
      totalPages = 1;
      currentPageId = 1;
    }

    // Convert comment Datastore entity into UserComment objects.
    List<UserComment> comments = new ArrayList<>(maxCommentsToDisplay);

    // Calculate the index of starting and ending comments.
    int startCommentIndex = (currentPageId - 1) * maxCommentsToDisplay;
    int endCommentIndex = currentPageId * maxCommentsToDisplay;
    // Check if any comments are within the valid index range; if so, proceed to collect comments.
    if (startCommentIndex >= 0 && startCommentIndex < totalComments) {
      endCommentIndex = Math.min(endCommentIndex, totalComments);
      for (int commentIndex = startCommentIndex; commentIndex < endCommentIndex; commentIndex++) {
        Entity commentEntity = commentHistoryList.get(commentIndex);
        String message = (String) commentEntity.getProperty("message");
        String name = (String) commentEntity.getProperty("name");
        String email = (String) commentEntity.getProperty("email");

        UserComment commentItem = new UserComment(message, name, email);
        comments.add(commentItem);
      }
    }

    // Package a history of comment objects, the total number of comments, the default number of
    // comments, the total number of pages, the current page ID, and whether the latest user
    // input was dangerous and rejected, whether the user input values of number of comments to 
    // display & page ID are invalid, to JSON format.
    commentDataPackage commentData = new commentDataPackage(comments, totalComments, DEFAULT_MAX_COMMENTS,
                                                            totalPages, currentPageId, invalidMaxComments,
                                                            invalidPageId, isLatestInputDangerous);
    invalidMaxComments = false;
    invalidPageId = false;
    isLatestInputDangerous = false;
    Gson gson = new Gson();
    String commentDataJson = gson.toJson(commentData);

    // Send the resultant JSON as the sevlet response.
    response.setContentType("application/json;");
    response.getWriter().println(commentDataJson);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Obtain user input from submitted form and timestamp.
    String message = request.getParameter("message");
    String name = request.getParameter("message-sender-name");
    String email = request.getParameter("message-sender-email");
    // Refrain from adding to the database if the user input is a potentially XSS attack.
    if (!message.equals(Encode.forHtml(message)) || !name.equals(Encode.forHtml(name)) ||
        !email.equals(Encode.forHtml(email))) {
      isLatestInputDangerous = true;
      response.sendRedirect("/index.html#contact_me");
      return;
    }
    long timestamp = System.currentTimeMillis();

    // Pack user input into an object.
    comment = new UserComment(message, name, email);

    // Create corresponding Datastore entity.
    Entity commentEntity = new Entity("UserComment");
    commentEntity.setProperty("message", comment.getMessage());
    commentEntity.setProperty("name", comment.getName());
    commentEntity.setProperty("email", comment.getEmail());
    commentEntity.setProperty("timestamp", timestamp);

    // Store user comment entity into database.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(commentEntity);

    // Redirect back to the homepage's "Contact Me" section.
    response.sendRedirect("/index.html#contact_me");
  }

  class commentDataPackage {
    private List<UserComment> comments;
    private int totalComments;
    private int defaultMaxComments;
    private int totalPages;
    private int currentPageId;
    private boolean invalidMaxComments;
    private boolean invalidPageId;
    private boolean isLatestInputDangerous;

    public commentDataPackage(List<UserComment> inputComments, int inputTotalComments,
                              int inputDefaultMaxComments, int inputTotalPages,
                              int inputCurrentPageId, boolean inputInvalidMaxComments,
                              boolean inputInvalidPageId, boolean inputIsLatestInputDangerous) {
      this.comments = inputComments;
      this.totalComments = inputTotalComments;
      this.defaultMaxComments = inputDefaultMaxComments;
      this.totalPages = inputTotalPages;
      this.currentPageId = inputCurrentPageId;
      this.invalidMaxComments = inputInvalidMaxComments;
      this.invalidPageId = inputInvalidPageId;
      this.isLatestInputDangerous = inputIsLatestInputDangerous;
    }
  }
}

class UserComment {
  private String message;
  private String name;
  private String email;

  public UserComment(String inputMessage, String inputName, String inputEmail) {
    this.message = inputMessage;
    this.name = inputName;
    this.email = inputEmail;
  }

  public String getMessage() {
    return this.message;
  }

  public String getName() {
    return this.name;
  }

  public String getEmail() {
    return this.email;
  }

  @Override
  public String toString() {
    return String.format("UserComment:\nMessage=%s\nName=%s\nEmail=%s\n",
                         this.message, this.name, this.email);
  }
}
