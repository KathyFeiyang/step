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
    // Convert comment object to JSON format.
    Gson gson = new Gson();
    String json = gson.toJson(comment);

    // Send the resultant JSON as the sevlet response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Obtain user input from submitted form.
    String message = request.getParameter("message");
    String name = request.getParameter("message-sender-name");
    String email = request.getParameter("message-sender-email");

    // Pack user input into an object.
    comment = new FeedbackRecord(message, name, email);

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

  @Override
  public String toString() {
    return String.format("FeedbackRecord:\nMessage=%s\nName=%s\nEmail=%s\n",
                         this.message, this.name, this.email);
  }
}
