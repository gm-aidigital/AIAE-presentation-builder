package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.slides.v1.Slides;

/**
 * The three Google API clients a single chart build needs, created once per request
 * from a shared request initializer and threaded through the chart-building steps so
 * each method takes one bundle instead of three separate client parameters.
 *
 * @param drive  Drive client used to copy template spreadsheets into the output folder
 * @param sheets Sheets client used to read chart specs and write pivoted chart data
 * @param slides Slides client used to swap placeholder charts on the deck
 */
record ChartClients(Drive drive, Sheets sheets, Slides slides) {
}
