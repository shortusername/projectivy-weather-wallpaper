#!/usr/bin/env python3
"""Render the radar wallpaper exactly as the plugin would, without a device.

Mirrors GeographyRenderer.kt and Backgrounds.radarMap() so layout and styling
can be iterated in seconds rather than one APK build at a time. Reads the same
assets/geography.bin the app ships and pulls live RainViewer tiles.
"""

import io
import math
import struct
import sys
import urllib.request

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
SCALE = 100000.0
TILE = 256

C_WATER = (8, 18, 32)
C_LAND = (58, 72, 88)
C_LAKE = (26, 40, 58)

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_LIGHT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-ExtraLight.ttf"


def load(path):
    d = open(path, "rb").read()
    assert d[:4] == b"WXGE"
    (ver,) = struct.unpack_from("<H", d, 4)
    assert ver == 5, ver
    pn, ln, cn = struct.unpack_from("<III", d, 6)
    off = 18

    def ring(off):
        (mode,) = struct.unpack_from("<B", d, off); off += 1
        (n,) = struct.unpack_from("<H", d, off); off += 2
        pts = []
        if mode == 0:
            lon, lat = struct.unpack_from("<ii", d, off); off += 8
            pts.append((lon / SCALE, lat / SCALE))
            for _ in range(n - 1):
                dx, dy = struct.unpack_from("<hh", d, off); off += 4
                lon += dx; lat += dy
                pts.append((lon / SCALE, lat / SCALE))
        else:
            for _ in range(n):
                lon, lat = struct.unpack_from("<ii", d, off); off += 8
                pts.append((lon / SCALE, lat / SCALE))
        return off, pts

    polys = []
    for _ in range(pn):
        layer, rc = struct.unpack_from("<BH", d, off); off += 3
        rings = []
        for _ in range(rc):
            off, pts = ring(off); rings.append(pts)
        polys.append((layer, rings))

    lines = []
    for _ in range(ln):
        (layer,) = struct.unpack_from("<B", d, off); off += 1
        off, pts = ring(off)
        lines.append((layer, pts))

    places = []
    for _ in range(cn):
        lon, lat = struct.unpack_from("<ii", d, off); off += 8
        (rank,) = struct.unpack_from("<B", d, off); off += 1
        (nl,) = struct.unpack_from("<B", d, off); off += 1
        name = d[off:off + nl].decode("utf-8"); off += nl
        places.append((lon / SCALE, lat / SCALE, rank / 10.0, name))

    assert off == len(d)
    return polys, lines, places


def lat_to_merc(lat):
    lat = max(-85.05, min(85.05, lat))
    r = math.radians(lat)
    return (1.0 - math.log(math.tan(r) + 1.0 / math.cos(r)) / math.pi) / 2.0


def merc_to_lat(y):
    return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y))))


def fetch(url):
    try:
        req = urllib.request.Request(url, headers={
            "User-Agent": "ProjectivyWeatherWallpaper/1.9 (preview)"})
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.read()
    except Exception as e:
        print(f"  fetch failed {url[:60]}: {e}", file=sys.stderr)
        return None




def dash(pts, on=14.0, off=9.0):
    """Split a polyline into dashes. Political boundaries read as dashed on
    broadcast maps, which also stops them being mistaken for coastline."""
    out, cur, carry, drawing = [], [], 0.0, True
    for i in range(len(pts) - 1):
        (ax, ay), (bx, by) = pts[i], pts[i + 1]
        seg = math.hypot(bx - ax, by - ay)
        if seg <= 0: continue
        t = 0.0
        while t < seg:
            need = (on if drawing else off) - carry
            step = min(need, seg - t)
            x0 = ax + (bx - ax) * (t / seg); y0 = ay + (by - ay) * (t / seg)
            t2 = t + step
            x1 = ax + (bx - ax) * (t2 / seg); y1 = ay + (by - ay) * (t2 / seg)
            if drawing:
                if cur and cur[-1] == (x0, y0): cur.append((x1, y1))
                else:
                    if len(cur) >= 2: out.append(cur)
                    cur = [(x0, y0), (x1, y1)]
            carry += step
            t = t2
            if carry >= (on if drawing else off) - 1e-9:
                if drawing and len(cur) >= 2: out.append(cur); cur = []
                drawing = not drawing; carry = 0.0
    if len(cur) >= 2: out.append(cur)
    return out


def clip_poly(pts, xmin, ymin, xmax, ymax):
    """Sutherland-Hodgman. Keeps fills sane when a polygon dwarfs the viewport."""
    def inside(p, edge):
        if edge == 0: return p[0] >= xmin
        if edge == 1: return p[0] <= xmax
        if edge == 2: return p[1] >= ymin
        return p[1] <= ymax

    def isect(a, b, edge):
        ax, ay = a; bx, by = b
        if edge in (0, 1):
            x = xmin if edge == 0 else xmax
            t = (x - ax) / (bx - ax) if bx != ax else 0.0
            return (x, ay + t * (by - ay))
        y = ymin if edge == 2 else ymax
        t = (y - ay) / (by - ay) if by != ay else 0.0
        return (ax + t * (bx - ax), y)

    out = pts
    for edge in range(4):
        if not out: return []
        inp, out = out, []
        prev = inp[-1]
        for cur in inp:
            ci, pi = inside(cur, edge), inside(prev, edge)
            if ci:
                if not pi: out.append(isect(prev, cur, edge))
                out.append(cur)
            elif pi:
                out.append(isect(prev, cur, edge))
            prev = cur
    return out


def clip_segments(pts, xmin, ymin, xmax, ymax):
    """Cohen-Sutherland per segment. Strokes only the visible parts, so clipped
    geometry never draws phantom lines along the viewport edge."""
    def code(p):
        c = 0
        if p[0] < xmin: c |= 1
        elif p[0] > xmax: c |= 2
        if p[1] < ymin: c |= 4
        elif p[1] > ymax: c |= 8
        return c

    runs, cur = [], []
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        ca, cb = code(a), code(b)
        ax, ay = a; bx, by = b
        accept = False
        for _ in range(8):
            if not (ca | cb): accept = True; break
            if ca & cb: break
            c = ca or cb
            if c & 8:   x = ax + (bx - ax) * (ymax - ay) / (by - ay); y = ymax
            elif c & 4: x = ax + (bx - ax) * (ymin - ay) / (by - ay); y = ymin
            elif c & 2: y = ay + (by - ay) * (xmax - ax) / (bx - ax); x = xmax
            else:       y = ay + (by - ay) * (xmin - ax) / (bx - ax); x = xmin
            if c == ca: ax, ay = x, y; ca = code((ax, ay))
            else:       bx, by = x, y; cb = code((bx, by))
        if accept:
            if cur and cur[-1] == (ax, ay): cur.append((bx, by))
            else:
                if len(cur) >= 2: runs.append(cur)
                cur = [(ax, ay), (bx, by)]
        else:
            if len(cur) >= 2: runs.append(cur)
            cur = []
    if len(cur) >= 2: runs.append(cur)
    return runs


DENSITY = (1.5, 18)   # (threshold offset, max labels)


def render(lat, lon, zoom, out_path, place_label="OSSINING", with_radar=True):
    polys, lines, places = load(
        "/mnt/user-data/outputs/weather-plugin/src/main/assets/geography.bin")

    n = 1 << zoom
    world = n * TILE
    cx = (lon + 180.0) / 360.0 * world
    cy = lat_to_merc(lat) * world
    scale = W / (3.0 * TILE)
    view_w, view_h = W / scale, H / scale
    left, top = cx - view_w * 0.62, cy - view_h * 0.54

    def px(l): return (((l + 180.0) / 360.0 * world) - left) * scale
    def py(l): return (lat_to_merc(l) * world - top) * scale

    lon_min = left / world * 360 - 180 - 1.5
    lon_max = (left + view_w) / world * 360 - 180 + 1.5
    lat_max = merc_to_lat(top / world) + 1.5
    lat_min = merc_to_lat((top + view_h) / world) - 1.5

    # Water base with a subtle vertical tone shift so it isn't a flat slab.
    img = Image.new("RGB", (W, H), C_WATER)
    grad = Image.new("RGB", (1, H))
    gd = ImageDraw.Draw(grad)
    for y in range(H):
        t = y / H
        gd.point((0, y), fill=(int(C_WATER[0] + 10 * (1 - t)),
                               int(C_WATER[1] + 14 * (1 - t)),
                               int(C_WATER[2] + 20 * (1 - t))))
    img = grad.resize((W, H))
    dr = ImageDraw.Draw(img)

    # Land is drawn into a mask first: that gives us a gradient fill and, by
    # subtracting the mask from its own blur, a soft coastal glow.
    land_mask = Image.new("L", (W, H), 0)
    lm = ImageDraw.Draw(land_mask)
    lake_mask = Image.new("L", (W, H), 0)
    km = ImageDraw.Draw(lake_mask)

    # --- filled land and lakes -------------------------------------------
    drew = 0
    for layer, rings in polys:
        lons = [p[0] for r in rings for p in r]
        lats = [p[1] for r in rings for p in r]
        if max(lons) < lon_min or min(lons) > lon_max: continue
        if max(lats) < lat_min or min(lats) > lat_max: continue
        PAD = 300
        target = km if layer == 1 else lm
        for i, r in enumerate(rings):
            pts = [(px(a), py(b)) for a, b in r]
            if len(pts) < 3: continue
            cp = clip_poly(pts, -PAD, -PAD, W + PAD, H + PAD)
            if len(cp) >= 3:
                target.polygon(cp, fill=(0 if i else 255))
        drew += 1

    # Land fill: vertical gradient through the mask, lighter at the top so the
    # scene has a light direction rather than reading as a flat cutout.
    land_grad = Image.new("RGB", (1, H))
    lg = ImageDraw.Draw(land_grad)
    for y in range(H):
        t = y / H
        lg.point((0, y), fill=(int(C_LAND[0] + 16 * (1 - t) - 6 * t),
                               int(C_LAND[1] + 16 * (1 - t) - 6 * t),
                               int(C_LAND[2] + 18 * (1 - t) - 6 * t)))
    img.paste(land_grad.resize((W, H)), (0, 0), land_mask)
    img.paste(Image.new("RGB", (W, H), C_LAKE), (0, 0), lake_mask)
    lake_edge = lake_mask.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.MaxFilter(3))
    img.paste(Image.new("RGB", (W, H), (110, 150, 190)), (0, 0),
              lake_edge.point(lambda a: int(a * 0.45)))

    # Coastal glow: blur the mask, subtract it, and you are left with a soft
    # band hugging the shoreline. Cheap, and it reads like broadcast graphics.
    from PIL import ImageChops
    blurred = land_mask.filter(ImageFilter.GaussianBlur(11))
    halo = ImageChops.subtract(blurred, land_mask)
    halo = halo.point(lambda a: int(a * 0.85))
    img.paste(Image.new("RGB", (W, H), (95, 150, 200)), (0, 0), halo)

    # Crisp shoreline on top of the glow.
    coast = land_mask.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.MaxFilter(3))
    img.paste(Image.new("RGB", (W, H), (150, 190, 225)), (0, 0),
              coast.point(lambda a: int(a * 0.75)))
    dr = ImageDraw.Draw(img)

    # --- borders ----------------------------------------------------------
    for layer, pts in lines:
        lons = [p[0] for p in pts]; lats = [p[1] for p in pts]
        if max(lons) < lon_min or min(lons) > lon_max: continue
        if max(lats) < lat_min or min(lats) > lat_max: continue
        xy = [(px(a), py(b)) for a, b in pts]
        # Halo underneath so borders stay legible over both land and radar.
        for run in clip_segments(xy, -300, -300, W + 300, H + 300):
            pieces = [run] if layer == 0 else dash(run)
            for piece in pieces:
                dr.line(piece, fill=(8, 14, 24), width=6 if layer == 0 else 5)
        col = (225, 236, 250) if layer == 0 else (196, 214, 236)
        for run in clip_segments(xy, -300, -300, W + 300, H + 300):
            pieces = [run] if layer == 0 else dash(run)
            for piece in pieces:
                dr.line(piece, fill=col, width=3 if layer == 0 else 2)

    # --- live radar -------------------------------------------------------
    if with_radar:
        import json
        meta = fetch("https://api.rainviewer.com/public/weather-maps.json")
        if meta:
            m = json.loads(meta)
            host = m.get("host", "https://tilecache.rainviewer.com")
            past = m.get("radar", {}).get("past", [])
            if past:
                path = past[-1]["path"]
                radar = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                tx0 = math.floor(left / TILE); tx1 = math.floor((left + view_w) / TILE)
                ty0 = math.floor(top / TILE); ty1 = math.floor((top + view_h) / TILE)
                got = 0
                for tx in range(tx0, tx1 + 1):
                    for ty in range(ty0, ty1 + 1):
                        wx = tx % n
                        if ty < 0 or ty >= n: continue
                        data = fetch(f"{host}{path}/{TILE}/{zoom}/{wx}/{ty}/2/1_1.png")
                        if not data: continue
                        t = Image.open(io.BytesIO(data)).convert("RGBA")
                        size = int(TILE * scale)
                        t = t.resize((size, size), Image.LANCZOS)
                        radar.paste(t, (int((tx * TILE - left) * scale),
                                        int((ty * TILE - top) * scale)), t)
                        got += 1
                print(f"  radar tiles: {got}")
                # bloom pass then sharp pass, as the Kotlin does
                glow = radar.filter(ImageFilter.GaussianBlur(9))
                glow.putalpha(glow.getchannel("A").point(lambda a: int(a * 0.42)))
                img = Image.alpha_composite(img.convert("RGBA"), glow)
                img = Image.alpha_composite(img, radar)
                img = img.convert("RGB")
                dr = ImageDraw.Draw(img)

    # --- city labels ------------------------------------------------------
    f_label = ImageFont.truetype(FONT, 25)
    thresh = zoom + DENSITY[0]
    vis = [p for p in places
           if p[2] <= thresh and lon_min <= p[0] <= lon_max and lat_min <= p[1] <= lat_max]
    vis.sort(key=lambda p: p[2])
    placed = []          # occupied label boxes, for collision avoidance
    drawn = 0
    for lo, la, mz, name in vis:
        if drawn >= DENSITY[1]: break
        x, y = px(lo), py(la)
        if x < 40 or x > W - 40 or y < 40 or y > H - 90: continue
        if x < W * 0.46 and y < H * 0.62: continue
        spaced = " ".join(name.upper()) if mz <= 3.0 else name
        tw = dr.textlength(spaced, font=f_label)
        if x + 12 + tw > W - 30:
            spaced = name          # fall back to unspaced
            tw = dr.textlength(spaced, font=f_label)
            if x + 12 + tw > W - 30:
                continue           # still no room; drop it
        # Reject anything overlapping a label already placed. Entries are
        # sorted by rank, so the more significant place wins the space.
        box = (x - 8, y - 16, x + 14 + tw, y + 16)
        if any(box[0] < b[2] and box[2] > b[0] and box[1] < b[3] and box[3] > b[1]
               for b in placed):
            continue
        placed.append(box)
        drawn += 1

        dr.ellipse([x - 5, y - 5, x + 5, y + 5], fill=(0, 0, 0))
        dr.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(228, 238, 250))
        dr.text((x + 12, y - 4), spaced, font=f_label, fill=(232, 240, 250),
                stroke_width=3, stroke_fill=(4, 10, 18))

    # --- your location ----------------------------------------------------
    mx, my = px(lon), py(lat)
    for rad, col in ((26, (255, 255, 255, 40)), (17, (255, 255, 255, 90))):
        ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(ov).ellipse([mx - rad, my - rad, mx + rad, my + rad],
                                   outline=col, width=3)
        img = Image.alpha_composite(img.convert("RGBA"), ov).convert("RGB")
    dr = ImageDraw.Draw(img)
    dr.ellipse([mx - 7, my - 7, mx + 7, my + 7], fill=(255, 255, 255),
               outline=(0, 0, 0), width=2)

    # --- vignette ---------------------------------------------------------
    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    steps = 60
    for i in range(steps):
        t = i / steps
        r = int(W * 0.72 * (1 - t) + W * 0.36 * t)
        vd.ellipse([W // 2 - r, H // 2 - int(r * H / W), W // 2 + r, H // 2 + int(r * H / W)],
                   fill=int(58 * (1 - t) ** 2))
    vig = vig.filter(ImageFilter.GaussianBlur(80))
    img = Image.composite(Image.new("RGB", (W, H), (0, 4, 10)), img, vig)
    dr = ImageDraw.Draw(img)

    # --- weather panel ----------------------------------------------------
    vert = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    vd2 = ImageDraw.Draw(vert)
    for y in range(int(H * 0.62)):
        a = int(150 * (1 - y / (H * 0.62)) ** 1.3)
        vd2.line([(0, y), (W, y)], fill=(0, 0, 0, a))
    horiz = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(horiz)
    for x in range(int(W * 0.52)):
        a = int(105 * (1 - x / (W * 0.52)) ** 1.4)
        hd.line([(x, 0), (x, H)], fill=(0, 0, 0, a))
    img = Image.alpha_composite(img.convert("RGBA"), vert)
    img = Image.alpha_composite(img, horiz).convert("RGB")
    dr = ImageDraw.Draw(img)

    M = 120
    dr.text((M, 160), place_label, font=ImageFont.truetype(FONT, 34),
            fill=(255, 255, 255))
    dr.text((M, 210), "77\u00B0F", font=ImageFont.truetype(FONT_LIGHT, 200),
            fill=(255, 255, 255))
    dr.text((M, 428), "Overcast", font=ImageFont.truetype(FONT_LIGHT, 60),
            fill=(245, 245, 245))
    dr.text((M, 512), "Feels like 83\u00B0   \u00B7   H 79\u00B0  L 67\u00B0   \u00B7   Wind 5 NW",
            font=ImageFont.truetype(FONT_LIGHT, 38), fill=(225, 225, 225))
    dr.text((M, H - 62), "Radar: RainViewer \u00B7 Map data: Natural Earth",
            font=ImageFont.truetype(FONT, 24), fill=(150, 155, 165))

    img.save(out_path, quality=95)
    print(f"  wrote {out_path} ({drew} polygons in view)")


if __name__ == "__main__":
    render(41.157, -73.862, 7, "/mnt/user-data/outputs/preview-z7.png")
    render(41.157, -73.862, 6, "/mnt/user-data/outputs/preview-z6.png")
