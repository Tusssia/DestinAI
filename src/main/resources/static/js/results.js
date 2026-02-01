(() => {
	const loadingEl = document.getElementById("results-loading");
	const errorEl = document.getElementById("results-error");
	const errorMessageEl = document.getElementById("results-error-message");
	const retryButton = document.getElementById("results-retry");
	const listEl = document.getElementById("results-list");

	if (!loadingEl || !errorEl || !errorMessageEl || !retryButton || !listEl) {
		return;
	}

	const payloadRaw = sessionStorage.getItem("destinai_questionnaire_payload");
	if (!payloadRaw) {
		window.location.href = "/questionnaire";
		return;
	}

	const requestPayload = JSON.parse(payloadRaw);
	const saveStates = new Map();
	const savedCountries = new Set();

	const readCsrfHeader = () => {
		const header = document.querySelector('meta[name="_csrf_header"]');
		const token = document.querySelector('meta[name="_csrf"]');
		if (!header || !token) {
			return null;
		}
		return {
			headerName: header.getAttribute("content"),
			token: token.getAttribute("content"),
		};
	};

	const setStatus = (status, message) => {
		errorMessageEl.textContent = message || "";
		loadingEl.hidden = status !== "loading";
		errorEl.hidden = status !== "error";
		listEl.hidden = status !== "loaded";
	};

	const createList = (items) => {
		listEl.innerHTML = "";
		const container = document.createElement("div");
		container.className = "recommendations-list";
		items.forEach((item) => {
			container.appendChild(createCard(item));
		});
		listEl.appendChild(container);
	};

	const createField = (label, value) => {
		const wrapper = document.createElement("div");
		const dt = document.createElement("dt");
		dt.textContent = label;
		const dd = document.createElement("dd");
		dd.textContent = value || "—";
		wrapper.appendChild(dt);
		wrapper.appendChild(dd);
		return wrapper;
	};

	const createListSection = (label, values) => {
		const wrapper = document.createElement("div");
		const title = document.createElement("p");
		title.textContent = label;
		const list = document.createElement("ul");
		if (Array.isArray(values) && values.length) {
			values.forEach((value) => {
				const li = document.createElement("li");
				li.textContent = value;
				list.appendChild(li);
			});
		} else {
			const li = document.createElement("li");
			li.textContent = "—";
			list.appendChild(li);
		}
		wrapper.appendChild(title);
		wrapper.appendChild(list);
		return wrapper;
	};

	const updateCardState = (country, status, message) => {
		saveStates.set(country, { status, message });
		const card = listEl.querySelector(`[data-country="${CSS.escape(country)}"]`);
		if (!card) {
			return;
		}
		const button = card.querySelector("button");
		const statusEl = card.querySelector(".save-status");
		if (button) {
			button.disabled = status === "saving" || status === "saved";
		}
		if (statusEl) {
			statusEl.textContent = message || "";
		}
	};

	const createCard = (item) => {
		const card = document.createElement("article");
		card.className = "recommendation-card";
		card.dataset.country = item.country;

		const heading = document.createElement("h2");
		heading.textContent = item.country || "—";
		card.appendChild(heading);

		const details = document.createElement("dl");
		details.appendChild(createField("Region", item.region));
		details.appendChild(
			createField("Estimated daily budget", item.estimated_daily_budget_eur_range)
		);
		details.appendChild(
			createField(
				"Best months",
				Array.isArray(item.best_months) ? item.best_months.join(", ") : ""
			)
		);
		details.appendChild(createField("Weather", item.weather_summary));
		details.appendChild(createField("Accommodation fit", item.accommodation_fit));
		details.appendChild(createField("Travel style fit", item.travel_style_fit));
		details.appendChild(createField("Why it matches", item.why_match));
		card.appendChild(details);

		card.appendChild(createListSection("Top activities", item.top_activities));
		card.appendChild(createListSection("Pros", item.pros));
		card.appendChild(createListSection("Cons", item.cons));

		const actions = document.createElement("div");
		const button = document.createElement("button");
		button.type = "button";
		button.textContent = "Save to favorites";
		const status = document.createElement("p");
		status.className = "save-status";
		status.setAttribute("role", "status");
		status.setAttribute("aria-live", "polite");
		actions.appendChild(button);
		actions.appendChild(status);
		card.appendChild(actions);

		button.addEventListener("click", () => {
			saveFavorite(item.country);
		});

		return card;
	};

	const saveFavorite = async (country) => {
		if (!country) {
			return;
		}
		const current = saveStates.get(country);
		if (current?.status === "saving" || current?.status === "saved") {
			return;
		}
		updateCardState(country, "saving", "Saving...");

		const csrf = readCsrfHeader();
		const headers = {
			"Content-Type": "application/json",
		};
		if (csrf?.headerName && csrf.token) {
			headers[csrf.headerName] = csrf.token;
		}

		try {
			const response = await fetch("/api/favorites", {
				method: "POST",
				credentials: "include",
				headers,
				body: JSON.stringify({ country }),
			});

			if (response.status === 401) {
				window.location.href = "/login";
				return;
			}

			if (!response.ok) {
				if (response.status === 400) {
					const errorBody = await response.json().catch(() => null);
					const message = errorBody?.message || "";
					if (message.toLowerCase().includes("limit")) {
						updateCardState(country, "idle", "Favorites limit reached.");
						return;
					}
					if (message.toLowerCase().includes("already")) {
						savedCountries.add(country);
						updateCardState(country, "saved", "Already saved.");
						return;
					}
				}
				updateCardState(country, "idle", "Could not save. Try again.");
				return;
			}

			savedCountries.add(country);
			updateCardState(country, "saved", "Saved.");
		} catch (error) {
			updateCardState(country, "idle", "Could not save. Try again.");
		}
	};

	const fetchRecommendations = async () => {
		setStatus("loading", "");
		const csrf = readCsrfHeader();
		const headers = {
			"Content-Type": "application/json",
		};
		if (csrf?.headerName && csrf.token) {
			headers[csrf.headerName] = csrf.token;
		}

		try {
			const response = await fetch("/api/recommendations", {
				method: "POST",
				credentials: "include",
				headers,
				body: JSON.stringify(requestPayload),
			});

			if (response.status === 401) {
				window.location.href = "/login";
				return;
			}

			if (!response.ok) {
				throw new Error("Service is temporarily unavailable. Please try again later.");
			}

			const data = await response.json();
			if (!data?.destinations || data.destinations.length !== 5) {
				throw new Error("We couldn't load your results. Please try again.");
			}

			createList(data.destinations);
			setStatus("loaded", "");
		} catch (error) {
			setStatus(
				"error",
				error instanceof Error
					? error.message
					: "Service is temporarily unavailable. Please try again later."
			);
		}
	};

	retryButton.addEventListener("click", () => {
		fetchRecommendations();
	});

	fetchRecommendations();
})();

