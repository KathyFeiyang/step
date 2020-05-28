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
 * Presents a receipt for getting user form feedback in pop-up window.
 */
function presentFeedbackReceipt() {
  // Obtain user input feedback content, name and email.
  const formElements = document.getElementById('feedback-form').elements;
  const userFeedback = formElements[0].value;
  const userName = formElements[1].value;
  const userEmail = formElements[2].value;

  // Construct feedback receipt.
  const receipt = `Dear ${userName},\nThank you for submitting a feedback!\n`
                  + `We have recorded the following:\n`
                  + `    *Message: "${userFeedback}"\n`
                  + `    *Contact Information: ${userEmail}\n`;

  // Present feedback receipt in a pop-up window.
  window.alert(receipt);
}

/**
 * Executes JavaScript code and returns execution results like a console.
 */
function executeConsoleCode() {
  // Obtain user input JavaScript code.
  const promptString = "Please enter your JavaScript code:\n";
  const jsCode = window.prompt(promptString);
  if (!jsCode) {
    window.alert("No code is received.");
    return;
  }

  // Execute JavaScript code; catch and return error if error occurs.
  let executionResult = null;
  try {
    executionResult = Function('"use strict"; return ' + jsCode)();
  }
  catch (err) {
    window.alert(`An error has occurred:\n`
                 + `> ${jsCode}\n`
                 + `< ${err}\n`);
    return;
  }

  // Present execution results in pop-up window.
  const consoleOutput = `CONSOLE\n`
                        + `> ${jsCode}\n`
                        + `< ${executionResult}\n`;
  window.alert(consoleOutput);
}
