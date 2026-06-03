package com.aidigital.reportconstructor.externalservices.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Google chart helper spreadsheet and slide object ids — bound from
 * {@code external.google.charts.*}. Defaults are empty; configure via env/yaml
 * (see {@code application-local.yml} for legacy POC ids).
 */
@Component
@ConfigurationProperties(prefix = "external.google.charts")
public class ChartTemplateCatalog {

    private int chartIdInSheet;
    private Map<Integer, String> dailyTemplateSheetIds = Map.of();
    private Map<Integer, String> dailySlideObjectIds = Map.of();
    private Map<Integer, String> monthlyTemplateSheetIds = Map.of();
    private Map<Integer, String> monthlySlideObjectIds = Map.of();
    private Map<Integer, String> distTemplateSheetIds = Map.of();
    private Map<Integer, String> distSlideObjectIds = Map.of();
    private List<Rgb> pieDefaultColors = List.of();

    public int getChartIdInSheet() {
        return chartIdInSheet;
    }

    public void setChartIdInSheet(int chartIdInSheet) {
        this.chartIdInSheet = chartIdInSheet;
    }

    public Map<Integer, String> getDailyTemplateSheetIds() {
        return dailyTemplateSheetIds;
    }

    public void setDailyTemplateSheetIds(Map<Integer, String> dailyTemplateSheetIds) {
        this.dailyTemplateSheetIds = dailyTemplateSheetIds == null ? Map.of() : dailyTemplateSheetIds;
    }

    public Map<Integer, String> getDailySlideObjectIds() {
        return dailySlideObjectIds;
    }

    public void setDailySlideObjectIds(Map<Integer, String> dailySlideObjectIds) {
        this.dailySlideObjectIds = dailySlideObjectIds == null ? Map.of() : dailySlideObjectIds;
    }

    public Map<Integer, String> getMonthlyTemplateSheetIds() {
        return monthlyTemplateSheetIds;
    }

    public void setMonthlyTemplateSheetIds(Map<Integer, String> monthlyTemplateSheetIds) {
        this.monthlyTemplateSheetIds = monthlyTemplateSheetIds == null ? Map.of() : monthlyTemplateSheetIds;
    }

    public Map<Integer, String> getMonthlySlideObjectIds() {
        return monthlySlideObjectIds;
    }

    public void setMonthlySlideObjectIds(Map<Integer, String> monthlySlideObjectIds) {
        this.monthlySlideObjectIds = monthlySlideObjectIds == null ? Map.of() : monthlySlideObjectIds;
    }

    public Map<Integer, String> getDistTemplateSheetIds() {
        return distTemplateSheetIds;
    }

    public void setDistTemplateSheetIds(Map<Integer, String> distTemplateSheetIds) {
        this.distTemplateSheetIds = distTemplateSheetIds == null ? Map.of() : distTemplateSheetIds;
    }

    public Map<Integer, String> getDistSlideObjectIds() {
        return distSlideObjectIds;
    }

    public void setDistSlideObjectIds(Map<Integer, String> distSlideObjectIds) {
        this.distSlideObjectIds = distSlideObjectIds == null ? Map.of() : distSlideObjectIds;
    }

    public List<Rgb> getPieDefaultColors() {
        return pieDefaultColors;
    }

    public void setPieDefaultColors(List<Rgb> pieDefaultColors) {
        this.pieDefaultColors = pieDefaultColors == null ? List.of() : pieDefaultColors;
    }

    /** Forced pie palette when the template has no slice colors (Teal / Orange). */
    public double[][] pieDefaultColorMatrix() {
        if (pieDefaultColors.isEmpty()) {
            return new double[][] {
                {0.173, 0.490, 0.502},
                {0.937, 0.490, 0.133}
            };
        }
        double[][] out = new double[pieDefaultColors.size()][];
        for (int i = 0; i < pieDefaultColors.size(); i++) {
            Rgb c = pieDefaultColors.get(i);
            out[i] = new double[] {c.getRed(), c.getGreen(), c.getBlue()};
        }
        return out;
    }

    public static class Rgb {
        private double red;
        private double green;
        private double blue;

        public double getRed() {
            return red;
        }

        public void setRed(double red) {
            this.red = red;
        }

        public double getGreen() {
            return green;
        }

        public void setGreen(double green) {
            this.green = green;
        }

        public double getBlue() {
            return blue;
        }

        public void setBlue(double blue) {
            this.blue = blue;
        }
    }
}
