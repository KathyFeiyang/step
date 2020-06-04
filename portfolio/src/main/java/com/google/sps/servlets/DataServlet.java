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

@WebServlet("/data")
public class DataServlet extends HttpServlet {
  private UserComment comment;
  private static final int DEFAULT_MAX_COMMENTS = 10;
  private int currentPageId = 1;

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Query comment history from Datastore.
    Query commentHistoryQuery = new Query("UserComment").addSort("timestamp", SortDirection.DESCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery commentHistory = datastore.prepare(commentHistoryQuery);
    List<Entity> commentHistoryList = commentHistory.asList(FetchOptions.Builder.withDefaults());
    int totalComments = commentHistoryList.size();
    int totalPages;

    // Obtain the maximum number of comments to display. If the number is invalid, take the default value.
    int maxCommentsToDisplay;
    try {
      maxCommentsToDisplay = Integer.parseInt(request.getParameter("maxCommentsToDisplay"));
      if (maxCommentsToDisplay < 0) {
        maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
      }
    } catch (NumberFormatException e) {
      maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
    }

    // Obtain the page ID of comment history to display.
    String pageIdParam = request.getParameter("pageId");
    totalPages = (int) Math.ceil(((double) totalComments) / maxCommentsToDisplay);
    try {
      currentPageId = Integer.parseInt(pageIdParam);
      // If the numeric input of page ID is invalid, set to 1.
      currentPageId = Math.max(1, currentPageId);
    } catch (NumberFormatException e) {
      if (pageIdParam.equals("first")) {
        currentPageId = 1;
      } else if (pageIdParam.equals("last")) {
        currentPageId = totalPages;
      } else if (pageIdParam.equals("prev") && currentPageId > 1) {
        currentPageId--;
      } else if (pageIdParam.equals("next") && currentPageId < totalPages) {
        currentPageId++;
      }
    }

    // Convert comment Datastore entity into UserComment objects.
    List<UserComment> comments = new ArrayList<>(maxCommentsToDisplay);

    // Calculate the index of starting and ending comments.
    int startCommentIndex = (currentPageId - 1) * maxCommentsToDisplay;
    int endCommentIndex = currentPageId * maxCommentsToDisplay;
    // Check if any comments are within the valid index range; if so, proceed to collect comments.
    if (startCommentIndex < totalComments) {
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
    // comments, the total number of pages, and the current page ID, to JSON format.
    commentDataPackage commentData = new commentDataPackage(comments, totalComments, DEFAULT_MAX_COMMENTS,
                                                            totalPages, currentPageId);
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

    public commentDataPackage(List<UserComment> inputComments, int inputTotalComments,
                              int inputDefaultMaxComments, int inputTotalPages,
                              int inputCurrentPageId) {
      this.comments = inputComments;
      this.totalComments = inputTotalComments;
      this.defaultMaxComments = inputDefaultMaxComments;
      this.totalPages = inputTotalPages;
      this.currentPageId = inputCurrentPageId;
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
