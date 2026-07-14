"""Academic Analytics + Prediction Engine (PRD section 8).

Uses a lightweight linear-regression predictor (numpy.polyfit) rather than
Random Forest/XGBoost: the PRD lists those as *suggested* models, and a
dependency-light linear trend is more appropriate for an offline, low-data
(a handful of exams per year) mobile app. The interface here is the natural
seam for swapping in a heavier model later if per-child datasets grow large
enough to benefit from one.
"""
from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.repositories.academic_repository import AcademicRepository
from app.repositories.expense_repository import ExpenseRepository


@dataclass
class TrendPoint:
    label: str
    value: float


@dataclass
class Prediction:
    predicted_value: Optional[float]
    confidence: Optional[float]  # 0-1, R^2 of the linear fit
    trend: str  # "improving" | "declining" | "stable" | "insufficient data"
    slope: Optional[float] = None


def _linear_fit(values: List[float]) -> Prediction:
    n = len(values)
    if n < 2:
        return Prediction(
            predicted_value=values[0] if values else None,
            confidence=None,
            trend="insufficient data",
        )
    x = np.arange(n, dtype=float)
    y = np.array(values, dtype=float)
    slope, intercept = np.polyfit(x, y, 1)
    predicted = slope * n + intercept
    y_hat = slope * x + intercept
    ss_res = float(np.sum((y - y_hat) ** 2))
    ss_tot = float(np.sum((y - y.mean()) ** 2))
    r2 = 1 - ss_res / ss_tot if ss_tot > 0 else 0.0
    r2 = max(0.0, min(1.0, r2))
    if abs(slope) < 0.5:
        trend = "stable"
    elif slope > 0:
        trend = "improving"
    else:
        trend = "declining"
    return Prediction(
        predicted_value=round(float(predicted), 2), confidence=round(r2, 2),
        trend=trend, slope=round(float(slope), 3),
    )


class AnalyticsEngine:
    def __init__(
        self,
        academic_repo: Optional[AcademicRepository] = None,
        expense_repo: Optional[ExpenseRepository] = None,
    ):
        self._academic = academic_repo or AcademicRepository()
        self._expense = expense_repo or ExpenseRepository()

    def _exam_groups(self, child_id: int):
        rows = self._academic.get_marks_history(child_id)
        groups: "OrderedDict[Tuple, List]" = OrderedDict()
        for r in rows:
            key = (r["year_label"], r["class_name"], r["term_name"], r["exam_type"])
            groups.setdefault(key, []).append(r)
        return groups

    def percentage_trend(self, child_id: int) -> List[TrendPoint]:
        points = []
        for (year_label, class_name, term_name, exam_type), rows in self._exam_groups(child_id).items():
            percentages = [r["percentage"] for r in rows if r["percentage"] is not None]
            if not percentages:
                continue
            avg = sum(percentages) / len(percentages)
            points.append(TrendPoint(label=f"{year_label} {term_name} {exam_type}", value=round(avg, 2)))
        return points

    def subject_trend(self, child_id: int, subject_name: str) -> List[TrendPoint]:
        points = []
        for (year_label, class_name, term_name, exam_type), rows in self._exam_groups(child_id).items():
            for r in rows:
                if r["subject_name"] == subject_name and r["percentage"] is not None:
                    points.append(
                        TrendPoint(label=f"{year_label} {term_name} {exam_type}", value=round(r["percentage"], 2))
                    )
        return points

    def subject_averages(self, child_id: int) -> Dict[str, float]:
        rows = self._academic.get_marks_history(child_id)
        totals: Dict[str, List[float]] = {}
        for r in rows:
            if r["percentage"] is not None:
                totals.setdefault(r["subject_name"], []).append(r["percentage"])
        return {name: round(sum(vals) / len(vals), 2) for name, vals in totals.items()}

    def subject_list(self, child_id: int) -> List[str]:
        return sorted(self.subject_averages(child_id).keys())

    def predict_overall(self, child_id: int) -> Prediction:
        return _linear_fit([p.value for p in self.percentage_trend(child_id)])

    def predict_subject(self, child_id: int, subject_name: str) -> Prediction:
        return _linear_fit([p.value for p in self.subject_trend(child_id, subject_name)])

    def strengths_and_weaknesses(self, child_id: int, top_n: int = 3):
        averages = self.subject_averages(child_id)
        ranked = sorted(averages.items(), key=lambda kv: kv[1], reverse=True)
        strengths = ranked[:top_n]
        weaknesses = list(reversed(ranked[-top_n:])) if len(ranked) > top_n else []
        return strengths, weaknesses

    def generate_insights(self, child_id: int) -> List[str]:
        insights: List[str] = []
        overall = self.predict_overall(child_id)
        if overall.trend == "improving" and overall.slope:
            insights.append(f"Overall performance improved by roughly {overall.slope:.1f} points per exam.")
        elif overall.trend == "declining" and overall.slope:
            insights.append(
                f"Overall performance is declining by roughly {abs(overall.slope):.1f} points per exam."
            )

        for subject in self.subject_list(child_id):
            pred = self.predict_subject(child_id, subject)
            if pred.trend == "improving" and pred.slope:
                insights.append(f"{subject} scores are trending upward (+{pred.slope:.1f} pts/exam).")
            elif pred.trend == "declining" and pred.slope:
                insights.append(
                    f"{subject} scores are declining ({pred.slope:.1f} pts/exam). Additional support may help."
                )

        strengths, weaknesses = self.strengths_and_weaknesses(child_id)
        if strengths:
            top_subject, top_avg = strengths[0]
            insights.append(f"{top_subject} is a strength area, averaging {top_avg:.0f}%.")
        if weaknesses:
            weak_subject, weak_avg = weaknesses[0]
            insights.append(f"{weak_subject} is the weakest area, averaging {weak_avg:.0f}%.")

        if not insights:
            insights.append("Add more exam records to unlock trend insights.")
        return insights

    def subject_risk_scores(self, child_id: int) -> Dict[str, float]:
        """0 (low risk) - 1 (high risk): combines a declining trend with a low average."""
        risks: Dict[str, float] = {}
        for subject, avg in self.subject_averages(child_id).items():
            pred = self.predict_subject(child_id, subject)
            slope_penalty = max(0.0, -(pred.slope or 0.0)) / 10.0
            low_score_penalty = max(0.0, (60 - avg) / 60.0)
            risks[subject] = round(min(1.0, slope_penalty + low_score_penalty), 2)
        return risks

    # --- Expense forecasting ---
    def expense_yearly_growth(self, child_id: int) -> Prediction:
        rows = self._expense.total_by_year(child_id)
        values = [r["total"] or 0.0 for r in rows if r["year_label"] != "Unassigned"]
        return _linear_fit(values)

    def expense_average_growth_rate(self, child_id: int) -> Optional[float]:
        rows = self._expense.total_by_year(child_id)
        values = [r["total"] or 0.0 for r in rows if r["year_label"] != "Unassigned" and r["total"]]
        if len(values) < 2:
            return None
        rates = [(cur - prev) / prev for prev, cur in zip(values, values[1:]) if prev]
        if not rates:
            return None
        return round(sum(rates) / len(rates) * 100, 1)
