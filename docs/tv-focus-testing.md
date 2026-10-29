# Testing D-pad focus

TV focus tests are easy to get wrong in ways that pass locally on one control and fail on the next. This doc explains why and how to write them. Checked against Compose UI / foundation / ui-test 1.12.1, Material 3 1.4.0 and tv-material 1.1.0 (Compose BOM 2026.09.00).

## The rule

Before asserting or relying on D-pad focus in an instrumented test, switch the test to keyboard input mode:

```kotlin
lateinit var inputModeManager: InputModeManager
composeRule.setContent {
    inputModeManager = LocalInputModeManager.current
    // content
}
composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
```

`SleepTimerCountdownTest`, `TvTimerEditorHubFocusTest` and `TvServiceTimerEditorFocusTest` do this. `requestInputMode` may not succeed (its KDoc says so), hence the assertion.

Once the Compose BOM ships ui-test 1.13 or later, use the documented config instead and drop the boilerplate:

```kotlin
@get:Rule
val composeRule = createComposeRule(config = ComposeUiTestConfig(inputMode = InputMode.Keyboard))
```

## Why

1. **Compose tests start in the device's input mode, usually touch.** Compose's `InputMode` follows `View.isInTouchMode`, and a freshly booted emulator is in touch mode. [Test focus navigation](https://developer.android.com/develop/ui/compose/touch-input/focus/testing-focus) and [Migrate to v2 testing APIs: default input mode](https://developer.android.com/develop/ui/compose/testing/migrate-v2#default-input-mode) say the v2 rules force `InputMode.Touch` at the start of each test, but ui-test 1.12.1 has no such code (checked in its bytecode); the reset comes with `ComposeUiTestConfig` in 1.13.0-alpha01. Until then a test that requests `InputMode.Keyboard` leaves the device out of touch mode for the next tests in its shard, because below API 33 (CI runs API 30) touch mode is global, not per window. Compose cannot request `InputMode.Touch` (it only reports whether the view is already in touch mode), so a test that needs touch mode calls `enterTouchMode` from `testutil/TouchMode.kt`, which injects a real tap; `SetupAssistantScreenTest` does.

2. **Clickables cannot take focus in touch mode.** `clickable`, `toggleable` and everything built on them use `Focusability.SystemDefined`. Its KDoc: "This should be used for clickable components such as buttons and checkboxes: these components should only gain focus when they are used with certain types of input devices, such as keyboard / d-pad." In the source it is focusable only when `inputMode != InputMode.Touch`. That covers Material 3 buttons and the clickable `Surface` (built on `Modifier.clickable`) and our `EditSwitchRow` (built on `toggleable`, whose node extends the clickable node). This matches the platform rule for Views ([Touch mode](https://developer.android.com/develop/ui/views/touch-and-input/input-events)): in touch mode only views focusable in touch mode, such as text fields, take focus.

3. **The failure is silent.** `requestFocus()` on such a node returns `false`; nothing throws. The first symptom is a later `assertIsFocused()` reporting `Focused = 'false'`.

4. **Some controls focus anyway and hide the problem.** Text fields use `Focusability.Always` (KDoc: "such as text fields"), `Modifier.focusable()` defaults to `Always`, and clickable tv-material `Surface`s are built on `focusable()`, not `clickable` (`tvClickable`, so that disabled TV surfaces stay focusable). A test that only focuses these passes in touch mode and proves nothing about buttons or switches. #581's timer-editor test passed this way while focusing the Title text field.

5. **`performKeyInput` does not leave touch mode.** No doc says this; it is from the source. `performKeyInput` ends in `RootForTest.sendKeyEvent`, which `AndroidComposeView` hands straight to Compose's focus owner. The event never reaches the view system, where a real key press exits touch mode ("any time a user hits a directional key … the device will exit touch mode", [Touch mode](https://developer.android.com/develop/ui/views/touch-and-input/input-events)). Pressing D-pad keys in a test therefore does not make clickables focusable.

6. **Production is fine.** On a TV the first D-pad press leaves touch mode, and Compose's `InputMode` follows `View.isInTouchMode`. When a test fails only on point 2, fix the test, not the screen.

## Related traps

- **Initial focus can start in a state-based text field.** `EditOutlinedTextField(state:)` passes `showKeyboardOnFocus = touch || selected` (`rememberKeyboardOnSelect`): under touch input the keyboard opens on focus, under the D-pad it opens when the field is selected with OK / Enter (`DirectionCenter`, `Enter`, `NumPadEnter`), so focus travels through a form without the IME following it. The TV timer editor opens with focus in the Title field and the TV profile editor in the name field; `TvTimerEditorHubFocusTest` and `TvServiceTimerEditorFocusTest` cover the timer editor. There is no `value` / `onValueChange` overload: it would run on the legacy `BasicTextField`, which ignores `KeyboardOptions.showKeyboardOnFocus` (foundation KDoc), so it could not follow the same rule; the phone sleep timer uses the state form too.
- **A `FocusRequester` on a lazy item detaches when the item scrolls away.** `requestFocus()` then prints a warning and returns `false`, and a `focusProperties { right = … }` pointing at it makes the key do nothing (no geometric fallback). Put the requester on the lazy list or grid and use `Modifier.focusRestorer(fallback)`, as `HubServiceGrid` does.
