#!/usr/bin/env python3
"""Wire engine beans: replace static calls and Resolved.notFound."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "backend"

NOT_FOUND = 'new Resolved(\\1, null, "not_found")'

def patch_file(path: Path, subs: list[tuple[str, str]], header: str | None = None):
    text = path.read_text()
    if header and "private final SheetUtils" not in text and "private final Fmt fmt" not in text:
        if "@Component" in text and "public class CampaignResolvers" in text:
            pass
    for old, new in subs:
        text = re.sub(old, new, text)
    if header and header not in text:
        text = text.replace(
            "public class CampaignResolvers {",
            header + "\npublic class CampaignResolvers {",
            1,
        )
    path.write_text(text)

def not_found_replace(text: str) -> str:
    return re.sub(
        r'Resolved\.notFound\(([^)]+)\)',
        r'new Resolved(\1, null, "not_found")',
        text,
    )

def main():
    files = list((ROOT / "service/src/main/java/com/aidigital/reportconstructor/service/reports").rglob("*.java"))
    files += list((ROOT / "external-services/src/main/java").rglob("*.java"))

    for path in files:
        text = path.read_text()
        orig = text
        text = not_found_replace(text)
        if path.name == "CampaignResolvers.java":
            if "private final SheetUtils sheetUtils" not in text:
                inj = """
    private final SheetUtils sheetUtils;
    private final Fmt fmt;
    private final TacticUtils tacticUtils;

    public CampaignResolvers(SheetUtils sheetUtils, Fmt fmt, TacticUtils tacticUtils) {
        this.sheetUtils = sheetUtils;
        this.fmt = fmt;
        this.tacticUtils = tacticUtils;
    }
"""
                text = text.replace("public class CampaignResolvers {", "public class CampaignResolvers {" + inj, 1)
            text = text.replace("SheetUtils.", "sheetUtils.")
            text = text.replace("Fmt.", "fmt.")
            text = text.replace("TacticUtils.", "tacticUtils.")
        elif path.name == "TacticResolvers.java":
            if "private final SheetUtils sheetUtils" not in text:
                inj = """
    private final SheetUtils sheetUtils;
    private final Fmt fmt;
    private final TacticUtils tacticUtils;
    private final CampaignResolvers campaignResolvers;

    public TacticResolvers(
            SheetUtils sheetUtils, Fmt fmt, TacticUtils tacticUtils, CampaignResolvers campaignResolvers) {
        this.sheetUtils = sheetUtils;
        this.fmt = fmt;
        this.tacticUtils = tacticUtils;
        this.campaignResolvers = campaignResolvers;
    }
"""
                text = text.replace("public class TacticResolvers {", "public class TacticResolvers {" + inj, 1)
            text = text.replace("SheetUtils.", "sheetUtils.")
            text = text.replace("Fmt.", "fmt.")
            text = text.replace("TacticUtils.", "tacticUtils.")
            text = text.replace("CampaignResolvers.resolve", "campaignResolvers.resolve")
        elif path.name == "CampaignDataCollector.java":
            if "private final SheetUtils sheetUtils" not in text:
                inj = """
    private final SheetUtils sheetUtils;
    private final TacticUtils tacticUtils;
    private final CampaignResolvers campaignResolvers;

    public CampaignDataCollector(
            SheetUtils sheetUtils, TacticUtils tacticUtils, CampaignResolvers campaignResolvers) {
        this.sheetUtils = sheetUtils;
        this.tacticUtils = tacticUtils;
        this.campaignResolvers = campaignResolvers;
    }
"""
                text = text.replace("public class CampaignDataCollector {", "public class CampaignDataCollector {" + inj, 1)
            text = text.replace("SheetUtils.", "sheetUtils.")
            text = text.replace("TacticUtils.", "tacticUtils.")
            text = text.replace(
                "CampaignResolvers.resolveTacticsList",
                "campaignResolvers.resolveTacticsList",
            )
        elif path.name == "ChartPivot.java":
            if "private final SheetUtils sheetUtils" not in text:
                inj = """
    private final SheetUtils sheetUtils;

    public ChartPivot(SheetUtils sheetUtils) {
        this.sheetUtils = sheetUtils;
    }
"""
                text = text.replace("public class ChartPivot {", "public class ChartPivot {" + inj, 1)
            text = text.replace("SheetUtils.parseDate", "sheetUtils.parseDate")
        if text != orig:
            path.write_text(text)
            print("patched", path.relative_to(ROOT))

    # DTOs: remove static empty
    for name in ("ClaudeStrategic.java", "ClaudeTactical.java", "ClaudeResults.java"):
        p = ROOT / "service/src/main/java/com/aidigital/reportconstructor/service/reports/dto" / name
        if not p.exists():
            continue
        text = p.read_text()
        text = re.sub(
            r"\n\s*public static \w+ empty\(\) \{[^}]+\}\n",
            "\n",
            text,
            flags=re.DOTALL,
        )
        p.write_text(text)
        print("dto", name)

if __name__ == "__main__":
    main()
