(() => {
	const requestForm = document.getElementById("otp-request-form");
	const verifyForm = document.getElementById("otp-verify-form");
	const statusEl = document.getElementById("login-status");
	const retryLink = document.getElementById("retry-request-link");

	if (!requestForm || !verifyForm || !statusEl || !retryLink) {
		return;
	}

	let currentStep = "request";
	let lastEmail = "";

	const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

	const setStatus = (message, isError) => {
		statusEl.textContent = message || "";
		statusEl.setAttribute("data-variant", isError ? "error" : "info");
	};

	const setStep = (step) => {
		currentStep = step;
		const isRequest = step === "request";
		requestForm.hidden = !isRequest;
		verifyForm.hidden = isRequest;
		if (isRequest) {
			setStatus("", false);
		}
	};

	const setRequestLoading = (isLoading) => {
		requestForm
			.querySelectorAll("input, button")
			.forEach((el) => (el.disabled = isLoading));
	};

	const setVerifyLoading = (isLoading) => {
		verifyForm
			.querySelectorAll("input, button")
			.forEach((el) => (el.disabled = isLoading));
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

	requestForm.addEventListener("submit", async (event) => {
		event.preventDefault();
		const emailInput = requestForm.querySelector('input[name="email"]');
		const email = emailInput ? emailInput.value.trim() : "";

		if (!email || !emailRegex.test(email)) {
			setStatus("Check your input and try again.", true);
			return;
		}

		setRequestLoading(true);
		setStatus("Sending your code...", false);

		const csrf = readCsrfHeader();
		const headers = {
			"Content-Type": "application/json",
		};
		if (csrf?.headerName && csrf.token) {
			headers[csrf.headerName] = csrf.token;
		}

		try {
			const response = await fetch("/api/auth/otp/request", {
				method: "POST",
				credentials: "include",
				headers,
				body: JSON.stringify({ email }),
			});

			if (!response.ok) {
				if (response.status === 429) {
					throw new Error("Please wait before requesting another code.");
				}
				throw new Error("Check your input and try again.");
			}

			lastEmail = email;
			const verifyEmail = verifyForm.querySelector('input[name="email"]');
			if (verifyEmail) {
				verifyEmail.value = lastEmail;
			}
			setStep("verify");
			setStatus("Code sent. Check your email to continue.", false);
		} catch (error) {
			const message =
				error instanceof Error
					? error.message
					: "Service is temporarily unavailable. Please try again later.";
			setStatus(message, true);
		} finally {
			setRequestLoading(false);
		}
	});

	verifyForm.addEventListener("submit", async (event) => {
		event.preventDefault();
		const emailInput = verifyForm.querySelector('input[name="email"]');
		const codeInput = verifyForm.querySelector('input[name="code"]');
		const tokenInput = verifyForm.querySelector('input[name="token"]');
		const email = emailInput ? emailInput.value.trim() : "";
		const code = codeInput ? codeInput.value.trim() : "";
		const token = tokenInput ? tokenInput.value.trim() : "";

		if (!email || !emailRegex.test(email) || (!code && !token)) {
			setStatus("Check your input and try again.", true);
			return;
		}

		setVerifyLoading(true);
		setStatus("Verifying your code...", false);

		const csrf = readCsrfHeader();
		const headers = {
			"Content-Type": "application/json",
		};
		if (csrf?.headerName && csrf.token) {
			headers[csrf.headerName] = csrf.token;
		}

		try {
			const payload = { email };
			if (code) {
				payload.code = code;
			} else {
				payload.token = token;
			}

			const response = await fetch("/api/auth/otp/verify", {
				method: "POST",
				credentials: "include",
				headers,
				body: JSON.stringify(payload),
			});

			if (!response.ok) {
				if (response.status === 401 || response.status === 403) {
					throw new Error("Invalid or expired code.");
				}
				if (response.status === 400) {
					throw new Error("Check your input and try again.");
				}
				throw new Error("Service is temporarily unavailable. Please try again later.");
			}

			window.location.href = "/favorites";
		} catch (error) {
			const message =
				error instanceof Error
					? error.message
					: "Service is temporarily unavailable. Please try again later.";
			setStatus(message, true);
		} finally {
			setVerifyLoading(false);
		}
	});

	retryLink.addEventListener("click", (event) => {
		event.preventDefault();
		setStep("request");
		if (lastEmail) {
			const requestEmail = requestForm.querySelector('input[name="email"]');
			if (requestEmail) {
				requestEmail.value = lastEmail;
			}
		}
	});
})();

