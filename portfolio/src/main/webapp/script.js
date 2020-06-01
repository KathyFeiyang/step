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

// use modern JavaScript (ES5)
"use strict"

/**
 * Adds a cyclic greeting to the page.
 */
let greetingIndex = 0;
function addCyclicGreeting() {
  const greetings =
      ['Hello world!', '¡Hola Mundo!', '你好，世界！', 'Bonjour le monde!', 'Hallo Welt!'];

  // Pick the next greeting in a cycle.
  const greeting = greetings[greetingIndex];
  // Update greeting index.
  greetingIndex = (greetingIndex + 1) % greetings.length;

  // Add it to the page.
  const greetingContainer = document.getElementById('greeting-container');
  greetingContainer.innerText = greeting;
}

/**
 * Fetches and adds a quote to the page.
 */
async function addQuote() {
  const response = await fetch('/data');
  const quoteHTML = await response.text();
  document.getElementById('quote-container').innerHTML = quoteHTML;
}

/**
 * Presents a receipt for getting user form feedback in a pop-up window.
 */
function presentFeedbackReceipt() {
  // Obtain user input feedback content, name and email.
  const formElements = document.getElementById('feedback-form').elements;
  const userFeedback = formElements[0].value;
  const userName = formElements[1].value;
  const userEmail = formElements[2].value;

  // Construct feedback receipt.
  const receipt = `Dear ${userName},\nThank you for submitting feedback!\n` +
                  `We have recorded the following:\n` +
                  `    *Message: "${userFeedback}"\n` +
                  `    *Contact Information: ${userEmail}\n`;

  // Present feedback receipt in a pop-up window.
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
 * Searches for and displays top 3, clickable Wikipedia results, matching phrases and corresponding URLs,
 *   without reloading the page.
 */
function searchWikipedia() {
  helperSearchWikipedia()
    .then(wikipediaJSON => {
      const wikipediaContainer = document.getElementById('wikipedia-container');
      wikipediaContainer.innerText = 'Top 3 Search Results:\n';

      // Display Wikipedia search phrase matches and corresponding URLs on the page.
      for (let i = 0; i < wikipediaJSON[1].length; i++) {
        const searchResult = document.createElement('a');
        const resultText = document.createTextNode(wikipediaJSON[1][i]);
        searchResult.href = wikipediaJSON[3][i];
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
  const wikipediaURL = `https://en.wikipedia.org/w/api.php?action=opensearch&limit=3&format=json` +
                       `&origin=*&search=${searchKeyword}`;

  // Fetch HTTP response for search result.
  const wikipediaResponse = await fetch(wikipediaURL, {mode: 'cors'});
  const wikipediaJSON = wikipediaResponse.json();
  return wikipediaJSON;
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
