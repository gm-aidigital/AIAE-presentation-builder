package com.aidigital.reportconstructor.externalservices.google;

/**
 * A single pie palette color expressed as normalized RGB components (each in the 0.0–1.0 range),
 * matching the channel scale expected by the Google Sheets/Slides chart APIs.
 */
public class RgbColor {

	private double red;
	private double green;
	private double blue;

	/**
	 * Returns the normalized red component (0.0–1.0) of this color.
	 *
	 * @return the red channel value in the 0.0–1.0 range
	 */
	public double getRed() {
		return red;
	}

	/**
	 * Sets the normalized red component (0.0–1.0) of this color.
	 *
	 * @param red the red channel value in the 0.0–1.0 range
	 */
	public void setRed(double red) {
		this.red = red;
	}

	/**
	 * Returns the normalized green component (0.0–1.0) of this color.
	 *
	 * @return the green channel value in the 0.0–1.0 range
	 */
	public double getGreen() {
		return green;
	}

	/**
	 * Sets the normalized green component (0.0–1.0) of this color.
	 *
	 * @param green the green channel value in the 0.0–1.0 range
	 */
	public void setGreen(double green) {
		this.green = green;
	}

	/**
	 * Returns the normalized blue component (0.0–1.0) of this color.
	 *
	 * @return the blue channel value in the 0.0–1.0 range
	 */
	public double getBlue() {
		return blue;
	}

	/**
	 * Sets the normalized blue component (0.0–1.0) of this color.
	 *
	 * @param blue the blue channel value in the 0.0–1.0 range
	 */
	public void setBlue(double blue) {
		this.blue = blue;
	}
}
