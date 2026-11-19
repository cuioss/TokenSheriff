envelope_version=1
sender_type=plan
sender_id=plan-13-final-finalize
epic=api-sheriff-seams
kind=candidate-lesson
created=2026-11-19T20:38:31Z

Candidate lesson from plan-13-final-finalize (Q-Gate 3-outline, finding 1f0eea, resolution taken_into_account).

Finding: deliverable 3 said the getByTestId('jwt-debugger-container') wait in devui-navigation.js should use the test id that utils/constants.js declares. constants.js:49 declares JWT_DEBUGGER_CONTAINER as the CSS selector '[data-testid="jwt-debugger-container"]', not the bare id. Passing it to getByTestId would never match and goToTokenDebugger would time out.

Resolution: TASK-005 requires .locator with CONSTANTS.SELECTORS.JWT_DEBUGGER_CONTAINER, or a new bare-id constant, never getByTestId with the selector.
