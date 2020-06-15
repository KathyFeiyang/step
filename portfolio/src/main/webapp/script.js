//Copyright 2019 Google LLC
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

// use modern JavaScript (ES5)
"use strict"

let greetingIndex = 0;
let comments;
let enableCommentHistorySection = true;
let isUserLoggedIn = false;
let commentHistorySectionHTMLBackup = '';
let map;
let isMapLibrariesLoaded = false;
const mapInitialZoom = 12;
const APIKey = config.APIKey;
const IMAGE_UPLOAD_NOT_SUPPORTED_DEPLOYED = 'notSupportedOnDeployedServer';

/**
 * Adds a cyclic greeting to the page.
 */
function addCyclicGreeting() {
  const greetings =
      ['Hello world!', '¡Hola Mundo!', '你好，世界！', 'Bonjour le monde!',
       'Hallo Welt!'];

  // Pick the next greeting in a cycle.
  const greeting = greetings[greetingIndex];
  // Update greeting index.
  greetingIndex = (greetingIndex + 1) % greetings.length;

  // Add it to the page.
  const greetingContainer = document.getElementById('greeting-container');
  greetingContainer.innerText = greeting;
}

/**
 * Obtain user authentication status and the corresponding action; add
 * information to the page.
 */
async function getAuthentication() {
  // Obtain authentication information.
  const response = await fetch(`/authentication`);
  const authenticationJson = await response.json();
  isUserLoggedIn = authenticationJson.isUserLoggedIn;
  const authenticationUrl = authenticationJson.authenticationUrl;
  const userReference = authenticationJson.userReference;

  // Display authentication status and action on the page.
  const authenticationInstructionHTML =
      document.getElementById('authentication-instruction');
  const authenticationUrlHTML = document.getElementById('authentication-url');
  const userReferenceHTML = document.getElementById('user-reference');

  if (isUserLoggedIn) {
    authenticationInstructionHTML.innerText =
        "You can log out by clicking on the link below.";
    authenticationUrlHTML.innerText = "Log out here";
  } else {
    authenticationInstructionHTML.innerText =
        "Please log in below to submit comments and view the comment history.";
    authenticationUrlHTML.innerText = "Log in here";
  }
  authenticationUrlHTML.href = authenticationUrl;
  userReferenceHTML.innerText = userReference;
}

/**
 * Obtains and sets the URL to which comment form images should be submitted.
 */
async function getBlobstoreUploadUrl() {
  const response = await fetch('/blobstore-upload-url');
  const blobstoreUploadUrl = await response.text();
  document.getElementById('comment-form').action = blobstoreUploadUrl;
}

/**
 * Toggle the comment history section and either removing or restoring the
 * comments.
 */
async function toggleCommentHistorySection() {
  enableCommentHistorySection = !enableCommentHistorySection;
  addComments(1);
}

/**
 * A high-level function to implement comment-related features:
 * (Content includes (1) comment submission; (2) comment history)
 * 
 * -- Authenticates the current user.
 * -- Fetches and adds a history of comments, and the theoretical maximum and
 * default number of comments, the total number of pages, and the current
 * page ID, to the page.
 * -- Hides/backups and displays/restores comment submission/history sections as
 * necessary according to the disabling and enabling of the comment history
 * section, and whether the user is logged in.
 * -- Shows a warning if the user input value of the number of comments to
 * display or page ID is invalid, or if the latest user comment may be a XSS
 * attack.
 */
async function addComments(pageId) {
  await getAuthentication();

  // If the comment history section's content is fully prepared, we no longer
  // need to fetch from the backend database.
  checkCommentSubmissionSection();
  if (!checkCommentHistorySection()) {
    // Fetch and add comment history.
    addCommentHistory(pageId);
  }
}

/**
 * Checks whether the comment submission section should be enabled, and only
 * display the comment submission form if the user is logged in.
 */
function checkCommentSubmissionSection() {
  const commentSubmissionSection = document
      .getElementById('comment-submission-section');
  commentSubmissionSection.style.display = isUserLoggedIn ? 'initial' : 'none';
}

/**
 * Checks whether the comment history section should be enabled, and backup
 * or restore the comment history section as needed; returns whether the
 * comment history section content has been fully prepared (whether we no
 * longer need to fetch comment history from the backend database).
 */
function checkCommentHistorySection() {
  const commentHistorySection = document
      .getElementById('comment-history-section');

  // If the comment history section was disabled just now or if the user is
  // not logged in, backup the current comment history content and remove
  // that content from the page. Additionally, if the user is not logged in,
  // backup and remove the comment submission form.
  if (!enableCommentHistorySection || !isUserLoggedIn) {
    if (!commentHistorySectionHTMLBackup) {
      commentHistorySectionHTMLBackup = commentHistorySection.innerHTML;
      commentHistorySection.innerHTML = '';
    }
    return true;
  }
  // If the comment history section was enabled just now, restore the content
  // from backup and clear the backup content. Since the previous content is
  // restored, refrain from fetching the comment history.
  // (enableCommentHistorySection is true; isUserLoggedIn is also true.)
  if (commentHistorySectionHTMLBackup) {
    commentHistorySection.innerHTML = commentHistorySectionHTMLBackup;
    commentHistorySectionHTMLBackup = '';
    if (isMapLibrariesLoaded) {
      window.addPlacesToMap(comments);
    }
    return true;
  }
  return false;
}

/**
 * Fetches and adds a history of comments, and the theoretical maximum and
 * default number of comments, the total number of pages, and the current
 * page ID, to the page, as texts and as limits imposed on the input fields.
 * Shows a warning if the user input value of the number of comments to display
 * or page ID is invalid, or if the latest user comment may be a XSS attack.
 */
async function addCommentHistory(pageId) {
  // Obtain user input of maximum number of comments to display.
  const maxCommentsToDisplay = document
      .getElementById('max-comments-to-display').value;

  // Fetch the comment history, in the specified length, and other metadata,
  // as JSON from the Java servlet.
  const response = await fetch(`/data?` +
      `maxCommentsToDisplay=${maxCommentsToDisplay}&pageId=${pageId}`);
  // If the response is a redirection, go to the redirected destination URL.
  // This can happen when the user needs to log in before accessing the comment
  // section.
  if (response.redirected) {
    window.location.replace(response.url);
    return;
  }
  const commentDataJson = await response.json();
  comments = commentDataJson.comments;
  const totalComments = commentDataJson.totalComments;
  const defaultMaxComments = commentDataJson.defaultMaxComments;
  const totalPages = commentDataJson.totalPages;
  const currentPageId = commentDataJson.currentPageId;
  const invalidInputFlags = commentDataJson.invalidInputFlags;
  const invalidMaxComments = invalidInputFlags.invalidMaxComments;
  const invalidPageId = invalidInputFlags.invalidPageId;
  const isLatestInputDangerous = invalidInputFlags.isLatestInputDangerous;
  console.log(`CONFIRM: addComments() fetched ${comments.length} comments.\n`);

  // Format each comment as an item in a HTML list structure.
  const commentHistoryHTML = document.getElementById('comment-container');
  commentHistoryHTML.innerHTML = '';
  for (let i = 0; i < comments.length; i++) {
    const formattedComment = helperFormatComment(comments[i]);
    const commentItem = document.createElement('li');
    commentItem.innerText = formattedComment;
    commentHistoryHTML.appendChild(commentItem);
    commentHistoryHTML.appendChild(document.createElement('br'));
  }

  // Set the theoretical maximum and default for the number of comments to
  // display in the input field for number of comments per page.
  const maxCommentsToDisplayInputField = document
      .getElementById('max-comments-to-display');
  maxCommentsToDisplayInputField.setAttribute('max', totalComments);
  maxCommentsToDisplayInputField.setAttribute('value', defaultMaxComments);

  // Set the theoretical maximum and current page ID in the page ID input
  // field.
  const goToPageIdInputField = document.getElementById('go-to-page-id');
  goToPageIdInputField.setAttribute('max', totalPages);
  goToPageIdInputField.setAttribute('value', currentPageId);

  // Show the ID of the currently displayed page.
  document.getElementById('current-page-id').innerText = currentPageId;

  // Show the total number of pages.
  const totalPagesText = document.getElementById('total-pages');
  if (totalPages != 0) {
    totalPagesText.innerText = totalPages;
  } else {
    totalPagesText.innerText = 'empty comment history';
  }

  // If the user input value of the number of comments to display or page ID
  // is invalid, or if the latest user form submission is potentially dangerous,
  // show a text warning.
  helperAddInvalidInputWarning('invalid-max-comments', invalidMaxComments,
                               'Invalid input: expected to be positive.\n' +
                               'Now displaying a default maximum of' +
                               ` ${defaultMaxComments} comments.\n`);
  helperAddInvalidInputWarning('invalid-page-id', invalidPageId,
                               `Invalid input: expected to be in range` +
                               ` [1, ${totalPages}].\n`);
  helperAddInvalidInputWarning('is-latest-input-dangerous',
                               isLatestInputDangerous,
                               'Your submission was considered to be a' + 
                               ' potential XSS attack.\n' +
                               'It would not be stored. Please try again.' +
                               ' Thank you!');

  // Add comments and detailed information about the referenced places to the
  // map.
  if (isMapLibrariesLoaded) {
    window.addPlacesToMap(comments);
  }
}

/**
 * Helper function to construct a formatted String of a comment.
 */
function helperFormatComment(comment) {
  const userInfo = comment.userInfo;
  return `${userInfo.name} (${userInfo.email}):\n` +
         `--> comments on ${comment.placeQueryName}:` +
         ` "${comment.commentContent}"\n` +
         `--> loves ${userInfo.petPreference}!\n`;
}

/**
 * Add warning regarding input value range to page, if necessary.
 */
function helperAddInvalidInputWarning(elementId, shouldAddWarning,
    warningContent) {
  const warning = document.getElementById(elementId);
  if (shouldAddWarning) {
    warning.innerText = warningContent;
  } else {
    warning.innerText = '';
  }
}

/**
 * Deletes the complete comment history stored in the backend database.
 */
async function deleteCommentHistory() {
  // Make final confirmation with user about whether to delete the comment
  // history.
  const confirmed = window.confirm('Please click on "OK" to delete the' +
                                   ' comment history;' +
                                   ' otherwise please click on "Cancel".\n');
  if (!confirmed) {
    return;
  }

  // Send POST request to backend server to delete comment history.
  const POSTRequest = new Request('/delete-data', {method: 'POST'});
  await fetch(POSTRequest);

  // Fetch the now-empty comment history from the server.
  addComments(1);
}

/**
 * Presents a receipt for getting a user comment, in a pop-up window.
 */
function presentPopupCommentReceipt() {
  // Obtain user input comment content and name.
  const formElements = document.getElementById('comment-form').elements;
  const userPlaceName = formElements[0].value;
  const userComment = formElements[1].value;
  const userName = formElements[2].value;

  // Construct user comment receipt.
  const receipt = `Dear ${userName},\nThank you for submitting feedback!\n` +
                  `You entered the following:\n` +
                  `    *Place Name: ${userPlaceName}\n` +
                  `    *Comment: "${userComment}"\n`;

  // Present comment receipt in a pop-up window.
  window.alert(receipt);
}

/**
 * Asynchronously adds a Google map and add information regarding places
 * referenced in the comments through associated markers and information
 * windows.
 */
function addMap() {
  const mapScript = document.createElement('script');
  // Load Maps and Places API asynchronously.
  mapScript.src = 'https://maps.googleapis.com/maps/api/js?' +
               `key=${APIKey}&libraries=places&callback=addPlacesToMap`;
  mapScript.defer = true;
  mapScript.async = true;

  // Attach the callback function to the `window` object
  window.addPlacesToMap = function(comments) {
    isMapLibrariesLoaded = true;
    // Upon invoking the callback function, the Maps and Places libraries
    // are loaded.
    map = new google.maps.Map(document.getElementById('map'), {
        zoom: mapInitialZoom,
    });

    // Obtain information about the places referenced in the comments, and
    // add that information and the corresponding comments to the map.
    if (!comments) {
      return;
    }
    for (let i = 0; i < comments.length; i++) {
      addPlaceInfo(comments[i]);
    }
  };

  document.head.appendChild(mapScript);
}

/**
 * Queries a place's information and embeds said information in the map using
 * markers and information windows.
 */
function addPlaceInfo(comment) {
  let request = {
    query: comment.placeQueryName,
    fields: ['place_id', 'name', 'geometry', 'formatted_address',
              'rating', 'opening_hours'],
  };
  let service = new google.maps.places.PlacesService(map);
  // Query for information using Place Search.
  service.findPlaceFromQuery(request, function(results, status) {
    if (status === google.maps.places.PlacesServiceStatus.OK) {
      for (let i = 0; i < results.length; i++) {
        let place = results[i];
        let isOpenNow;
        let openingHours;
        const placeId = place.place_id;
        const detailsRequest = {
          placeId: placeId,
          fields: ['opening_hours', 'utc_offset_minutes'],
          // (utc_offset_minutes is required for opening_hours.isOpen()
          // to work.)
        };
        // Add a marker at the place's coordinate.
        const marker =
            new google.maps.Marker({title: place.name,
                                    position: place.geometry.location,
                                    map: map});

        // Query for additional information using Place Details.
        service.getDetails(detailsRequest,
            function(detailsResults, detailsStatus) {
          let formattedPlaceInfo;
          if (detailsStatus === google.maps.places.PlacesServiceStatus.OK) {
            // Obtaining operating hours information.
            if (detailsResults.opening_hours) {
              isOpenNow = detailsResults.opening_hours.isOpen();
              openingHours = detailsResults.opening_hours.weekday_text;
            }
            isOpenNow = isOpenNow ? isOpenNow : "Information not found."
            openingHours = openingHours ? openingHours : ["Information not found."];
            formattedPlaceInfo =
                helperFormatPlaceInfo(place.name, comment,
                                      place.formatted_address, place.rating,
                                      isOpenNow, openingHours);
          } else {
            formattedPlaceInfo =
                helperFormatPlaceInfo(place.name, comment,
                                      place.formatted_address, place.rating,
                                      `Uknown: "${detailsStatus}"`,
                                      [`Unknown: "${detailsStatus}"`]);
          }
          // Add an information window about the place.
          const infowindow = new google.maps.InfoWindow({
              content: formattedPlaceInfo,
          });
          marker.addListener('click', function() {
              infowindow.open(map, marker);
          });
        });
      }
      map.setCenter(results[0].geometry.location);
    }
  });
}

/**
 * Helper function to format a place's information and user's comment about the
 * place in HTML.
 */
function helperFormatPlaceInfo(name, comment, formattedAddress, rating,
    isOpenNow, openingHours) {
  const userInfo = comment.userInfo;
  let safeImageUrl = comment.imageUrl ? comment.imageUrl : "";
  let imageDescription = "User-shared image";
  if (safeImageUrl === IMAGE_UPLOAD_NOT_SUPPORTED_DEPLOYED) {
    safeImageUrl = "";
    imageDescription = "Image uploading is currently not supported.";
  }
  return '<div id="place-info-window">' +
           `<h3>${name}</h3>` +
           '<p><b>Visitors</b></p>' +
           `<p><em>${userInfo.name} (${userInfo.email})</em>
               comments on "${comment.placeQueryName}":<br>
               --> "${comment.commentContent}"</p>` +
           `<img src="${safeImageUrl}" alt="${imageDescription}">` +
           `<p><b>Address</b> ${formattedAddress}</p>` +
           `<p><b>Rating</b> ${rating}</p>` +
           `<p><b>Currently open?</b> ${isOpenNow}</p>` +
           `<p><b>Hours</b><br>${openingHours.join('<br>')}</p>`
         '</div>';
}

/**
 * Executes JavaScript code and returns execution results like a console.
 */
function executeConsoleCode() {
  // Obtain user input JavaScript code.
  const promptString = 'Please enter your JavaScript code:\n';
  const jsCode = window.prompt(promptString);
  if (!jsCode) {
    window.alert('No code is received.');
    return;
  }

  // Execute JavaScript code; catch and return error if error occurs.
  let executionResult = null;
  try {
    executionResult = Function('"use strict"; return ' + jsCode)();
  } catch (err) {
    window.alert(`An error has occurred:\n` +
                 `> ${jsCode}\n` +
                 `< ${err}\n`);
    return;
  }

  // Present execution results in a pop-up window.
  const consoleOutput = `CONSOLE\n` +
                        `> ${jsCode}\n` +
                        `< ${executionResult}\n`;
  window.alert(consoleOutput);
}

/**
 * Searches for and displays top 3, clickable Wikipedia results, matching
 * phrases and corresponding URLs, without reloading the page.
 */
function searchWikipedia() {
  helperSearchWikipedia()
    .then(wikipediaJson => {
      const wikipediaContainer = document.getElementById('wikipedia-container');
      wikipediaContainer.innerText = 'Top 3 Search Results:\n';

      // Display Wikipedia search phrase matches and corresponding URLs on the
      // page.
      for (let i = 0; i < wikipediaJson[1].length; i++) {
        const searchResult = document.createElement('a');
        const resultText = document.createTextNode(wikipediaJson[1][i]);
        searchResult.href = wikipediaJson[3][i];
        // Embed result phrase into URL anchor.
        searchResult.appendChild(resultText);
        // Add combined result of text and URL to the page.
        wikipediaContainer.appendChild(searchResult);
        wikipediaContainer.appendChild(document.createElement('br'));
      }
    });
}

/**
 * Helper function to fetch top 3 Wikipedia search results.
 */
async function helperSearchWikipedia() {
  // Obtain user input of search keyword.
  const searchKeyword = document.getElementById('search-keyword').value;
  const wikipediaURL = 'https://en.wikipedia.org/w/api.php?' +
                       'action=opensearch&limit=3&format=json' +
                       `&origin=*&search=${searchKeyword}`;

  // Fetch HTTP response for search result.
  const wikipediaResponse = await fetch(wikipediaURL, {mode: 'cors'});
  const wikipediaJson = await wikipediaResponse.json();
  return wikipediaJson;
}

/**
 * Adds event listeners to make avatar picture talk at mouse click.
 */
function addListenersForTalkingAvatar() {
  const talkingAvatar = document.getElementById('talking-avatar');

  // Talk at mouse down and up.
  talkingAvatar.addEventListener('mousedown', addAvatarGreeting);
  talkingAvatar.addEventListener('mouseup', clearAvatarGreeting);
}

/**
 * Adds avatar greeting to page and emphasis to avatar image.
 */
function addAvatarGreeting() {
  const avatarTalkBubble = document.getElementById('avatar-talk-bubble');
  const talkingAvatar = document.getElementById('talking-avatar');

  // Add greeting and set greeting style.
  avatarTalkBubble.innerText = 'Howdy! I hope to invite you to my island!';
  avatarTalkBubble.style.background = 'darkgray';
  avatarTalkBubble.style.fontWeight = 'bolder';
  avatarTalkBubble.style.textAlign = 'center';

  // Add emphasis to avatar image.
  talkingAvatar.style.border = 'dotted';
  talkingAvatar.style.opacity = 0.4;
}

/**
 * Removes avatar greeting and image emphasis.
 */
function clearAvatarGreeting() {
  const avatarTalkBubble = document.getElementById('avatar-talk-bubble');
  const talkingAvatar = document.getElementById('talking-avatar');

  // Reset greeting to image caption.
  avatarTalkBubble.innerText = 'Animal Crossing avatar picture';
  avatarTalkBubble.style.background = 'white';
  avatarTalkBubble.style.fontWeight = 'normal';
  avatarTalkBubble.style.textAlign = 'left';

  // Remove image emphasis.
  talkingAvatar.style.border = 'double';
  talkingAvatar.style.borderColor = 'darkgray';
  talkingAvatar.style.borderWidth = 'thick';
  talkingAvatar.style.opacity = 1.0;
}
