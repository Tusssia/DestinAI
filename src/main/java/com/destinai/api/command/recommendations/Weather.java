package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "weather" field to match API contract values.
 */
public enum Weather {
	SUNNY_DRY("sunny_dry"),
	SUNNY_HUMID("sunny_humid"),
	COOL("cool"),
	RAINY("rainy");

	private final String wireValue;

	Weather(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static Weather fromJson(String value) {
		for (Weather weather : values()) {
			if (weather.wireValue.equals(value)) {
				return weather;
			}
		}
		throw new IllegalArgumentException("Unsupported weather value: " + value);
	}
}

