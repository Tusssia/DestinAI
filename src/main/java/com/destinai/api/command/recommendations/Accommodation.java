package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "accommodation" field to match API contract values.
 */
public enum Accommodation {
	CAMPING("camping"),
	HOSTELS("hostels"),
	HOTELS("hotels");

	private final String wireValue;

	Accommodation(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static Accommodation fromJson(String value) {
		for (Accommodation accommodation : values()) {
			if (accommodation.wireValue.equals(value)) {
				return accommodation;
			}
		}
		throw new IllegalArgumentException("Unsupported accommodation value: " + value);
	}
}

