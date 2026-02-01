package com.destinai.api.command.recommendations;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Narrowed enum for the "budget" field to match API contract values.
 */
public enum Budget {
	VERY_LOW("very_low"),
	MEDIUM("medium"),
	LUXURIOUS("luxurious");

	private final String wireValue;

	Budget(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toJson() {
		return wireValue;
	}

	@JsonCreator
	public static Budget fromJson(String value) {
		for (Budget budget : values()) {
			if (budget.wireValue.equals(value)) {
				return budget;
			}
		}
		throw new IllegalArgumentException("Unsupported budget value: " + value);
	}
}

