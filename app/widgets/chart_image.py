"""Converts a matplotlib Figure into a Kivy Image widget."""
from __future__ import annotations

from io import BytesIO

import matplotlib.pyplot as plt
from kivy.core.image import Image as CoreImage
from kivy.uix.image import Image as KivyImage


def figure_to_image(fig, **kwargs) -> KivyImage:
    buf = BytesIO()
    fig.savefig(buf, format="png", dpi=130, bbox_inches="tight")
    plt.close(fig)
    buf.seek(0)
    core_image = CoreImage(buf, ext="png")
    kwargs.setdefault("size_hint_y", None)
    kwargs.setdefault("height", "260dp")
    kwargs.setdefault("allow_stretch", True)
    return KivyImage(texture=core_image.texture, **kwargs)
