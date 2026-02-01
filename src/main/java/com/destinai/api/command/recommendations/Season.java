package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "season" field to match API contract values.
 */
public enum Season {
	WINTER("winter"),
	SPRING("spring"),
	SUMMER("summer"),
	AUTUMN("autumn");

	private final String wireValue;

	Season(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static Season fromJson(String value) {
		for (Season season : values()) {
			if (season.wireValue.equals(value)) {
				return season;
			}
		}
		throw new IllegalArgumentException("Unsupported season value: " + value);
	}
}

