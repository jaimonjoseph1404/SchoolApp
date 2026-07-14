"""Renders analytics data as matplotlib figures for the Analytics module (PRD section 8).

Figures are rasterized to PNG and shown in a plain kivy Image widget (see
app/widgets/chart_image.py) rather than embedded via kivy_garden.matplotlib,
which doesn't import on Python 3.12+ (it depends on the removed stdlib
`distutils`). Static PNGs are simpler and sufficient since these charts are
not interactive.

matplotlib is imported lazily and guarded: it has no python-for-android
recipe track record as solid as numpy's, and isn't in the Android build's
requirements (see buildozer.spec) until that's verified in a real build.
CHARTS_AVAILABLE lets the Analytics screen degrade to text-only insights
instead of crashing when it isn't present.
"""
from __future__ import annotations

from typing import Dict, List, Sequence

TEAL = "#00796B"
AMBER = "#FFA000"

try:
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    CHARTS_AVAILABLE = True
except Exception:
    plt = None
    CHARTS_AVAILABLE = False


def line_chart(labels: Sequence[str], values: Sequence[float], title: str = "", ylabel: str = "%"):
    fig, ax = plt.subplots(figsize=(6, 3))
    if values:
        ax.plot(range(len(values)), values, marker="o", color=TEAL)
        ax.set_xticks(range(len(labels)))
        ax.set_xticklabels(labels, rotation=30, ha="right", fontsize=7)
    else:
        ax.text(0.5, 0.5, "No data yet", ha="center", va="center", transform=ax.transAxes)
    ax.set_title(title, fontsize=10)
    ax.set_ylabel(ylabel, fontsize=8)
    ax.grid(alpha=0.3)
    fig.tight_layout()
    return fig


def bar_chart(labels: Sequence[str], values: Sequence[float], title: str = "", ylabel: str = ""):
    fig, ax = plt.subplots(figsize=(6, 3))
    if values:
        ax.bar(labels, values, color=TEAL)
        ax.tick_params(axis="x", labelrotation=30, labelsize=7)
    else:
        ax.text(0.5, 0.5, "No data yet", ha="center", va="center", transform=ax.transAxes)
    ax.set_title(title, fontsize=10)
    ax.set_ylabel(ylabel, fontsize=8)
    ax.grid(alpha=0.3, axis="y")
    fig.tight_layout()
    return fig


def radar_chart(categories: Sequence[str], values: Sequence[float], title: str = "Subject Strengths"):
    fig = plt.figure(figsize=(4.5, 4.5))
    if not categories:
        ax = fig.add_subplot(111)
        ax.text(0.5, 0.5, "No data yet", ha="center", va="center", transform=ax.transAxes)
        ax.set_title(title, fontsize=10)
        fig.tight_layout()
        return fig

    n = len(categories)
    angles = [i / n * 2 * 3.14159265 for i in range(n)]
    angles += angles[:1]
    plot_values = list(values) + [values[0]]

    ax = fig.add_subplot(111, polar=True)
    ax.plot(angles, plot_values, color=TEAL, linewidth=2)
    ax.fill(angles, plot_values, color=TEAL, alpha=0.25)
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, fontsize=8)
    ax.set_ylim(0, 100)
    ax.set_title(title, fontsize=10)
    fig.tight_layout()
    return fig
