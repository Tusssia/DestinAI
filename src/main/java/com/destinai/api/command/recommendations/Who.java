package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "who" field to match API contract values.
 */
public enum Who {
	SOLO("solo"),
	COUPLE("couple");

	private final String wireValue;

	Who(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static Who fromJson(String value) {
		for (Who who : values()) {
			if (who.wireValue.equals(value)) {
				return who;
			}
		}
		throw new IllegalArgumentException("Unsupported who value: " + value);
	}
}

