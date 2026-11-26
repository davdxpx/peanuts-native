#!/usr/bin/env python3
"""The in-game menus' layouts: the three pictures of the layout question (dev.peanuts.ui.LayoutChoice), android/app/src/peanuts/res/drawable/pil_layout_*.xml.

Each picture is a list of paths on a 240 x 168 grid: a screen with a running game (sky, hills, a path, a player) and the menus of that layout over it, in
the panel's own colours (sheet, rows, the peanut block, the yellow focus frame). Only path data is used, so the strings are VectorDrawable pathData as
they are.

  two     Two menus: the game menu at the left, the quick menu at the right
  right   One menu at the right: Game first on its rail
  bottom  Bottom screen: the game in full view on top, dimmed a little while the menu is open on the screen below

  python3 peanuts/scripts/layouts.py           writes the three pil_layout_<name>.xml
  python3 peanuts/scripts/layouts.py --check   writes nothing; exit 1 when a file differs from what this would write (peanuts/tests/test_layout_pictures.py)
"""
import pathlib
import sys

W, H = 240, 168
REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / 'android/app/src/peanuts/res/drawable'

# The panel's colours (dev.peanuts.ingame.Look, PanelPolish)
BODY, BEZEL = '#1B1C1F', '#0E0F11'
SHEET, RAIL, ROW, CHIP = '#202124', '#17181B', '#2D2E31', '#3A3B3E'
PEANUT, YELLOW, TAN = '#4A3920', '#FFD54F', '#CDB48C'
INK, QUIET, DIM = '#E8EAED', '#9AA0A6', '#5F6368'
SKY, SKY_LOW, HILL_FAR, HILL, PATH, TREE, TRUNK = '#86C1EA', '#BFE0F3', '#8FBF6C', '#6E9F50', '#DCC79A', '#3E7034', '#6B4B2E'
SHIRT, SKIN = '#D9534F', '#F1D1AE'


def g(v):
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return (f'M{g(x + r)} {g(y)}H{g(x + w - r)}A{g(r)} {g(r)} 0 0 1 {g(x + w)} {g(y + r)}V{g(y + h - r)}A{g(r)} {g(r)} 0 0 1 {g(x + w - r)} {g(y + h)}'
            f'H{g(x + r)}A{g(r)} {g(r)} 0 0 1 {g(x)} {g(y + h - r)}V{g(y + r)}A{g(r)} {g(r)} 0 0 1 {g(x + r)} {g(y)}Z')


def circle(cx, cy, r):
    return f'M{g(cx - r)} {g(cy)}a{g(r)} {g(r)} 0 1 0 {g(2 * r)} 0a{g(r)} {g(r)} 0 1 0 {g(-2 * r)} 0Z'


class Pic:
    def __init__(self):
        self.layers = []   # (path, fill, alpha, stroke, stroke width, even-odd)

    def fill(self, d, color, alpha=1.0, evenodd=False):
        self.layers.append((d, color, alpha, None, 0, evenodd))

    def stroke(self, d, color, width):
        self.layers.append((d, None, 1.0, color, width, False))

    def vector(self, name):
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               f'<!-- {name}: written by peanuts/scripts/layouts.py. Change it there and run the script, not here. -->',
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
               f'    android:width="{W}dp" android:height="{H}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">']
        for d, fill, a, stroke, sw, eo in self.layers:
            attrs = [f'android:pathData="{d}"']
            if fill:
                attrs.append(f'android:fillColor="{fill}"')
                if a < 1: attrs.append(f'android:fillAlpha="{g(a)}"')
            if stroke:
                attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{g(sw)}"', 'android:strokeLineJoin="round"']
            if eo: attrs.append('android:fillType="evenOdd"')
            out.append('    <path ' + ' '.join(attrs) + '/>')
        out.append('</vector>')
        return '\n'.join(out) + '\n'


def screen(p, x, y, w, h, r=5):
    """A screen with the game running in it (clipped to its corners by the bezel drawn last: see bezel)"""
    p.fill(rrect(x, y, w, h, 1), SKY)
    p.fill(f'M{g(x)} {g(y + h * .38)}H{g(x + w)}V{g(y + h * .6)}H{g(x)}Z', SKY_LOW)
    for cx, cy, s in ((.3, .16, 1), (.72, .24, .8)):   # two clouds
        for part in (rrect(x + w * (cx - .07 * s), y + h * (cy + .015), w * .14 * s, h * .06 * s, h * .03 * s),
                     circle(x + w * (cx - .02 * s), y + h * (cy + .02), h * .045 * s), circle(x + w * (cx + .03 * s), y + h * (cy + .03), h * .035 * s)):
            p.fill(part, '#F2F7FB')   # each part a path of its own: one path of them would leave holes where their windings cancel
    p.fill(f'M{g(x)} {g(y + h * .6)}Q{g(x + w * .28)} {g(y + h * .4)} {g(x + w * .6)} {g(y + h * .56)}T{g(x + w)} {g(y + h * .5)}V{g(y + h)}H{g(x)}Z', HILL_FAR)
    p.fill(f'M{g(x)} {g(y + h * .74)}Q{g(x + w * .45)} {g(y + h * .58)} {g(x + w)} {g(y + h * .7)}V{g(y + h)}H{g(x)}Z', HILL)
    p.fill(f'M{g(x + w * .47)} {g(y + h * .62)}H{g(x + w * .53)}L{g(x + w * .68)} {g(y + h)}H{g(x + w * .32)}Z', PATH)
    for tx, s in ((.16, 1), (.84, .8)):
        p.fill(rrect(x + w * tx - w * .006, y + h * (.5 + .05 * s), w * .012, h * .1 * s, 0.5), TRUNK)
        p.fill(circle(x + w * tx, y + h * (.47 + .03 * (1 - s)), h * .085 * s), TREE)
    p.fill(rrect(x + w * .487, y + h * .74, w * .026, h * .1, 1.2), SHIRT)
    p.fill(circle(x + w * .5, y + h * .71, h * .033), SKIN)


def bezel(p, x, y, w, h, r=5, edge=3):
    """The screen's black edge over the game's corners (even-odd: a frame with a rounded hole)"""
    p.fill(rrect(x - edge, y - edge, w + 2 * edge, h + 2 * edge, r + edge) + rrect(x, y, w, h, r), BEZEL, evenodd=True)


def dim(p, x, y, w, h, a=.4):
    p.fill(rrect(x, y, w, h, 1), '#000000', a)


def text_bar(p, x, y, w, color=INK, a=.85, h=2.6):
    p.fill(rrect(x, y - h / 2, w, h, h / 2), color, a)


def game_rows(p, x, y, w, row, n=5, sel=0, cover=True, icon=True):
    """The game menu's content: the game's header (cover, name, chips), the rows (an icon and a name), Stop set apart at the bottom"""
    cy = y
    if cover:
        p.fill(rrect(x, cy, row * 1.15, row * 1.15, 2.2), '#5B6E8C')
        p.fill(rrect(x + row * .2, cy + row * .25, row * .75, row * .35, 1), '#E9EEF6', .8)
        text_bar(p, x + row * 1.45, cy + row * .38, w * .5, INK, .95, 3)
        p.fill(rrect(x + row * 1.45, cy + row * .72, w * .14, row * .32, row * .16), CHIP)
        text_bar(p, x + row * 1.45 + w * .17, cy + row * .88, w * .22, QUIET, .7, 2.2)
        cy += row * 1.55
    for i in range(n):
        yy = cy + i * row
        if i == sel:
            p.fill(rrect(x - row * .15, yy, w + row * .3, row * .86, 2.4), PEANUT, .9)
            p.stroke(rrect(x - row * .15, yy, w + row * .3, row * .86, 2.4), YELLOW, 1.1)
        if icon: p.fill(circle(x + row * .32, yy + row * .43, row * .17), INK if i == sel else QUIET, .9)
        text_bar(p, x + row * .75, yy + row * .43, w * (.55 - .06 * (i % 3)), INK, .9 if i == sel else .75)
    return cy + n * row


def stop(p, x, y, w, row):
    p.fill(f'M{g(x)} {g(y)}H{g(x + w)}V{g(y + .5)}H{g(x)}Z', CHIP)
    p.stroke(f'M{g(x + row * .32 - row * .12)} {g(y + row * .38)}a{g(row * .17)} {g(row * .17)} 0 1 0 {g(row * .24)} 0', QUIET, .8)
    text_bar(p, x + row * .75, y + row * .55, w * .4, INK, .75)


def rail(p, x, y, h, wr, icons, sel, game_first=False):
    """The rail of tab icons: the selected one on the peanut block; with Game first, a line under it; the settings at the bottom"""
    p.fill(f'M{g(x)} {g(y)}H{g(x + wr)}V{g(y + h)}H{g(x)}Z', RAIL)
    step = wr * 1.05
    yy = y + wr * .3
    for i in range(icons):
        if i == sel: p.fill(rrect(x, yy, wr, step * .92, 0), PEANUT)
        c = YELLOW if i == sel else QUIET
        p.fill(rrect(x + wr * .32, yy + step * .3, wr * .36, step * .32, 1), c, .95)
        yy += step
        if game_first and i == 0:
            p.fill(f'M{g(x + wr * .2)} {g(yy - step * .05)}H{g(x + wr * .8)}V{g(yy + .45 - step * .05)}H{g(x + wr * .2)}Z', CHIP)
            yy += step * .12
    sy = y + h - step * 1.1
    p.fill(rrect(x + wr * .3, sy + step * .25, wr * .4, step * .4, 1), QUIET, .9)


def tab_rows(p, x, y, w, row):
    """A tab of the quick menu: its header (Back, the name), a section head, a segmented choice, switches and a slider"""
    p.fill(circle(x + row * .35, y + row * .4, row * .32), CHIP)
    text_bar(p, x + row * .95, y + row * .4, w * .36, INK, .95, 3)
    yy = y + row * 1.1
    text_bar(p, x, yy, w * .22, TAN, .9, 2)
    yy += row * .45
    p.fill(rrect(x, yy, w, row * .72, 2.4), ROW)
    p.fill(rrect(x + w * .35, yy + row * .08, w * .31, row * .56, 2), PEANUT)
    p.stroke(rrect(x + w * .35, yy + row * .08, w * .31, row * .56, 2), YELLOW, 1)
    yy += row * 1.1
    for k in range(2):
        text_bar(p, x, yy + row * .3, w * (.5 - .1 * k), INK, .75)
        p.fill(rrect(x + w - row * .9, yy + row * .14, row * .9, row * .34, row * .17), YELLOW if k == 0 else DIM, .7 if k == 0 else 1)
        p.fill(circle(x + w - (row * .22 if k == 0 else row * .68), yy + row * .31, row * .2), YELLOW if k == 0 else '#BDC1C6')
        yy += row * .8
    text_bar(p, x, yy + row * .2, w * .3, INK, .75)
    p.fill(rrect(x, yy + row * .6, w, row * .1, row * .05), DIM)
    p.fill(rrect(x, yy + row * .6, w * .62, row * .1, row * .05), YELLOW)
    p.fill(circle(x + w * .62, yy + row * .65, row * .17), YELLOW)


def device(p, x, y, w, h, r=9):
    p.fill(rrect(x, y, w, h, r), BODY)


def two():
    p = Pic()
    device(p, 6, 14, 228, 140, 12)
    sx, sy, sw, sh = 16, 22, 208, 124
    screen(p, sx, sy, sw, sh)
    dim(p, sx, sy, sw, sh, .38)
    # the game menu at the left
    gw = sw * .33
    p.fill(f'M{g(sx)} {g(sy)}H{g(sx + gw)}V{g(sy + sh)}H{g(sx)}Z', SHEET, .97)
    row = 12.5
    end = game_rows(p, sx + 7, sy + 8, gw - 14, row, n=5, sel=0)
    stop(p, sx + 7, sy + sh - row * 1.2, gw - 14, row)
    # the quick menu at the right
    qw = sw * .38
    qx = sx + sw - qw
    p.fill(f'M{g(qx)} {g(sy)}H{g(sx + sw)}V{g(sy + sh)}H{g(qx)}Z', SHEET, .97)
    wr = 15
    rail(p, qx, sy, sh, wr, 5, 0)
    tab_rows(p, qx + wr + 7, sy + 7, qw - wr - 13, 12.5)
    bezel(p, sx, sy, sw, sh)
    return p


def right():
    p = Pic()
    device(p, 6, 14, 228, 140, 12)
    sx, sy, sw, sh = 16, 22, 208, 124
    screen(p, sx, sy, sw, sh)
    dim(p, sx, sy, sw, sh, .38)
    qw = sw * .5
    qx = sx + sw - qw
    p.fill(f'M{g(qx)} {g(sy)}H{g(sx + sw)}V{g(sy + sh)}H{g(qx)}Z', SHEET, .97)
    wr = 16
    rail(p, qx, sy, sh, wr, 5, 0, game_first=True)
    row = 12.5
    game_rows(p, qx + wr + 9, sy + 8, qw - wr - 17, row, n=5, sel=0)
    stop(p, qx + wr + 9, sy + sh - row * 1.2, qw - wr - 17, row)
    bezel(p, sx, sy, sw, sh)
    return p


def bottom():
    p = Pic()
    # a dual-screen device as the AYN Thor: the top screen (16:9) over the hinge, the bottom screen (nearly square) under it
    device(p, 48, 2, 144, 84, 9)
    tx, ty, tw, th = 56, 8, 128, 72
    screen(p, tx, ty, tw, th)
    dim(p, tx, ty, tw, th, .3)   # the game runs on, dimmed a little while the menu is open
    bezel(p, tx, ty, tw, th, 4, 2.5)
    p.fill(rrect(58, 87, 124, 4, 2), '#2A2B2F')
    device(p, 76, 92, 88, 74, 9)
    bx, by, bw, bh = 83, 98, 74, 62
    p.fill(rrect(bx, by, bw, bh, 1), '#15110B')
    mx, my = bx, by + 3
    p.fill(rrect(mx, my, bw, bh - 3, 4), SHEET)
    p.fill(f'M{g(mx)} {g(my + 4)}H{g(mx + bw)}V{g(by + bh)}H{g(mx)}Z', SHEET)
    wr = 11
    rail(p, mx, my, bh - 3, wr, 3, 0, game_first=True)
    row = 9
    game_rows(p, mx + wr + 6, my + 5, bw - wr - 12, row, n=3, sel=0)
    stop(p, mx + wr + 6, by + bh - row * 1.15, bw - wr - 12, row)
    bezel(p, bx, by, bw, bh, 4, 2.5)
    return p


PICS = {'two': two, 'right': right, 'bottom': bottom}


def files():
    """pil_layout_<name>.xml -> its text"""
    return {f'pil_layout_{name}.xml': make().vector(f'pil_layout_{name}') for name, make in PICS.items()}


def main(argv):
    check = argv[1:] == ['--check']
    if argv[1:] and not check:
        raise SystemExit('usage: layouts.py [--check]')
    want = files()
    wrong = sorted(n for n, text in want.items() if not (OUT / n).is_file() or (OUT / n).read_bytes() != text.encode('utf-8'))
    extra = sorted({p.name for p in OUT.glob('pil_layout_*.xml')} - set(want))
    if check:
        for n in wrong:
            print(f'{n}: not what layouts.py writes (run peanuts/scripts/layouts.py)')
        for n in extra:
            print(f'{n}: no picture in layouts.py names it')
        return 1 if wrong or extra else 0
    for n in wrong:
        (OUT / n).write_text(want[n], encoding='utf-8', newline='\n')
    for n in extra:
        (OUT / n).unlink()
    print(f'{len(want)} pictures, {len(wrong)} written, {len(extra)} removed')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
