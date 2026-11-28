# NARUTO X BORUTO Ultimate Ninja STORM CONNECTIONS, US release (`0100D2D0190A4000`)

<!-- PEANUTS -->
Update **1.70 (`v655360`)**, SDK 14.3.0: the US release, under its own title ID. CyberConnect2's NUCC engine on NVN with a Scaleform UI drawn with OpenGL ES through EGL, as in the EU release ([01005A20190A6000](../01005A20190A6000/README.md)), whose note covers the game, its keys, its shaders and the fixes it needed. Modules `main`, `subsdk0` and `sdk`. `main` (`48ece454…`) is the EU 1.70's build, and so are `subsdk0` and `sdk` (below).

## What it needs

The EU profile's keys as they are, with this release's title ID ([`game.tsv`](game.tsv)): the 5 GB layout with the 3.25 GB heap region, `mem_used 0x6000000` and `sys_resource 0x2000000`. The EU note's [Status](../01005A20190A6000/README.md#status) says where they come from. Like the EU release it needs the EGL and GL forwarding of #847 on Android.

## Files and shaders

- The same 1.70 files as the EU release; `shader_map.tsv`, `shader_pairs.tsv`, `shader_facts.tsv` and `shader_files.tsv` are copies of the EU profile's tables (570 shaders), as for the other releases that have a profile of their own.

## Builds

| Module | Build ID |
|---|---|
| `main` | `48ece454b61412b9fb46fab2be3f5ef7` |
| `subsdk0` | `c781dd63daf0ddcdb9975da50a198f93` |
| `sdk` | `2aef2cffb104ee7c9e212fd2f4c731e9` |

One build row, `v655360` (1.70). `main`'s build ID was read from this release's 1.70 update when it was imported, on a PC and on the phone. The `subsdk0` and `sdk` IDs are the EU row's: the phone prepared and ran this release's dump with this row and no build warning, and Prepare matches every module of the row (`DumpLayout`), so a dump whose `subsdk0` or `sdk` differed would have been refused.

## Testing

AYN Thor (Snapdragon 8 Gen 2, Adreno 740), Turnip 27.0.0-R2, base game v0 + update 1.70 imported and prepared in the app:

- The tutorial fight (Naruto against Kakashi): the UI and HUD over the 3D frame, 29.6 fps (`[vk] fps: 29.6 game frames a second`), no crash in about 3 minutes.

Not checked: the docked / handheld switch, long runs, Qualcomm's own driver (only Turnip was tested), and the `glTexSubImage3D` / `glCompressedTexSubImage3D` wrappers (the layer refuses them and returns `NULL`; the game did not call them in those 3 minutes).

## Found on this game

Nothing beyond the EU release: no host change was needed for this release.

## Open

- Everything open in the [EU note](../01005A20190A6000/README.md#open).
- The base game's own build IDs are not registered: only 1.70 plays.
