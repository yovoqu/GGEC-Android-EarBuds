# -*- coding: utf-8 -*-
"""GGEC IoT 整合 App 的启动图标：一份几何定义 → 全套资源。

图形只在下面的 BG_* / MARK 里定义一次（108×108 的 adaptive 画布，原点在左上、y 轴向下），
脚本把同一份几何同时输出成矢量 XML 和商店图 PNG，两边不会漂移。产物不要手改：

  app/src/main/res/drawable/ic_launcher_background.xml     adaptive 背景层（渐变矢量）
  app/src/main/res/drawable/ic_launcher_foreground.xml     adaptive 前景层（纯白图形，同时作 Android 13+ 主题单色层）
  app/src/main/res/mipmap-anydpi-v26/ic_launcher(_round).xml   adaptive 入口：背景 + 前景 + 单色层
  app/src/main/ic_launcher-playstore.png                   512×512 商店图
  design/app_icon/ic_launcher.svg                          设计源稿，浏览器/Figma 可直接打开

不生成 mipmap-*dpi 的旧版 PNG：minSdk 是 29，系统一定走 adaptive 矢量（anydpi 优先于任何具体密度），
那批图运行时永远选不中。将来若把 minSdk 降到 26 以下，需要补旧版回退图标。

用法（仓库根目录）：python design/app_icon/build_app_icon.py
依赖：numpy、Pillow。栅格化用有向距离场做解析抗锯齿，不依赖 cairo / ImageMagick。
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

DESIGN = Path(__file__).resolve().parent
ROOT = DESIGN.parents[1]
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"

CANVAS = 108.0   # adaptive 画布边长
CENTER = 54.0    # 画布中心


# ───────────────────────────── 几何原语 ─────────────────────────────

@dataclass(frozen=True)
class Disc:
    """实心圆。"""
    cx: float
    cy: float
    r: float


@dataclass(frozen=True)
class Arc:
    """描边圆弧，两端圆头。角度制：0° 指向右，顺时针为正；sweep 必须小于 360。"""
    cx: float
    cy: float
    r: float
    start: float
    sweep: float
    width: float


def arms(cx, cy, radius, width, node_radius, gap, count=3, start=-90.0):
    """沿轨道均分 count 条"旋臂"：每条是一段圆头弧，从上一个节点身后让出 gap 起笔，
    顺时针一路画进下一个节点的圆心，节点就是这条弧的头，整圈像在朝一个方向流动。

    让出的角度按"圆头圆心到上一个节点圆心的弦长 = 节点半径 + gap + 半线宽"反解，
    所以无论线宽和节点多大，圆头与上一个节点之间的视觉空隙都恒为 gap。
    """
    step = 360.0 / count
    back = math.degrees(2 * math.asin((node_radius + gap + width / 2) / (2 * radius)))
    parts = []
    for k in range(count):
        ang = start + step * k
        rad = math.radians(ang)
        parts.append(Disc(cx + radius * math.cos(rad), cy + radius * math.sin(rad), node_radius))
        parts.append(Arc(cx, cy, radius, ang + back, step - back, width))
    return parts


# ───────────────────────────── 设计定义 ─────────────────────────────

# 背景：左上浅蓝 → 中段品牌蓝（YoColors.BRAND）→ 右下深蓝（YoColors.COLOR_PRIMARY_DARK）。
BG_START = (0.0, 0.0)
BG_END = (CANVAS, CANVAS)
BG_STOPS = ((0.0, "#2F7BFF"), (0.5, "#0A59F7"), (1.0, "#0847C5"))
MARK_COLOR = "#FFFFFF"

# 图形：中心一颗核心圆（枢纽），外圈三条顺时针流动的旋臂，每条的头是一个节点（设备）。
# 层级靠大小说话：枢纽 > 设备 > 连线。全部是圆与圆弧，纯白单色，缩到 36px 仍能认出三个节点。
# 最外沿（节点外缘）距中心 28.1，落在直径 66 的安全圆（半径 33）之内：
# 画布中央 72×72 是任何遮罩形状下都保证可见的区域。
MARK = (
    Disc(CENTER, CENTER, 9.5),
    *arms(CENTER, CENTER, radius=22.5, width=4.0, node_radius=5.6, gap=2.8),
)

# 商店图的取景窗口：比 adaptive 的 72 宽松、比整张 108 紧凑，让图形在 512 里占约 60% 宽。
STORE_WINDOW = 88.0


# ───────────────────────────── 栅格化 ─────────────────────────────

def _sd_disc(x, y, d):
    return np.hypot(x - d.cx, y - d.cy) - d.r


def _sd_arc(x, y, a):
    dx, dy = x - a.cx, y - a.cy
    dist = np.hypot(dx, dy)
    ang = np.degrees(np.arctan2(dy, dx))
    inside = ((ang - a.start) % 360.0) <= a.sweep
    s0, s1 = math.radians(a.start), math.radians(a.start + a.sweep)
    end0 = np.hypot(x - (a.cx + a.r * math.cos(s0)), y - (a.cy + a.r * math.sin(s0)))
    end1 = np.hypot(x - (a.cx + a.r * math.cos(s1)), y - (a.cy + a.r * math.sin(s1)))
    return np.where(inside, np.abs(dist - a.r), np.minimum(end0, end1)) - a.width / 2


def _sd_mark(x, y):
    field = None
    for part in MARK:
        sd = _sd_disc(x, y, part) if isinstance(part, Disc) else _sd_arc(x, y, part)
        field = sd if field is None else np.minimum(field, sd)
    return field


def _hex_rgb(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255.0


def _background(x, y):
    x0, y0 = BG_START
    x1, y1 = BG_END
    vx, vy = x1 - x0, y1 - y0
    t = np.clip(((x - x0) * vx + (y - y0) * vy) / (vx * vx + vy * vy), 0.0, 1.0)
    offsets = [o for o, _ in BG_STOPS]
    colors = np.stack([_hex_rgb(c) for _, c in BG_STOPS])
    return np.stack([np.interp(t, offsets, colors[:, ch]) for ch in range(3)], axis=-1)


def render(size, window):
    """渲染 size×size 的不透明图标，画面是画布中央 window×window 个单位。

    用像素中心采样距离场，覆盖率 = clip(0.5 − 距离/像素边长)，边缘是解析抗锯齿，
    任何尺寸下线宽都精确。末尾加 ±0.5 LSB 的定种子抖动，压住渐变在大图上的 8 位色带。
    """
    upp = window / size
    axis = CENTER + (np.arange(size) + 0.5 - size / 2) * upp
    x, y = np.meshgrid(axis, axis)
    cover = np.clip(0.5 - _sd_mark(x, y) / upp, 0.0, 1.0)[..., None]
    rgb = _background(x, y) * (1 - cover) + _hex_rgb(MARK_COLOR) * cover
    rgb = rgb + (np.random.default_rng(7).random(rgb.shape) - 0.5) / 255.0
    rgba = np.concatenate([np.clip(rgb, 0, 1), np.ones(x.shape + (1,))], axis=-1)
    return Image.fromarray(np.round(rgba * 255).astype(np.uint8), "RGBA")


# ───────────────────────────── 矢量输出 ─────────────────────────────

def _n(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def _disc_path(d):
    return f"M{_n(d.cx - d.r)},{_n(d.cy)}a{_n(d.r)},{_n(d.r)} 0 1,0 {_n(2 * d.r)},0a{_n(d.r)},{_n(d.r)} 0 1,0 {_n(-2 * d.r)},0z"


def _arc_path(a):
    s0, s1 = math.radians(a.start), math.radians(a.start + a.sweep)
    large = 1 if a.sweep > 180.0 else 0
    return (f"M{_n(a.cx + a.r * math.cos(s0))},{_n(a.cy + a.r * math.sin(s0))}"
            f"A{_n(a.r)},{_n(a.r)} 0 {large},1 {_n(a.cx + a.r * math.cos(s1))},{_n(a.cy + a.r * math.sin(s1))}")


def _mark_paths(indent):
    """圆合成一条填充路径，同线宽的圆弧合成一条描边路径，XML 更短也更好读。"""
    pad = " " * indent
    blocks = []
    discs = [p for p in MARK if isinstance(p, Disc)]
    if discs:
        blocks.append(f'{pad}<path\n{pad}    android:fillColor="{MARK_COLOR}"\n'
                      f'{pad}    android:pathData="{"".join(_disc_path(d) for d in discs)}" />')
    for width in sorted({p.width for p in MARK if isinstance(p, Arc)}):
        arcs = [p for p in MARK if isinstance(p, Arc) and p.width == width]
        blocks.append(f'{pad}<path\n{pad}    android:pathData="{"".join(_arc_path(a) for a in arcs)}"\n'
                      f'{pad}    android:strokeColor="{MARK_COLOR}"\n{pad}    android:strokeLineCap="round"\n'
                      f'{pad}    android:strokeWidth="{_n(width)}" />')
    return "\n".join(blocks)


GENERATED = "<!-- 由 design/app_icon/build_app_icon.py 生成，勿手改；改图形请改脚本里的 BG_* / MARK。 -->"


def foreground_xml():
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + GENERATED + "\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + _mark_paths(4) + "\n</vector>\n")


def background_xml():
    stops = "\n".join(f'                <item android:color="{c}" android:offset="{_n(o)}" />' for o, c in BG_STOPS)
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + GENERATED + "\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            '            <gradient\n                android:type="linear"\n'
            f'                android:startX="{_n(BG_START[0])}"\n                android:startY="{_n(BG_START[1])}"\n'
            f'                android:endX="{_n(BG_END[0])}"\n                android:endY="{_n(BG_END[1])}">\n'
            f'{stops}\n'
            '            </gradient>\n'
            '        </aapt:attr>\n'
            '    </path>\n</vector>\n')


def adaptive_xml():
    """adaptive 图标入口：背景 + 前景 + Android 13+ 主题单色层。

    前景只有纯白图形，直接兼作单色层——系统只取它的 alpha 再染成主题色，不需要另画一份。
    圆形图标（roundIcon）与方形共用同一份，由系统按各自的遮罩裁切。
    """
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + GENERATED + "\n"
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n')


def source_svg():
    stops = "\n".join(f'      <stop offset="{_n(o)}" stop-color="{c}"/>' for o, c in BG_STOPS)
    shapes = []
    for part in MARK:
        if isinstance(part, Disc):
            shapes.append(f'  <circle cx="{_n(part.cx)}" cy="{_n(part.cy)}" r="{_n(part.r)}" fill="{MARK_COLOR}"/>')
        else:
            shapes.append(f'  <path d="{_arc_path(part)}" fill="none" stroke="{MARK_COLOR}" '
                          f'stroke-width="{_n(part.width)}" stroke-linecap="round"/>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">\n'
            '  <!-- adaptive 画布：中央 72×72 是任何遮罩下都可见的区域，图形留在直径 66 的安全圆内。 -->\n'
            '  <defs>\n'
            f'    <linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="{_n(BG_START[0])}" y1="{_n(BG_START[1])}" '
            f'x2="{_n(BG_END[0])}" y2="{_n(BG_END[1])}">\n{stops}\n    </linearGradient>\n'
            '  </defs>\n'
            '  <rect width="108" height="108" fill="url(#bg)"/>\n' + "\n".join(shapes) + "\n</svg>\n")


# ───────────────────────────── 生成入口 ─────────────────────────────

def build():
    drawable = RES / "drawable"
    entry = RES / "mipmap-anydpi-v26"
    drawable.mkdir(parents=True, exist_ok=True)
    entry.mkdir(parents=True, exist_ok=True)
    texts = {
        drawable / "ic_launcher_background.xml": background_xml(),
        drawable / "ic_launcher_foreground.xml": foreground_xml(),
        entry / "ic_launcher.xml": adaptive_xml(),
        entry / "ic_launcher_round.xml": adaptive_xml(),
        DESIGN / "ic_launcher.svg": source_svg(),
    }
    for path, content in texts.items():
        path.write_text(content, encoding="utf-8", newline="\n")
    store = MAIN / "ic_launcher-playstore.png"
    render(512, STORE_WINDOW).save(store, optimize=True)
    for path in (*texts, store):
        print(path.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    build()
