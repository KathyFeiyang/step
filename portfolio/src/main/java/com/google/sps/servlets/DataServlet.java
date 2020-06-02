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
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/data")
public class DataServlet extends HttpServlet {
  private FeedbackRecord comment;

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Query comment history from Datastore.
    Query commentHistoryQuery = new Query("FeedbackRecord").addSort("timestamp", SortDirection.DESCENDING);
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    PreparedQuery commentHistory = datastore.prepare(commentHistoryQuery);

    // Convert comment Datastore entity into FeedbackRecord objects.
    List<FeedbackRecord> comments = new ArrayList<>();
    for (Entity commentEntity : commentHistory.asIterable()) {
      String message = (String) commentEntity.getProperty("message");
      String name = (String) commentEntity.getProperty("name");
      String email = (String) commentEntity.getProperty("email");

      FeedbackRecord commentItem = new FeedbackRecord(message, name, email);
      comments.add(commentItem);
    }

    // Convert a history of comment objects to JSON format.
    Gson gson = new Gson();
    String json = gson.toJson(comments);

    // Send the resultant JSON as the sevlet response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Obtain user input from submitted form and timestamp.
    String message = request.getParameter("message");
    String name = request.getParameter("message-sender-name");
    String email = request.getParameter("message-sender-email");
    long timestamp = System.currentTimeMillis();

    // Pack user input into an object.
    comment = new FeedbackRecord(message, name, email);

    // Create corresponding Datastore entity.
    Entity feedbackEntity = new Entity("FeedbackRecord");
    feedbackEntity.setProperty("message", comment.getMessage());
    feedbackEntity.setProperty("name", comment.getName());
    feedbackEntity.setProperty("email", comment.getEmail());
    feedbackEntity.setProperty("timestamp", timestamp);

    // Store feedback entity into database.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(feedbackEntity);

    // Redirect back to the homepage's "Contact Me" section.
    response.sendRedirect("/index.html#contact_me");
  }
}

class FeedbackRecord {
  private String message;
  private String name;
  private String email;

  public FeedbackRecord(String inputMessage, String inputName, String inputEmail) {
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
    return String.format("FeedbackRecord:\nMessage=%s\nName=%s\nEmail=%s\n",
                         this.message, this.name, this.email);
  }
}
