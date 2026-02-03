package com.LiteBansByCMC.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

	private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([dhm])");

	/**
	 * Parses a string like "7d", "5h", "30m" into milliseconds.
	 */
	public static long parseTime(String timeString) {
		if (timeString == null || timeString.isEmpty()) return 0;

		Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
		long totalMillis = 0;

		while (matcher.find()) {
			int value = Integer.parseInt(matcher.group(1));
			String unit = matcher.group(2);

			switch (unit) {
				case "d": totalMillis += value * 24L * 60L * 60L * 1000L; break;
				case "h": totalMillis += value * 60L * 60L * 1000L; break;
				case "m": totalMillis += value * 60L * 1000L; break;
			}
		}
		return totalMillis;
	}
}