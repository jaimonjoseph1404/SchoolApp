from kivymd.uix.label import MDLabel
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.screen import MDScreen

from app.services import chart_service
from app.services.analytics_service import AnalyticsEngine
from app.widgets.chart_image import figure_to_image
from app.widgets.child_selector import ChildDropdownField


def _wrapped_label(text: str, **kwargs) -> MDLabel:
    label = MDLabel(text=text, adaptive_height=True, **kwargs)
    label.bind(width=lambda inst, w: setattr(inst, "text_size", (w, None)))
    return label


class AnalyticsScreen(MDScreen):
    def on_kv_post(self, base_widget):
        self._selected_child_id = None
        self._subject_menu = None
        self._subject_chart_widget = None
        self.engine = AnalyticsEngine()

        self.child_field = ChildDropdownField(on_child_selected=self._on_child_selected)
        self.ids.child_field_container.add_widget(self.child_field)

    def _on_child_selected(self, child_id):
        self._selected_child_id = child_id
        self.refresh()

    def open_subject_menu(self):
        if not self._selected_child_id:
            return
        subjects = self.engine.subject_list(self._selected_child_id)
        if not subjects:
            return
        items = [{"text": s, "on_release": lambda s=s: self._select_subject(s)} for s in subjects]
        self._subject_menu = MDDropdownMenu(caller=self.ids.subject_field, items=items, width_mult=4)
        self._subject_menu.open()

    def _select_subject(self, subject):
        self.ids.subject_field.text = subject
        if self._subject_menu:
            self._subject_menu.dismiss()
        self._render_subject_chart(subject)

    def refresh(self):
        self.ids.charts_container.clear_widgets()
        self.ids.insights_container.clear_widgets()
        self.ids.subject_field.text = ""
        self._subject_chart_widget = None
        self.ids.predictions_label.text = ""
        if not self._selected_child_id:
            return

        child_id = self._selected_child_id

        if chart_service.CHARTS_AVAILABLE:
            trend = self.engine.percentage_trend(child_id)
            fig = chart_service.line_chart(
                [p.label for p in trend], [p.value for p in trend], title="Overall Percentage Trend"
            )
            self.ids.charts_container.add_widget(figure_to_image(fig))

            averages = self.engine.subject_averages(child_id)
            fig2 = chart_service.radar_chart(
                list(averages.keys()), list(averages.values()), title="Subject Strengths"
            )
            self.ids.charts_container.add_widget(figure_to_image(fig2, height="320dp"))
        else:
            self.ids.charts_container.add_widget(
                _wrapped_label("Charts aren't available on this build — showing text insights below.")
            )

        overall = self.engine.predict_overall(child_id)
        growth = self.engine.expense_yearly_growth(child_id)
        avg_rate = self.engine.expense_average_growth_rate(child_id)

        lines = []
        if overall.predicted_value is not None:
            confidence = f" (confidence {overall.confidence * 100:.0f}%)" if overall.confidence is not None else ""
            lines.append(f"Expected next exam average: {overall.predicted_value}%{confidence}")
            lines.append(f"Trend: {overall.trend}")
        else:
            lines.append("Add more exams to unlock score predictions.")

        if growth.predicted_value is not None:
            lines.append(f"Projected next year's expenses: Rs. {growth.predicted_value:,.0f}")
        if avg_rate is not None:
            lines.append(f"Average yearly cost growth: {avg_rate}%")

        risk_scores = self.engine.subject_risk_scores(child_id)
        high_risk = [s for s, r in risk_scores.items() if r >= 0.5]
        if high_risk:
            lines.append("Subject risk score — needs attention: " + ", ".join(high_risk))

        self.ids.predictions_label.text = "\n".join(lines)

        for insight in self.engine.generate_insights(child_id):
            self.ids.insights_container.add_widget(_wrapped_label(f"•  {insight}"))

    def _render_subject_chart(self, subject):
        if not chart_service.CHARTS_AVAILABLE:
            return
        if self._subject_chart_widget is not None:
            self.ids.charts_container.remove_widget(self._subject_chart_widget)
            self._subject_chart_widget = None
        trend = self.engine.subject_trend(self._selected_child_id, subject)
        fig = chart_service.line_chart(
            [p.label for p in trend], [p.value for p in trend], title=f"{subject} Trend"
        )
        widget = figure_to_image(fig)
        self.ids.charts_container.add_widget(widget)
        self._subject_chart_widget = widget
