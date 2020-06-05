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

/**
 * Adds a cyclic greeting to the page.
 */
let greetingIndex = 0;
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
 * Fetches and adds a history of comments, and the theoretical maximum and
 * default number of comments, the total number of pages, and the current
 * page ID, to the page.
 * Show a warning if the user input value of the number of comments to display
 * or page ID is invalid, or if the latest user input may be a XSS attack.
 */
async function addComments(pageId) {
  // Obtain user input of maximum number of comments to display.
  const maxCommentsToDisplay = document
      .getElementById('max-comments-to-display').value;

  // Fetch the comment history, in the specified length, and other metadata,
  // as JSON from the Java servlet.
  const response = await fetch(`/data?` +
      `maxCommentsToDisplay=${maxCommentsToDisplay}&pageId=${pageId}`);
  const commentDataJson = await response.json();
  const comments = commentDataJson.comments;
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
    const commentFormatted = helperFormatComment(comments[i]);
    const commentItem = document.createElement('li');
    commentItem.innerText = commentFormatted;
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

  // If the user input value of the number of comments to display or page ID
  // is invalid, or if the latest user form submission is potentially dangerous,
  // show a text warning.
  helperAddInvalidInputWarning('invalid-max-comments', invalidMaxComments,
                               'Invalid input: expected to be positive.\n');
  helperAddInvalidInputWarning('invalid-page-id', invalidPageId,
                               `Invalid input: expected to be in range` +
                               ` [1, ${totalPages}].\n`);
  helperAddInvalidInputWarning('is-latest-input-dangerous',
                               isLatestInputDangerous,
                               'Your submission was considered to be a' + 
                               ' potential XSS attack.\n' +
                               'It would not be stored. Please try again.' +
                               ' Thank you!');
}

/**
 * Helper function to construct a formatted String of a comment.
 */
function helperFormatComment(commentJson) {
  return `${commentJson.name} (${commentJson.email}):\n` +
         `--> says "${commentJson.message}"\n` +
         `--> loves ${commentJson.petPreference}!\n`;
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
  // Obtain user input comment content, name and email.
  const formElements = document.getElementById('comment-form').elements;
  const userComment = formElements[0].value;
  const userName = formElements[1].value;
  const userEmail = formElements[2].value;

  // Construct user comment receipt.
  const receipt = `Dear ${userName},\nThank you for submitting feedback!\n` +
                  `We have recorded the following:\n` +
                  `    *Message: "${userComment}"\n` +
                  `    *Contact Information: ${userEmail}\n`;

  // Present comment receipt in a pop-up window.
  window.alert(receipt);
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
