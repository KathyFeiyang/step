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

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Query comment history from Datastore.
    Query commentHistoryQuery = new Query("UserComment").addSort("timestamp", SortDirection.DESCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery commentHistory = datastore.prepare(commentHistoryQuery);
    int totalComments = commentHistory.countEntities(FetchOptions.Builder.withDefaults());

    // Obtain maximum number of comments to display. If the number is invalid, take on the default value.
    int maxCommentsToDisplay;
    try {
      maxCommentsToDisplay = Integer.parseInt(request.getParameter("maxCommentsToDisplay"));
      if (maxCommentsToDisplay < 0) {
        maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
      }
    } catch (NumberFormatException e) {
      maxCommentsToDisplay = DEFAULT_MAX_COMMENTS;
    }

    // Convert comment Datastore entity into UserComment objects.
    List<UserComment> comments = new ArrayList<>(maxCommentsToDisplay);
    int commentCounter = 0;
    for (Entity commentEntity : commentHistory.asIterable()) {
      if (commentCounter == maxCommentsToDisplay) {
        break;
      }
      String message = (String) commentEntity.getProperty("message");
      String name = (String) commentEntity.getProperty("name");
      String email = (String) commentEntity.getProperty("email");

      UserComment commentItem = new UserComment(message, name, email);
      comments.add(commentItem);
      commentCounter++;
    }

    // Convert a history of comment objects, the total number of comments and default number, to JSON format.
    commentDataPackage commentData = new commentDataPackage(comments, totalComments, DEFAULT_MAX_COMMENTS);
    Gson gson = new Gson();
    String commentDataJSON = gson.toJson(commentData);

    // Send the resultant JSON as the sevlet response.
    response.setContentType("application/json;");
    response.getWriter().println(commentDataJSON);
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
    private int defaultComments;

    public commentDataPackage(List<UserComment> inputComments, int inputTotalComments,
                              int inputDefaultComments) {
      this.comments = inputComments;
      this.totalComments = inputTotalComments;
      this.defaultComments = inputDefaultComments;
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
