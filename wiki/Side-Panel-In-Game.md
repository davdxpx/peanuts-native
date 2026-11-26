# The in-game panel

<!-- PEANUTS begin: the in-game menus (InGamePanel): a game menu at the left, a quick menu at the right -->
Two menus slide in over a running game, with the game going on behind them. The **game menu** at the left has everything about this session: Back to the game, Pause, Back up the save, Button mapping, Share log and Stop. The **quick menu** at the right holds the settings that can change while you play, in tabs on a rail at its left. Almost every change applies at once and is kept for the next start. The only exceptions are marked "next start".
<!-- PEANUTS end -->

<!-- TODO screenshot: the in-game panel open over a game, Performance tab -->

## Open and close it

<!-- PEANUTS begin: the in-game menus (InGamePanel) -->
| Action | Opens | Closes |
|---|---|---|
| Swipe in from the left edge | the game menu | |
| Swipe in from the right edge | the quick menu | |
| The phone's Back (button or edge swipe) | the game menu | yes (in the quick menu: one step back) |
| A long press on the on-screen controls' show / hide button (top centre) | the quick menu | |
| A controller: Home pressed, or hold - and + for half a second (Settings > Controller: in-game panel changes this) | the game menu | yes |
| A controller: Home held for half a second | the quick menu | |
| A controller's own Back (the AYN Thor, whose Home is Android's): pressed / held | the game menu / the quick menu | yes |
| The game menu's **Quick menu** row | the quick menu | |
| Your own **Quick menu button** (Settings > Controls > Controller), if you set one | the quick menu | yes |
| A tap beside a menu, or the controller's B | | yes (B in a quick menu opened from the game menu goes back there) |

While Preparing shaders shows, a shader preparation runs or the controller screen asks who plays, Back does what it did before the menus: it cancels that.

A hint at the right edge shows how, once after install. With a controller: D-pad or left stick moves the selection, A presses, B goes back. In the quick menu, up and down on the rail switch the tab, right goes into the tab's rows and left comes back to the rail; left and right step a slider or a row of choices; a yellow frame marks the focused row.

## The game menu

The game's cover, name, version and how long this session has run, then:
- **Back to the game**.
- **Pause the game**: freezes the game until you tap the screen (or press A or Start on a gamepad). While paused, the game's clock, graphics, threads and audio are suspended.
- **Quick menu**: the quick menu, with B back to this row.
- **Back up the save**: copies the save the game runs on to where Back up saves puts its copies, while the game runs; the row shows the newest backup ("Today 14:02"). If the game saves during the copy, the copy is tried again; the save itself is only read.
- **Button mapping**: the game's mapping (it applies at the next start).
- **Share log**: this run's log so far, or the game's build log (you choose).
- **Stop the game**, set apart at the bottom: asks once (Keep playing / Stop); unsaved progress is lost, and the log stays for **Share log**. A game that has not saved keeps its progress only in memory, also while paused or in the background, so stopping it then loses it.
<!-- PEANUTS end -->

## The quick menu's tabs <!-- PEANUTS: on a rail; Cheats and Mods each a tab when the game has some -->

### Performance
- **Frame rate cap**: the same choices as on the game page ([Settings: Performance](Settings-Performance#frame-rate-cap)), applied now. A game's frame limit list is 60, 45, 40, 30, 20 fps; other games: The game's own, 60, 30, 20, 15 fps.
- **Full game speed at low frame rates** (games whose speed follows their frame rate) and **Hold 60 fps: never drop to 30, no frame holds** (Mario Kart World).
- **Upscaler** (and **FSR sharpness**): for a game whose upscaler the app replaces; applies from the next frame. Hidden for a game with Resolution presets unless Settings > Help > Tester settings is on.
- **Dynamic resolution**: a slider from Off to 30 % (Donkey Kong Bananza).
- **Frame generation**: the on switch, target, multiplier and motion estimation resolution ([Settings: Performance](Settings-Performance#frame-generation)).
- The game's own **live** switches, if its profile has any.

The live lines on this tab (the frame rate now) refresh twice a second while it shows.

### Display
- **Resolution**: the same choice as on the game page. For some games the window changes at once and the 3D picture at the next start; a line says which. Fire Emblem: Fortune's Weave shows three groups: Resolution, Upscaling quality, Graphics quality.
- **Picture**: **Fit** or **Stretch**, **Size** 50 to 100 % in 5 % steps (the picture shrinks with its shape kept and black around it), and **Position (this screen)**: Top, Centre, Bottom.
- **Overlay**: the master switch and one switch per item.

### Controls
- **Controllers > Player 1** (while a gamepad is connected).
- **On-screen controls**: Auto / On / Off, opacity, size, vibration on press, the log, the show / hide button, **Reset the controls**, **Edit the controls layout…**.
- **Rumble (the game's vibration)**.
- **Motion sensitivity**: a slider, Slower to Faster.
- **Next start**: the game's own switches that need a restart. <!-- PEANUTS: Button mapping is the game menu's -->

See [Settings: Controls](Settings-Controls).

<!-- PEANUTS begin: Cheats and Mods tabs; the menu's own settings at the rail's bottom -->
### Cheats and Mods
Only for a game that has some, and while Settings > App > Mods tab is on: **Cheats**, one switch per cheat, grouped by section, live (**All off** sits in the tab's header), and **Mods**. See [Cheats](Cheats).

### General (the menu's own settings, at the bottom of the rail)
- **Menu**: **Size** and **Text size**, each Small / Normal / Large, for every game, applied at once; **Vibrate on each step** (on) and **Menu sounds** (off).
- **This game**: **Pause when the game comes back from the background** (on by default): coming back from another app shows the game paused until a tap.
- **Layout** <!-- PEANUTS: InGamePanel.layoutSetting -->: **Two menus** (the default), **One menu at the right** (Game first on its rail, then the quick menu's tabs), or, on a device with a second screen such as the AYN Thor, **Bottom screen** (the one menu on the bottom screen, the game in full view on top). With one menu, Home and Back open it on Game (the left edge is the game's); Home held, or a swipe in from the right edge, on the tab shown last; L and R step over Game and the tabs. On the bottom screen a swipe up from its bottom edge (a faint grip shows where) opens it too. The first start after the update asks once on Home, with a picture of each layout.

Pause, Stop and Back to the game are the game menu's.
<!-- PEANUTS end -->

## Notes

- The panel's changes are the same settings as the game page's: a change in one shows in the other.
- If a game was started without live control (a test setting), the rows say the change applies at the next start. A normal start has it.

## Sources

`SidePanel.java`, `PadPanel.java`, `TouchControls.java`, `PanelLook.java`, `Overlay.java`, `Lsfg.java`, `GameUpscaler.java`, `android/README.md` (In-game panel, Pause). <!-- PEANUTS: `InGamePanel.java`, `dev/peanuts/ingame/` -->
