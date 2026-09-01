#!/usr/bin/env python3
"""Generate layered PSD templates for wallpaper pack authors.

Produces a real multi-layer PSD with named guide layers that can be toggled or
deleted, rather than a flattened JPEG with lines drawn on it. Opens in
Photoshop, Affinity Photo, GIMP, Krita and Photopea.

Run: python3 tools/make_templates.py
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from pytoshop import enums, image_data
from pytoshop.user import nested_layers

W, H = 1920, 1080

FONT_DIRS = [
    "/usr/share/fonts/truetype/dejavu",
    "/Library/Fonts",
    "C:/Windows/Fonts",
]


def font(size, bold=False):
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    for d in FONT_DIRS:
        p = os.path.join(d, name)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def rgba_layer(name, draw_fn, opacity=255, visible=True):
    """Build one PSD layer from a PIL drawing callback."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(img), img)
    arr = np.asarray(img).astype(np.uint8)

    channels = {
        -1: arr[:, :, 3],          # alpha
        0: arr[:, :, 0],
        1: arr[:, :, 1],
        2: arr[:, :, 2],
    }
    return nested_layers.Image(
        name=name,
        visible=visible,
        opacity=opacity,
        top=0, left=0, bottom=H, right=W,
        channels=channels,
        color_mode=enums.ColorMode.rgb,
    )


# --------------------------------------------------------------------- layers

def draw_artwork_hint(d, img):
    """Bottom layer: a neutral field so the guides are visible on open."""
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)],
               fill=(int(34 + 18 * (1 - t)), int(44 + 20 * (1 - t)),
                     int(56 + 24 * (1 - t)), 255))
    f = font(44)
    msg = "REPLACE THIS LAYER WITH YOUR ARTWORK"
    tw = d.textlength(msg, font=f)
    d.text(((W - tw) / 2, H * 0.52), msg, font=f, fill=(255, 255, 255, 70))


def draw_panel_zone(d, img):
    """Where the plugin draws temperature, conditions and detail lines."""
    d.rectangle([0, 0, W * 0.55, H * 0.62], fill=(255, 60, 60, 38))
    d.rectangle([0, 0, W * 0.55, H * 0.62], outline=(255, 90, 90, 190), width=4)
    f = font(34, bold=True)
    d.text((44, 40), "WEATHER PANEL", font=f, fill=(255, 190, 190, 235))
    f2 = font(26)
    d.text((44, 84), "Temperature, conditions, detail lines.", font=f2,
           fill=(255, 200, 200, 210))
    d.text((44, 118), "Keep detail and focal points out of this area.", font=f2,
           fill=(255, 200, 200, 210))


def draw_app_row(d, img):
    """The launcher's own app shelf sits across the bottom."""
    top = H * 0.80
    d.rectangle([0, top, W, H], fill=(255, 170, 40, 40))
    d.rectangle([0, top, W, H], outline=(255, 190, 70, 190), width=4)
    f = font(32, bold=True)
    d.text((44, top + 28), "APP SHELF", font=f, fill=(255, 220, 160, 235))
    f2 = font(26)
    d.text((44, top + 74),
           "Projectivy draws app icons here. Nothing important below this line.",
           font=f2, fill=(255, 225, 180, 210))


def draw_clock_zone(d, img):
    """Launcher clock and status, top right."""
    left, bottom = W * 0.78, H * 0.13
    d.rectangle([left, 0, W, bottom], fill=(120, 170, 255, 38))
    d.rectangle([left, 0, W, bottom], outline=(150, 190, 255, 180), width=4)
    f = font(26, bold=True)
    d.text((left + 24, 26), "CLOCK / STATUS", font=f, fill=(200, 225, 255, 235))


def draw_focal_zone(d, img):
    """The region that actually reads: right of the panel, above the shelf."""
    left, top, right, bottom = W * 0.55, H * 0.13, W, H * 0.80
    d.rectangle([left, top, right, bottom], fill=(80, 230, 140, 30))
    d.rectangle([left, top, right, bottom], outline=(110, 240, 165, 190), width=4)
    f = font(34, bold=True)
    d.text((left + 30, top + 26), "PUT YOUR FOCAL POINT HERE", font=f,
           fill=(190, 255, 215, 240))
    f2 = font(26)
    d.text((left + 30, top + 74),
           "This is the part of the frame nothing else covers.",
           font=f2, fill=(200, 255, 220, 215))


def draw_overscan(d, img):
    """Some TVs and projectors crop the outer edge."""
    m = 0.035
    d.rectangle([0, 0, W, H], outline=(255, 255, 255, 60), width=2)
    d.rectangle([W * m, H * m, W * (1 - m), H * (1 - m)],
                outline=(255, 255, 255, 130), width=3)
    f = font(24)
    caption = "3.5% overscan — may be cropped on some TVs"
    tw = d.textlength(caption, font=f)
    # Bottom-centre: the top-left corner is taken by the panel guide's title.
    d.text(((W - tw) / 2, H * (1 - m) - 38), caption, font=f,
           fill=(255, 255, 255, 170))


def draw_thirds(d, img):
    for i in (1, 2):
        d.line([(W * i / 3, 0), (W * i / 3, H)], fill=(255, 255, 255, 55), width=2)
        d.line([(0, H * i / 3), (W, H * i / 3)], fill=(255, 255, 255, 55), width=2)
    d.line([(W / 2, 0), (W / 2, H)], fill=(255, 255, 255, 40), width=1)
    d.line([(0, H / 2), (W, H / 2)], fill=(255, 255, 255, 40), width=1)


def draw_scrim_preview(d, img):
    """Approximates the darkening the plugin applies over your artwork."""
    for y in range(int(H * 0.68)):
        a = int(150 * (1 - y / (H * 0.68)) ** 1.3)
        d.line([(0, y), (W, y)], fill=(0, 0, 0, a))


# ----------------------------------------------------------------------- main

# (name, draw fn, opacity, visible) — listed bottom layer first, which is the
# order the flatten pass wants. pytoshop takes them top-first, so build()
# reverses.
LAYER_SPEC = [
    ("YOUR ARTWORK - replace me", draw_artwork_hint, 255, True),
    ("PREVIEW - plugin scrim, toggle on to check contrast",
     draw_scrim_preview, 255, False),
    ("GUIDE - focal area", draw_focal_zone, 255, True),
    ("GUIDE - weather panel", draw_panel_zone, 255, True),
    ("GUIDE - app shelf", draw_app_row, 255, True),
    ("GUIDE - clock zone", draw_clock_zone, 255, True),
    ("GUIDE - overscan", draw_overscan, 255, True),
    ("GUIDE - thirds", draw_thirds, 180, False),
]


def build(path, kind):
    layers = [
        rgba_layer(name, fn, opacity=op, visible=vis)
        for name, fn, op, vis in reversed(LAYER_SPEC)
    ]

    psd = nested_layers.nested_layers_to_psd(layers, color_mode=enums.ColorMode.rgb)

    # pytoshop leaves the merged image data empty, which shows as a black
    # thumbnail in file browsers and in apps that preview before compositing.
    # Flatten the same layers here and store that.
    flat = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    for name, fn, opacity, visible in LAYER_SPEC:
        if not visible:
            continue
        overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        fn(ImageDraw.Draw(overlay), overlay)
        if opacity < 255:
            a = overlay.getchannel("A").point(lambda v: int(v * opacity / 255))
            overlay.putalpha(a)
        flat = Image.alpha_composite(flat, overlay)

    rgb = np.asarray(flat.convert("RGB")).astype(np.uint8)
    # Shape must be (channels, height, width).
    psd.image_data = image_data.ImageData(
        channels=np.transpose(rgb, (2, 0, 1)),
        compression=enums.Compression.rle,
    )

    with open(path, "wb") as fd:
        psd.write(fd)
    print(f"  {os.path.basename(path)}  ({os.path.getsize(path) / 1024:.0f} KB, "
          f"{len(layers)} layers)")


def build_png_guides(out_dir):
    """PNG overlays for anyone not using a layered editor.

    Place over your artwork as the top layer, check the fit, then delete.
    """
    combined = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for name, fn, opacity, visible in LAYER_SPEC:
        if not visible or not name.startswith("GUIDE"):
            continue
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        fn(ImageDraw.Draw(layer), layer)
        combined = Image.alpha_composite(combined, layer)
    path = os.path.join(out_dir, "pack-guides-overlay-1920x1080.png")
    combined.save(path)
    print(f"  {os.path.basename(path)}  ({os.path.getsize(path) / 1024:.0f} KB)")

    scrim = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_scrim_preview(ImageDraw.Draw(scrim), scrim)
    path = os.path.join(out_dir, "pack-scrim-preview-1920x1080.png")
    scrim.save(path)
    print(f"  {os.path.basename(path)}  ({os.path.getsize(path) / 1024:.0f} KB)")


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    templates = os.path.join(out, "..", "templates")
    os.makedirs(templates, exist_ok=True)
    print("Writing templates:")
    build(os.path.join(templates, "pack-template-1920x1080.psd"), "static")
    build_png_guides(templates)
