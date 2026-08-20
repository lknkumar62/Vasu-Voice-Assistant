# VASU AI — Universal Device Control Assistant

Android voice assistant foundation for legitimate, user-granted device automation.

## Current status

The repository was empty at the start of implementation. This commit establishes the project specification and implementation contract before adding Android source code.

## Design principles

- Explicit user-granted permissions only.
- AccessibilityService is the single UI automation engine.
- Android public APIs are preferred for device controls.
- Restricted operations open the relevant Settings UI and explain the limitation.
- Plans are verified before success is spoken.
- Offline commands remain available without network access.
- Cloud reasoning is optional and only used when connectivity is available.
- Secure/password fields and app-enforced accessibility restrictions are respected.

## Planned implementation phases

1. Android build foundation and permission center.
2. Accessibility UI tree inspection, element lookup, click, scroll and text input.
3. Existing/centralized device-action layer.
4. Offline command routing and cloud plan execution.
5. Communication and notification controls.
6. Verification, logging, regression coverage and release build.
