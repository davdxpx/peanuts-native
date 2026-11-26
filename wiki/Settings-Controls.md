# Settings: Controls

How you play: the on-screen controls, a controller, rumble, button mapping and motion aim. Global defaults are on **Settings > Controls**; per-game values are on the game page's **Game** tab (Controls section) or in the in-game quick menu's **Controls** tab ([The in-game panel](Side-Panel-In-Game)). <!-- PEANUTS: the in-game menus -->

<!-- TODO screenshot: Settings page, Controls card (On-screen controls, Rumble, Button mapping) -->

## On-screen controls

- **What it does:** the touch buttons over the game. **Auto** (default) draws nothing while a gamepad is connected, **On** always draws them, **Off** never. For every game.
- **When to toggle:** Off if the controls get in the way and you play with a controller.
- **Show / hide button** (a switch): the small button at the top centre that hides the on-screen controls. Default on. A long press on it opens the in-game quick menu. <!-- PEANUTS -->
- In the in-game quick menu (Controls tab): <!-- PEANUTS --> **Opacity**, **Size** (60 to 150 %), **Vibrate on press**, **Show the log over the game**, **Show / hide button (top centre)**, **Reset the controls**, **Edit the controls layout…** (drag a group to move it; a second finger resizes it, 50 to 200 %; the layout is kept per screen shape, so a foldable keeps one layout per screen) and **Position (this screen)** for the picture: Top, Centre, Bottom.

## Screen rotation
- **What it does:** **Landscape** (default) or **Automatic**, for the game screen.

## Controller: in-game panel
<!-- PEANUTS begin: the in-game menus (InGamePanel) -->
- **What it does:** how a controller opens the in-game menus: **Home, or hold - and + (half a second)** (default), **Home button only**, **Hold - and + only (half a second)**, or **Off**. Home pressed, or - and + held, opens the game menu; Home held for half a second opens the quick menu. On a device whose Home is Android's (the AYN Thor), the controller's own Back does the same: pressed the game menu, held the quick menu. In a menu the D-pad or left stick moves the selection, A presses, B goes back; in the quick menu up and down on its rail switch the tab.
- **What to expect:** while a menu is open no controller input reaches the game, and every button the game holds is released when it opens.
- **Quick menu button** (not set by default): a button of your own that opens the quick menu at once; tap the row, then press the button. A button games use (A, B, the shoulders, the D-pad, + and -) gets a warning first, since the game no longer gets it; a free one, such as an extra button on the back of the controller, is better.
<!-- PEANUTS end -->

## Players and controllers
Each connected gamepad is a player of its own. The in-game quick menu's **Controllers > Player 1** <!-- PEANUTS --> (shown while a gamepad is connected) picks which one is player 1, or **Automatic**; the choice is kept for every game.

## Rumble
- **What it does:** the game's vibration, on the controller or on the phone when there is no controller. Per game; Settings has the default.
- **When to toggle:** off if it is too much or drains the battery faster for you (battery effect not measured yet).
- **What to expect:** in the in-game quick menu it applies at once, including turning it on in a game that started with it off. <!-- PEANUTS -->
- **Default:** on.

## Button mapping
- **What it does:** one row per Switch button with the controller button that presses it. Tap a row, then press a button on the controller; it swaps with the one it replaces. **Save** stores it, **Reset to default** drops it. The on-screen controls are not changed.
- **When to toggle:** when your controller's layout differs from the game's expectation.
- **What to expect:** applies at the next start (the host reads it once). Settings has the default for every game; a game's own mapping wins.

## Xbox button label swap
- **What it does:** for Xbox-style layouts. **Off**, **Swap labels for X/Y and A/B** (the on-screen prompts are redrawn), or **Swap labels for X/Y, swap buttons A/B** (prompts for X/Y are redrawn and A and B are swapped by label).
- **Default:** Off. Per game with a **Default** choice that follows Settings.

## Motion sensitivity
- **What it does:** how far the phone's gyro turns the aim. Steps: 10, 20, 30, 40, 50, 60, 75, 100, 125, 150 %. 100 % is the real turn, as on a console. From the next start in Game settings; the in-game panel's slider applies at each step.
- **Default:** 50 %, because a held phone is heavier and less steady than a console.

## Sources

`MainActivity.java` (`buildSettings`), `GameSettings.java`, `TouchControls.java`, `PadPanel.java`, `PadMapUi.java`, `PadOrder.java`, `Prefs.java`, `android/README.md` (Controllers and players, In-game panel).
