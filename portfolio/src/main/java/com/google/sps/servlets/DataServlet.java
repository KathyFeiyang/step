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
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
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
  private InvalidInputFlags invalidInputFlags = new InvalidInputFlags();

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    UserService userService = UserServiceFactory.getUserService();

    // Check if the user is logged-in; if not, the user must first login.
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect("/index.html#contact_me");
      return;
    }

    // Query comment history from Datastore.
    Query commentHistoryQuery = new Query("UserComment").addSort("timestamp",
                                                                 SortDirection.DESCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery commentHistory = datastore.prepare(commentHistoryQuery);
    List<Entity> commentHistoryList = commentHistory.asList(FetchOptions.Builder.withDefaults());
    int totalComments = commentHistoryList.size();

    // Obtain the maximum number of comments to display. If the number is invalid, take the default
    // value.
    String maxCommentsToDisplayStr = request.getParameter("maxCommentsToDisplay");
    int maxCommentsToDisplay = this.computeMaxCommentsToDisplay(maxCommentsToDisplayStr);

    // Obtain the page ID of comment history to display.
    String pageIdStr = request.getParameter("pageId");
    int totalPages = (int) Math.ceil(((double) totalComments) / maxCommentsToDisplay);
    this.computeAndSetCurrentPageId(pageIdStr, totalPages);

    // Obtain the desired comments according to the specified number of comments per page and
    // page ID.
    List<UserComment> comments = this.getSpecifiedComments(maxCommentsToDisplay, totalComments,
                                                           commentHistoryList);

    // Package a history of comment objects, the total number of comments, the default number of
    // comments, the total number of pages, the current page ID, and whether the latest user
    // input was dangerous and rejected, whether the user input values of number of comments to 
    // display & page ID are invalid, to JSON format.
    CommentDataPackage commentData;
    commentData = new CommentDataPackage(comments, totalComments, totalPages, this.currentPageId);
    Gson gson = new Gson();
    String commentDataJson = gson.toJson(commentData);
    this.invalidInputFlags.resetInvalidInputFlags();

    // Send the resultant JSON as the servlet response.
    response.setContentType("application/json;");
    response.getWriter().println(commentDataJson);
  }

  /**
  * Computes the maximum number of comments to display considering the possible range. Set the
  * invalid input flag if the original user input is invalid.
  *
  * @param maxCommentsToDisplayStr the raw user input of the maximum number of comments to display
  * @return the parsed and safe maximum comments to display
  */
  private int computeMaxCommentsToDisplay(String maxCommentsToDisplayStr) {
    int maxCommentsToDisplay;
    try {
      maxCommentsToDisplay = Integer.parseInt(maxCommentsToDisplayStr);
      if (maxCommentsToDisplay < 1) {
        maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
        this.invalidInputFlags.setInvalidMaxComments();
      }
    } catch (NumberFormatException e) {
      maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
      // Upon initialization, the field is empty; this special case doesn't indicate erroneous user
      // input.
      if (!maxCommentsToDisplayStr.isEmpty()) {
        this.invalidInputFlags.setInvalidMaxComments();
      }
    }
    return maxCommentsToDisplay;
  }

  /**
  * Computes and sets the current page ID considering the possible range. Set the invalid input
  * flag if the original user input is invalid.
  *
  * @param pageIdStr the raw user input of the page ID
  * @param totalPages the total number of pages in the database, given the number of comments per page
  */
  private void computeAndSetCurrentPageId(String pageIdStr, int totalPages) {
    try {
      this.currentPageId = Integer.parseInt(pageIdStr);
      // If the numeric input of page ID is in an invalid range, set the page ID to the closest
      // valid value, and set the invalid input flag.
      if (this.currentPageId < 1) {
        this.currentPageId = 1;
        this.invalidInputFlags.setInvalidPageId();
      } else if (this.currentPageId > totalPages) {
        if (totalPages != 0) {
          this.currentPageId = totalPages;
          this.invalidInputFlags.setInvalidPageId();
        } else {
          // If there are no comments in the database, set the page ID to 1.
          this.currentPageId = 1;
        }
      }
    } catch (NumberFormatException e) {
      switch (pageIdStr) {
        case "first":
          this.currentPageId = 1;
          break;
        case "last":
          // check and handle the special case of zero pages.
          this.currentPageId = Math.max(totalPages, 1);
          break;
        case "prev":
          this.currentPageId = Math.max(1, this.currentPageId - 1);
          break;
        case "next":
          // check and handle the special case of zero pages.
          this.currentPageId = Math.min(Math.max(totalPages, 1), this.currentPageId + 1);
          break;
        default:
          this.invalidInputFlags.setInvalidPageId();
      }
    }
  }

  /**
  * Collects the desired comments according to the specified number of comments per page and
  * page ID; converts Datastore Entities into UserComments.
  *
  * @param maxCommentsToDisplayStr the maximum number of comments to display
  * @param totalComments the total number of comments in the database
  * @param commentHistoryList the complete list of comment Entities in the database.
  * @return list of desired comments as UserComment objects.
  */
  private List<UserComment> getSpecifiedComments(int maxCommentsToDisplay, int totalComments,
      List<Entity> commentHistoryList) {
    List<UserComment> comments = new ArrayList<>(maxCommentsToDisplay);
    // Calculate the index of starting and ending comments.
    int startCommentIndex = (this.currentPageId - 1) * maxCommentsToDisplay;
    int endCommentIndex = Math.min(this.currentPageId * maxCommentsToDisplay, totalComments);

    for (int commentIndex = startCommentIndex; commentIndex < endCommentIndex; commentIndex++) {
      // Convert comment Datastore Entity object into UserComment objects.
      Entity commentEntity = commentHistoryList.get(commentIndex);
      String userId = (String) commentEntity.getProperty("userId");
      String message = (String) commentEntity.getProperty("message");
      String name = (String) commentEntity.getProperty("name");
      String email = (String) commentEntity.getProperty("email");
      String petPreference = (String) commentEntity.getProperty("petPreference");

      UserComment commentItem = new UserComment(userId, message, name, email, petPreference);
      comments.add(commentItem);
    }
    return comments;
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    UserService userService = UserServiceFactory.getUserService();

    // Check if the user is logged-in; if not, the user must first login.
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect("/index.html#contact_me");
      return;
    }

    // Obtain user input from submitted form and timestamp.
    String userId = userService.getCurrentUser().getUserId();
    String message = request.getParameter("message");
    String name = request.getParameter("message-sender-name");
    String email = userService.getCurrentUser().getEmail();
    String petPreference = request.getParameter("user-pet-preference");
    // Refrain from adding to the database if the user input is a potentially XSS attack.
    // Users select petPreference from a set of predefined options, so petPreference is safe.
    if (!message.equals(Encode.forHtml(message)) || !name.equals(Encode.forHtml(name))) {
      this.invalidInputFlags.setIsLatestInputDangerous();
      response.sendRedirect("/index.html#contact_me");
      return;
    }
    long timestamp = System.currentTimeMillis();

    // Pack user input into an object.
    comment = new UserComment(userId, message, name, email, petPreference);

    // Create corresponding Datastore entity.
    Entity commentEntity = new Entity("UserComment");
    commentEntity.setProperty("userId", comment.getUserId());
    commentEntity.setProperty("message", comment.getMessage());
    commentEntity.setProperty("name", comment.getName());
    commentEntity.setProperty("email", comment.getEmail());
    commentEntity.setProperty("petPreference", comment.getPetPreference());
    commentEntity.setProperty("timestamp", timestamp);

    // Store user comment entity into database.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(commentEntity);

    // Redirect back to the homepage's "Contact Me" section.
    response.sendRedirect("/index.html#contact_me");
  }

  private class CommentDataPackage {
    private List<UserComment> comments;
    private int totalComments;
    private int defaultMaxComments;
    private int totalPages;
    private int currentPageId;
    private InvalidInputFlags invalidInputFlags;

    CommentDataPackage(List<UserComment> inputComments, int inputTotalComments,
        int inputTotalPages, int inputCurrentPageId) {
      this.comments = inputComments;
      this.totalComments = inputTotalComments;
      this.defaultMaxComments = DataServlet.DEFAULT_MAX_COMMENTS;
      this.totalPages = inputTotalPages;
      this.currentPageId = inputCurrentPageId;
      this.invalidInputFlags = DataServlet.this.invalidInputFlags;
    }
  }

  private class InvalidInputFlags {
    private boolean invalidMaxComments = false;
    private boolean invalidPageId = false;
    private boolean isLatestInputDangerous = false;

    void setInvalidMaxComments() {
      this.invalidMaxComments = true;
    }

    void setInvalidPageId() {
      this.invalidPageId = true;
    }

    void setIsLatestInputDangerous() {
      this.isLatestInputDangerous = true;
    }

    void resetInvalidInputFlags() {
      this.invalidMaxComments = false;
      this.invalidPageId = false;
      this.isLatestInputDangerous = false;
    }
  }
}

class UserComment {
  private String userId;
  private String message;
  private String name;
  private String email;
  private String petPreference;

  public UserComment(String inputUserId, String inputMessage, String inputName, String inputEmail,
      String inputPetPreference) {
    this.userId = inputUserId;
    this.message = inputMessage;
    this.name = inputName;
    this.email = inputEmail;
    this.petPreference = inputPetPreference;
  }

  public String getUserId() {
    return this.userId;
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

  public String getPetPreference() {
    return this.petPreference;
  }

  @Override
  public String toString() {
    return String.format("UserComment:\nMessage=%s\nName=%s (ID=%s)\nEmail=%s\n[Loves %s!]\n",
                         this.message, this.name, this.userId, this.email, this.petPreference);
  }
}
