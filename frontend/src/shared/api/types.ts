// Handy aliases over the generated OpenAPI component schemas, so feature code
// imports `MappingEntry` instead of components["schemas"]["MappingEntryV1"].
import type { components } from "./generated/schema";

type S = components["schemas"];

export type SheetReadRequest = S["SheetReadRequestV1"];
export type SheetReadResult = S["SheetReadResultV1"];
export type SheetSummaryResult = S["SheetSummaryResultV1"];
export type SheetSummaryRow = S["SheetSummaryRowV1"];
export type LineItemMatchRequest = S["LineItemMatchRequestV1"];
export type LineItemMatchResult = S["LineItemMatchResultV1"];
export type MappingEntry = S["MappingEntryV1"];
export type IdNaming = S["IdNamingV1"];
export type PreviewRequest = S["PreviewRequestV1"];
export type PreviewResult = S["PreviewResultV1"];
export type PlaceholderEntry = S["PlaceholderEntryV1"];
export type PreviewSection = S["SectionV1"];
export type GenerateRequest = S["GenerateRequestV1"];
export type DateRangeRequest = S["DateRangeRequestV1"];
export type DateRangeResult = S["DateRangeResultV1"];
export type DateFilter = S["DateFilterV1"];
export type ReportJob = S["ReportJobV1"];
export type ReportType = S["ReportTypeV1"];
export type User = S["UserV1"];
export type ReportList = S["ReportListV1"];
export type ReportSummary = S["ReportSummaryV1"];
export type AdminStats = S["AdminStatsV1"];
export type AdminTotals = S["AdminTotalsV1"];
export type AdminUserStat = S["AdminUserStatV1"];
export type AdminTypeStat = S["AdminTypeStatV1"];
export type AdminDayVolume = S["AdminDayVolumeV1"];
export type AdminList = S["AdminListV1"];
export type AdminEntry = S["AdminEntryV1"];
export type AddAdminRequest = S["AddAdminRequestV1"];
export type Source = S["SourceV1"];
export type Rows2D = S["Rows2DV1"];
export type GoogleConnectionStatus = S["GoogleConnectionStatusV1"];
