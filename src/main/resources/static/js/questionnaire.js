(() => {
	const form = document.getElementById("questionnaire-form");
	const statusEl = document.getElementById("questionnaire-status");
	const submitButton = document.getElementById("questionnaire-submit");

	if (!form || !statusEl || !submitButton) {
		return;
	}

	const setStatus = (message, isError) => {
		statusEl.textContent = message || "";
		statusEl.setAttribute("data-variant", isError ? "error" : "info");
	};

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

	const readFormData = () => {
		const data = new FormData(form);
		const activities = data.getAll("activities").map((value) => value.toString());
		return {
			who: data.get("who")?.toString() || "",
			travel_type: data.get("travel_type")?.toString() || "",
			accommodation: data.get("accommodation")?.toString() || "",
			activities,
			budget: data.get("budget")?.toString() || "",
			weather: data.get("weather")?.toString() || "",
			season: data.get("season")?.toString() || "",
		};
	};

	const isValid = (payload) => {
		return (
			Boolean(payload.who) &&
			Boolean(payload.travel_type) &&
			Boolean(payload.accommodation) &&
			Boolean(payload.budget) &&
			Boolean(payload.weather) &&
			Boolean(payload.season) &&
			Array.isArray(payload.activities) &&
			payload.activities.length > 0
		);
	};

	const setLoading = (isLoading) => {
		form.querySelectorAll("input, button").forEach((el) => {
			el.disabled = isLoading;
		});
		if (!isLoading) {
			updateSubmitState();
		}
	};

	const updateSubmitState = () => {
		const payload = readFormData();
		submitButton.disabled = !isValid(payload);
	};

	form.addEventListener("change", () => {
		updateSubmitState();
	});

	form.addEventListener("submit", async (event) => {
		event.preventDefault();
		const payload = readFormData();
		if (!isValid(payload)) {
			setStatus("Please answer all questions before submitting.", true);
			return;
		}

		setLoading(true);
		setStatus("Generating recommendations...", false);

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
				body: JSON.stringify(payload),
			});

			if (response.status === 401) {
				window.location.href = "/login";
				return;
			}

			if (!response.ok) {
				throw new Error("Service is temporarily unavailable. Please try again later.");
			}

			sessionStorage.setItem(
				"destinai_questionnaire_payload",
				JSON.stringify(payload)
			);
			window.location.href = "/results";
		} catch (error) {
			setStatus(
				error instanceof Error
					? error.message
					: "Service is temporarily unavailable. Please try again later.",
				true
			);
		} finally {
			setLoading(false);
		}
	});

	updateSubmitState();
})();

