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

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.io.IOException;
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
      String userEmail = userService.getCurrentUser().getEmail();
      String urlToRedirectToAfterUserLogsOut = "/index.html#contact_me";
      String logoutUrl = userService.createLogoutURL(urlToRedirectToAfterUserLogsOut);
      authenticationInfo = new AuthenticationInfo(true, logoutUrl, userEmail);
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
