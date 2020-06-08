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
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/authentication")
public class AuthenticationServlet extends HttpServlet {

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    AuthenticationInfo authenticationInfo;
    response.setContentType("application/json;");

    UserService userService = UserServiceFactory.getUserService();
    if (userService.isUserLoggedIn()) {
      String urlToRedirectToAfterUserLogsOut = "/index.html#contact_me";
      String logoutUrl = userService.createLogoutURL(urlToRedirectToAfterUserLogsOut);
      // Obtain the name to refer to the current user.
      String userName = this.getUserName(userService.getCurrentUser().getUserId());
      if (!userName.isEmpty()) {
        authenticationInfo = new AuthenticationInfo(true, logoutUrl, userName);
      } else {
        String userEmail = userService.getCurrentUser().getEmail();
        authenticationInfo = new AuthenticationInfo(true, logoutUrl, userEmail);
      }
    } else {
      String urlToRedirectToAfterUserLogsIn = "/index.html#contact_me";
      String loginUrl = userService.createLoginURL(urlToRedirectToAfterUserLogsIn);
      authenticationInfo = new AuthenticationInfo(false, loginUrl, "beautiful stranger");
    }
    // Convert user authentication information into JSON.
    Gson gson = new Gson();
    String authenticationInfoJson = gson.toJson(authenticationInfo);

    // Send the resultant JSON as the servlet response.
    response.getWriter().println(authenticationInfoJson);
  }

  /**
  * Finds the specified name of the user corresponding to the user ID. If the user submitted
  * multiple comments with different names, take the latest name; if the user has not submitted
  * a comment, return an empty String.
  *
  * @param userId the ID of the user for whom we want to find the name
  * @return the specified name of the user
  */
  private String getUserName(String userId) {
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    // Take the latest name.
    Query query = new Query("UserComment")
        .setFilter(new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId))
        .addSort("timestamp", SortDirection.DESCENDING);
    PreparedQuery results = datastore.prepare(query);
    List<Entity> latestCommentEntity = results.asList(FetchOptions.Builder.withLimit(1));
    // user has not submitted a comment
    if (latestCommentEntity.size() == 0) {
      return "";
    }
    return (String) latestCommentEntity.get(0).getProperty("name");
  }

  private class AuthenticationInfo {
    private boolean isUserLoggedIn;
    private String authenticationUrl;
    private String authenticationId;

    AuthenticationInfo(boolean inputIsUserLoggedIn, String inputAuthenticationUrl,
        String inputAuthenticationId) {
      this.isUserLoggedIn = inputIsUserLoggedIn;
      this.authenticationUrl = inputAuthenticationUrl;
      this.authenticationId = inputAuthenticationId;
    }
  }
}
