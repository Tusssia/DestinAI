package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "travel_type" field to match API contract values.
 */
public enum TravelType {
	BACKPACKING("backpacking"),
	STAYING_IN_ONE_PLACE("staying_in_one_place");

	private final String wireValue;

	TravelType(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static TravelType fromJson(String value) {
		for (TravelType travelType : values()) {
			if (travelType.wireValue.equals(value)) {
				return travelType;
			}
		}
		throw new IllegalArgumentException("Unsupported travel_type value: " + value);
	}
}

