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
  private List<String> quotes;

  @Override
  public void init() {
    quotes = new ArrayList<>(4);
    quotes.add("Hope you are having a good one!");
    quotes.add("How are you :)");
    quotes.add("It is fantastic to see you!");
    quotes.add("Thanks for vising my portfolio!");
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // Convert quotes object to JSON format.
    Gson gson = new Gson();
    String json = gson.toJson(quotes);

    // Send the resultant JSON as the sevlet response.
    response.setContentType("application/json;");
    response.getWriter().println(json);
  }
}
