#!/usr/bin/env bash
#
# new-template-env.sh — points the WORKSPACE backend at the new deck template.
#
# Sourced (never executed) by replit-dev-backend.sh only. Deliberately not
# sourced by replit-env.sh: that file is also read by replit-build.sh, and
# keeping the opt-in out of the build path makes it impossible for these values
# to leak into a published release by accident. The published app boots through
# replit-run.sh, which sources lib/deploy-env.sh and nothing else, so it keeps
# the application.yml defaults — the old template — until the new one is signed
# off.
#
# Every assignment is `${VAR:-…}`, so a value set in Replit Secrets or
# Configurations still wins over the one here.
#
# Ids read from a presentations.get dump of the new template on 2026-08-17; the
# inventory and the reasoning behind each one live in
# .claude/tasks/new-template-migration/new-template-env.md.
#
# TO SHIP THE NEW TEMPLATE TO PRODUCTION: move these values into the
# application.yml defaults (they are already env-overridable there) and delete
# this file along with the line that sources it. Do not extend it to
# replit-run.sh — the workspace/production split is the point.

# The deck itself, and the one generic tactic slide duplicated per tactic.
export SLIDES_TEMPLATE_ID="${SLIDES_TEMPLATE_ID:-15ryPctwlmTy2uCpjrVjR1kVJErnpvQ9wXnF7IAka-OM}"
export TACTIC_MASTER_SLIDE_OBJECT_ID="${TACTIC_MASTER_SLIDE_OBJECT_ID:-p8}"

# One chart-source workbook per type (was 28 per type), each with its own
# in-sheet chart id. The workbook id doubles as the key that finds that chart on
# a duplicated tactic slide, so the three must stay three distinct files.
export DAILY_CHART_TEMPLATE_SHEET_ID="${DAILY_CHART_TEMPLATE_SHEET_ID:-1LjtiI83T_0-v64CsoANTIjDzinipBk5ELe7NVzO5r2A}"
export MONTHLY_CHART_TEMPLATE_SHEET_ID="${MONTHLY_CHART_TEMPLATE_SHEET_ID:-1r4aI3ToKfZ7W_TfVNokdShp91mXFIiA2eLy8PmTx4oI}"
export DIST_CHART_TEMPLATE_SHEET_ID="${DIST_CHART_TEMPLATE_SHEET_ID:-1fzG6Uuu2U1E3w0lnXGKO0TERYb59LkmSNSBgVR9DPtw}"
export DAILY_CHART_ID_IN_SHEET="${DAILY_CHART_ID_IN_SHEET:-510717191}"
export MONTHLY_CHART_ID_IN_SHEET="${MONTHLY_CHART_ID_IN_SHEET:-510717191}"
export DIST_CHART_ID_IN_SHEET="${DIST_CHART_ID_IN_SHEET:-1431807138}"

# Master slides duplicated per tactic for the Step-3 breakdown sections.
export BREAKDOWN_MASTER_SLIDE_TP="${BREAKDOWN_MASTER_SLIDE_TP:-p9}"
export BREAKDOWN_MASTER_SLIDE_CA="${BREAKDOWN_MASTER_SLIDE_CA:-p10}"
export BREAKDOWN_MASTER_SLIDE_GEO="${BREAKDOWN_MASTER_SLIDE_GEO:-p13}"
export BREAKDOWN_MASTER_SLIDE_AUD="${BREAKDOWN_MASTER_SLIDE_AUD:-p11}"
export BREAKDOWN_MASTER_SLIDE_DEV="${BREAKDOWN_MASTER_SLIDE_DEV:-p14}"
export THOUGHTS_MASTER_SLIDE_OBJECT_ID="${THOUGHTS_MASTER_SLIDE_OBJECT_ID:-g3f6fd96d9b2_1_0}"

# Platforms group slides and their 7-row tables, deleted / row-trimmed by the
# tactic trim when a group has no tactics left.
export RESULTS_SLIDE_OBJECT_ID_1="${RESULTS_SLIDE_OBJECT_ID_1:-p7}"
export RESULTS_SLIDE_OBJECT_ID_2="${RESULTS_SLIDE_OBJECT_ID_2:-g3f6fd96d9b2_1_139}"
export RESULTS_SLIDE_OBJECT_ID_3="${RESULTS_SLIDE_OBJECT_ID_3:-g3f6fd96d9b2_1_153}"
export RESULTS_SLIDE_OBJECT_ID_4="${RESULTS_SLIDE_OBJECT_ID_4:-g3f6fd96d9b2_1_167}"
export SUMMARY_TABLE_OBJECT_ID_1="${SUMMARY_TABLE_OBJECT_ID_1:-p7_i467}"
export SUMMARY_TABLE_OBJECT_ID_2="${SUMMARY_TABLE_OBJECT_ID_2:-g3f6fd96d9b2_1_143}"
export SUMMARY_TABLE_OBJECT_ID_3="${SUMMARY_TABLE_OBJECT_ID_3:-g3f6fd96d9b2_1_157}"
export SUMMARY_TABLE_OBJECT_ID_4="${SUMMARY_TABLE_OBJECT_ID_4:-g3f6fd96d9b2_1_171}"

# Slides an EOM deck must never ship: the frequency play and the awareness /
# market-share slide.
export EOM_DROP_SLIDE_OBJECT_IDS="${EOM_DROP_SLIDE_OBJECT_IDS:-p17,p6}"

# Per-tactic breakdown charts, keyed by series. The audience slide carries two
# charts, so aud (age) and aud-seg (top segments) each get their own workbook.
export BREAKDOWN_AUD_SOURCE_SHEET_ID="${BREAKDOWN_AUD_SOURCE_SHEET_ID:-1K4XeQcIngAoYckwim7DWpA14pWC4ssuSypuUccA8kQc}"
export BREAKDOWN_AUD_CHART_ID="${BREAKDOWN_AUD_CHART_ID:-522266257}"
export BREAKDOWN_AUD_SEG_SOURCE_SHEET_ID="${BREAKDOWN_AUD_SEG_SOURCE_SHEET_ID:-1PAurz6x7NC_35A1HKllbhSgfk5wvncN_oRnFraGy2r8}"
export BREAKDOWN_AUD_SEG_CHART_ID="${BREAKDOWN_AUD_SEG_CHART_ID:-522266257}"
export BREAKDOWN_DEV_SOURCE_SHEET_ID="${BREAKDOWN_DEV_SOURCE_SHEET_ID:-1kDdh68zWxrujlW0dbhsNmLS5xu9jT3KbfjtbvbRBVvc}"
export BREAKDOWN_DEV_CHART_ID="${BREAKDOWN_DEV_CHART_ID:-522266257}"

echo "[new-template-env] workspace backend pinned to the new deck template ${SLIDES_TEMPLATE_ID}"
