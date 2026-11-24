# amiibo

Games that read amiibo find one on Peanuts' virtual stand: a player places one of their own amiibo, or one of the app's list, and the game reads it as
it reads a figure on a console's NFC reader. GitHub issue #582.

## How a game sees it

The SDK's `nn::nfp` calls are taken over in the host (`dm/peanuts/peanuts_amiibo.c`, in place of `hle_sdk16.c`'s block in the Android host). The
take-over sits above the NFC service and above the figures' encryption: a game asks for plain structures and gets them from what the app hands over.

| Call | Answer |
|---|---|
| `ListDevices`, `GetNpadId` | one NFC device on the connected controller |
| `StartDetection` | the device searches (state 1); the app sees the search (`Bridge.amiiboStatus`) |
| an amiibo placed | state 2, the activate event signalled |
| `GetTagInfo` | the tag's UID (7 bytes), NFC-A, type 2 |
| `Mount` | state 4 |
| `GetModelInfo` | from the amiibo's ID: the character as the ID's bytes hold it, variant, type, model number, series |
| `GetCommonInfo` | last write, write count, area size 0xD8 |
| `GetRegisterInfo` | the player's Mii as the owner (the picture's Mii of the user the game started with), the character's name as nickname |
| `OpenApplicationArea`, `GetApplicationArea`, `SetApplicationArea`, `Flush` | this game's area, kept by Peanuts per access ID |
| `CreateApplicationArea`, `RecreateApplicationArea` | written at once |
| `Unmount` | state 2; a second later the tag leaves the stand on its own (state 3, the deactivate event) |
| `StopDetection` | state 0; a tag on the stand leaves it |
| amiibo settings applets | end at once: already registered, nothing to erase or restore |
| `nn::nfc::IsNfcEnabled` | on |

Each game gets an area of its own on an amiibo (its access ID), as Ryujinx keeps them: there is no "data of another game" step, and resetting one
game's data leaves the others'.

## What the app keeps

- `res/raw/amiibo_db.tsv` (peanuts source set): every amiibo of [AmiiboAPI](https://github.com/N3evin/AmiiboAPI) (MIT, Copyright (c) 2017 Nevin Vu)
  with its name, series and type, and what each does in which Switch game, offline. `peanuts/scripts/amiibo-db.py` makes it from a pinned commit.
- `files/amiibo/own/<ID>-<UID>/`: one of the player's amiibo. `tag.txt` (ID, UID, from a file or the list), `placed.tsv` (per game: how often, when
  last), and what the host writes: `area-<access ID>.bin` with the game's title ID beside it (`.title`) and `common.txt` (writes, last written).
- `files/amiibo/games.txt`: games seen initializing nfp, so their page shows the amiibo area though AmiiboAPI does not list them.
- `cacheDir/amiibo/<ID>.png`: pictures, fetched from AmiiboAPI's images only when a page shows the amiibo, and kept.

An imported `.bin` gives its ID (plain at 0x54) and its UID (the first pages). Everything else in a dump is encrypted and is not read: the game data a
figure carries from a console stays on the figure, and Peanuts keeps its own. No keys of any kind are asked for, read or shipped.

## The game page

A game that uses amiibo has an amiibo tile (after Controls). Its area shows the player's own amiibo that fit the game, then every amiibo that fits it,
searches all of them, and imports .bin files, folders and ZIP packs. An amiibo's page says what it does in this game and whether the game keeps data on
it, and resets that game's data (Reset game data) or removes the amiibo from the player's own.

## In game

The quick menu has an amiibo tab in a game that uses amiibo; its rail item lights up while the game looks for one. After the game has looked for 0.4 s,
once per search, a card slides in over the game (Suggest, the default): one of the player's own that fits, or the last placed in this game, with A Place
and B Not now; a short row of several; or Choose an amiibo, which opens the tab. B closes the card and the game goes on looking. "When a game looks for an
amiibo" in the tab also offers Only light up this tab and Place my last one at once. A placed amiibo comes with the show "Peanuts Native × amiibo" (about
2.3 s, the amiibo standing with its name for a good second; any button skips it), and the game gets the tag when the peanut cracks. Placed from the tab
while the game does not look, it is ready for the next search.

## Control

`adb shell am start -n <package>/dev.peanuts.ctl.Command -e cmd game -e do amiibo -e value <status | off | ID | own:<ID>-<UID>>` (CONTROL.md).
