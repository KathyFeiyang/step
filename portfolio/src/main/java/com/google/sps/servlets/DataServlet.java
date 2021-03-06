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

import com.google.appengine.api.blobstore.BlobInfo;
import com.google.appengine.api.blobstore.BlobInfoFactory;
import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ImagesServiceFailureException;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.Math;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.owasp.encoder.Encode;

@WebServlet("/data")
public class DataServlet extends HttpServlet {
  public static final String REDIRECT_URL = "/index.html#contact_me";
  private static final List<String> SUPPORTED_IMAGE_FORMATS =
      Arrays.asList("apng", "bmp", "gif", "ico", "cur", "jpg", "jpeg", "jfif", "pjpeg", "pjp",
      "png", "svg", "tif", "tiff", "webp");
  private static final String IMAGE_UPLOAD_NOT_SUPPORTED_DEPLOYED = "notSupportedOnDeployedServer";
  private static final int DEFAULT_MAX_COMMENTS = 10;
  private UserComment comment;
  private int currentPageId = 1;
  private InvalidInputFlags invalidInputFlags = new InvalidInputFlags();

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    UserService userService = UserServiceFactory.getUserService();

    // Check if the user is logged-in; if not, the user must first login.
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(REDIRECT_URL);
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
      String name = (String) commentEntity.getProperty("name");
      String email = (String) commentEntity.getProperty("email");
      String petPreference = (String) commentEntity.getProperty("petPreference");
      UserInfo userInfo = new UserInfo(userId, name, email, petPreference);
      String placeQueryName = (String) commentEntity.getProperty("placeQueryName");
      String commentContent = (String) commentEntity.getProperty("commentContent");
      String imageUrl = (String) commentEntity.getProperty("imageUrl");

      UserComment commentItem = new UserComment(userInfo, placeQueryName, commentContent,
                                                imageUrl);
      comments.add(commentItem);
    }
    return comments;
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    UserService userService = UserServiceFactory.getUserService();

    // Check if the user is logged-in; if not, the user must first login.
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect(REDIRECT_URL);
      return;
    }

    // Obtain user input from submitted form and timestamp.
    String userId = userService.getCurrentUser().getUserId();
    String name = request.getParameter("comment-sender-name");
    String email = userService.getCurrentUser().getEmail();
    String petPreference = request.getParameter("user-pet-preference");
    UserInfo userInfo = new UserInfo(userId, name, email, petPreference);
    String placeQueryName = request.getParameter("place-query-name");
    String commentContent = request.getParameter("comment-content");
    // Get the URL of the image that the user uploaded to Blobstore.
    String imageUrl = getUploadedFileUrl(request, "comment-image");
    // Refrain from adding to the database if the user input is a potentially XSS attack.
    // Users select petPreference from a set of predefined options, so petPreference is safe.
    if (!name.equals(Encode.forHtml(name)) ||
        !placeQueryName.equals(Encode.forHtml(placeQueryName)) ||
        !commentContent.equals(Encode.forHtml(commentContent))) {
      this.invalidInputFlags.setIsLatestInputDangerous();
      response.sendRedirect(REDIRECT_URL);
      return;
    }
    long timestamp = System.currentTimeMillis();

    // Pack user input into an object.
    comment = new UserComment(userInfo, placeQueryName, commentContent, imageUrl);

    // Create corresponding Datastore entity.
    Entity commentEntity = new Entity("UserComment");
    commentEntity.setProperty("userId", userInfo.getUserId());
    commentEntity.setProperty("name", userInfo.getName());
    commentEntity.setProperty("email", userInfo.getEmail());
    commentEntity.setProperty("petPreference", userInfo.getPetPreference());
    commentEntity.setProperty("placeQueryName", comment.getPlaceQueryName());
    commentEntity.setProperty("commentContent", comment.getCommentContent());
    commentEntity.setProperty("imageUrl", comment.getImageUrl());
    commentEntity.setProperty("timestamp", timestamp);

    // Store user comment entity into database.
    DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
    datastore.put(commentEntity);

    // Redirect back to the homepage's "Contact Me" section.
    response.sendRedirect(REDIRECT_URL);
  }

  /** 
   * Returns a URL that points to the uploaded file, or null if the user didn't upload a file.
   *
   * @param request the request sent to this servlet which contains all user submitted data.
   * @param formInputElementName the name of the file input element on the HTML submission form.
   * @return URL of the uploaded file hosted on Blobstore.
   */
  private String getUploadedFileUrl(HttpServletRequest request, String formInputElementName) {
    BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
    Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
    List<BlobKey> blobKeys = blobs.get(formInputElementName);

    // User submitted form without selecting a file, so we can't get a URL. (dev server)
    if (blobKeys == null || blobKeys.isEmpty()) {
      return null;
    }

    // Our form only contains a single file input, so get the first index.
    BlobKey blobKey = blobKeys.get(0);

    // User submitted form without selecting a file, so we can't get a URL. (live server)
    BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
    if (blobInfo.getSize() == 0) {
      blobstoreService.delete(blobKey);
      return null;
    }

    String filename = blobInfo.getFilename();
    if (!isValidImage(filename)) {
      return null;
    }

    // Use ImagesService to get a URL that points to the uploaded file.
    ImagesService imagesService = ImagesServiceFactory.getImagesService();
    ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);

    // To support running in Google Cloud Shell with AppEngine's devserver, we must use the relative
    // path to the image, rather than the path returned by imagesService which contains a host.
    try {
      URL url = new URL(imagesService.getServingUrl(options));
      return url.getPath();
    } catch (MalformedURLException e) {
      return imagesService.getServingUrl(options);
    } catch (ImagesServiceFailureException e) {
      return IMAGE_UPLOAD_NOT_SUPPORTED_DEPLOYED;
    }
  }

  /** 
   * Checks whether an image file belongs to the set of supported image formats.
   *
   * @param filename the file name of an image.
   * @return whether said image is of a supported format.
   */
  private boolean isValidImage(String filename) {
    String[] splitFilename = filename.split("\\.");
    String fileExtension = splitFilename[splitFilename.length - 1].toLowerCase();
    return SUPPORTED_IMAGE_FORMATS.contains(fileExtension);
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
  private UserInfo userInfo;
  private String placeQueryName;
  private String commentContent;
  private String imageUrl;

  public UserComment(UserInfo inputUserInfo, String inputPlaceQueryName,
      String inputCommentContent, String inputImageUrl) {
    this.userInfo = inputUserInfo;
    this.placeQueryName = inputPlaceQueryName;
    this.commentContent = inputCommentContent;
    this.imageUrl = inputImageUrl;
  }

  public UserInfo getUserInfo() {
    return this.userInfo;
  }

  public String getPlaceQueryName() {
    return this.placeQueryName;
  }

  public String getCommentContent() {
    return this.commentContent;
  }

  public String getImageUrl() {
    return this.imageUrl;
  }

  @Override
  public String toString() {
    return String.format("UserComment:\nUser Info=%s\nPlace Name=%s\nComment=%s\nImage URL=%s\n",
                         this.userInfo, this.placeQueryName, this.commentContent, this.imageUrl);
  }
}

class UserInfo {
  private String userId;
  private String name;
  private String email;
  private String petPreference;

  public UserInfo(String inputUserId, String inputName, String inputEmail,
      String inputPetPreference) {
    this.userId = inputUserId;
    this.name = inputName;
    this.email = inputEmail;
    this.petPreference = inputPetPreference;
  }

  public String getUserId() {
    return this.userId;
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
    return String.format("UserInfo:\nName=%s (ID=%s)\nEmail=%s\n[Loves %s!]\n",
                         this.name, this.userId, this.email, this.petPreference);
  }
}
