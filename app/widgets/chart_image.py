"""Converts a matplotlib Figure into a Kivy Image widget.

Only call this when app.services.chart_service.CHARTS_AVAILABLE is True —
matplotlib is an optional dependency (see chart_service for why).
"""
from __future__ import annotations

from io import BytesIO

from kivy.core.image import Image as CoreImage
from kivy.uix.image import Image as KivyImage


def figure_to_image(fig, **kwargs) -> KivyImage:
    import matplotlib.pyplot as plt

    buf = BytesIO()
    fig.savefig(buf, format="png", dpi=130, bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    core_image = CoreImage(buf, ext="png")
    kwargs.setdefault("size_hint_y", None)
    kwargs.setdefault("height", "260dp")
    kwargs.setdefault("allow_stretch", True)
    return KivyImage(texture=core_image.texture, **kwargs)
