(() => {
	const listEl = document.getElementById("favorites-list");
	const statusEl = document.getElementById("favorites-status");
	const emptyEl = document.getElementById("favorites-empty");
	const searchForm = document.getElementById("favorites-search-form");
	const searchInput = document.getElementById("favorites-search");
	const sortSelect = document.getElementById("favorites-sort");
	const prevButton = document.getElementById("favorites-prev");
	const nextButton = document.getElementById("favorites-next");
	const pageInfo = document.getElementById("favorites-page-info");

	if (
		!listEl ||
		!statusEl ||
		!emptyEl ||
		!searchForm ||
		!searchInput ||
		!sortSelect ||
		!prevButton ||
		!nextButton ||
		!pageInfo
	) {
		return;
	}

	const query = {
		page: 1,
		pageSize: 10,
		sort: "created_at_desc",
		country: "",
	};

	const inFlight = new Set();

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

	const setStatus = (message, isError) => {
		statusEl.textContent = message || "";
		statusEl.setAttribute("data-variant", isError ? "error" : "info");
	};

	const buildUrl = () => {
		const params = new URLSearchParams();
		params.set("page", String(query.page));
		params.set("page_size", String(query.pageSize));
		params.set("sort", query.sort);
		if (query.country) {
			params.set("country", query.country);
		}
		return `/api/favorites?${params.toString()}`;
	};

	const formatDate = (value) => {
		if (!value) {
			return "—";
		}
		const date = new Date(value);
		if (Number.isNaN(date.getTime())) {
			return "—";
		}
		return date.toLocaleDateString();
	};

	const renderList = (items, pageInfoData) => {
		listEl.innerHTML = "";
		if (!items.length) {
			emptyEl.hidden = false;
			pageInfo.textContent = "";
			prevButton.disabled = true;
			nextButton.disabled = true;
			return;
		}
		emptyEl.hidden = true;
		const container = document.createElement("div");
		container.className = "favorites-list";
		items.forEach((item) => {
			container.appendChild(createItem(item));
		});
		listEl.appendChild(container);
		pageInfo.textContent = `Page ${pageInfoData.page} of ${pageInfoData.totalPages}`;
		prevButton.disabled = pageInfoData.page <= 1;
		nextButton.disabled = pageInfoData.page >= pageInfoData.totalPages;
	};

	const createItem = (item) => {
		const article = document.createElement("article");
		article.className = "favorite-item";
		article.dataset.id = item.id;

		const heading = document.createElement("h2");
		heading.textContent = item.country || "—";
		article.appendChild(heading);

		const time = document.createElement("time");
		time.textContent = `Saved on ${formatDate(item.created_at)}`;
		article.appendChild(time);

		const noteLabel = document.createElement("label");
		noteLabel.textContent = "Note";
		const noteTextarea = document.createElement("textarea");
		noteTextarea.value = item.note || "";
		noteTextarea.maxLength = 100;
		noteLabel.appendChild(noteTextarea);
		article.appendChild(noteLabel);

		const actions = document.createElement("div");
		const saveButton = document.createElement("button");
		saveButton.type = "button";
		saveButton.textContent = "Save note";
		const deleteButton = document.createElement("button");
		deleteButton.type = "button";
		deleteButton.textContent = "Delete";
		const status = document.createElement("p");
		status.className = "favorite-status";
		status.setAttribute("role", "status");
		status.setAttribute("aria-live", "polite");
		actions.appendChild(saveButton);
		actions.appendChild(deleteButton);
		actions.appendChild(status);
		article.appendChild(actions);

		const setItemLoading = (isLoading) => {
			noteTextarea.disabled = isLoading;
			saveButton.disabled = isLoading;
			deleteButton.disabled = isLoading;
		};

		const handleSave = async () => {
			if (inFlight.has(item.id)) {
				return;
			}
			inFlight.add(item.id);
			setItemLoading(true);
			status.textContent = "Saving...";

			const csrf = readCsrfHeader();
			const headers = {
				"Content-Type": "application/json",
			};
			if (csrf?.headerName && csrf.token) {
				headers[csrf.headerName] = csrf.token;
			}

			try {
				const response = await fetch(`/api/favorites/${item.id}`, {
					method: "PATCH",
					credentials: "include",
					headers,
					body: JSON.stringify({ note: noteTextarea.value.trim() }),
				});

				if (response.status === 401) {
					window.location.href = "/login";
					return;
				}

				if (!response.ok) {
					throw new Error("Could not save note.");
				}

				status.textContent = "Saved.";
			} catch (error) {
				status.textContent = "Could not save note.";
			} finally {
				inFlight.delete(item.id);
				setItemLoading(false);
			}
		};

		const handleDelete = async () => {
			if (inFlight.has(item.id)) {
				return;
			}
			const confirmed = window.confirm("Delete this favorite?");
			if (!confirmed) {
				return;
			}
			inFlight.add(item.id);
			setItemLoading(true);
			status.textContent = "Deleting...";

			const csrf = readCsrfHeader();
			const headers = {};
			if (csrf?.headerName && csrf.token) {
				headers[csrf.headerName] = csrf.token;
			}

			try {
				const response = await fetch(`/api/favorites/${item.id}`, {
					method: "DELETE",
					credentials: "include",
					headers,
				});

				if (response.status === 401) {
					window.location.href = "/login";
					return;
				}

				if (!response.ok) {
					throw new Error("Could not delete favorite.");
				}

				await loadFavorites();
			} catch (error) {
				status.textContent = "Could not delete favorite.";
			} finally {
				inFlight.delete(item.id);
				setItemLoading(false);
			}
		};

		saveButton.addEventListener("click", handleSave);
		deleteButton.addEventListener("click", handleDelete);

		return article;
	};

	const loadFavorites = async () => {
		setStatus("Loading favorites...", false);
		try {
			const response = await fetch(buildUrl(), {
				method: "GET",
				credentials: "include",
			});

			if (response.status === 401) {
				window.location.href = "/login";
				return;
			}

			if (!response.ok) {
				throw new Error("Service is temporarily unavailable. Please try again later.");
			}

			const data = await response.json();
			const totalPages = Math.max(
				1,
				Math.ceil((data.total || 0) / (data.page_size || query.pageSize))
			);
			renderList(data.items || [], {
				page: data.page || query.page,
				totalPages,
			});
			setStatus("", false);
		} catch (error) {
			setStatus(
				error instanceof Error
					? error.message
					: "Service is temporarily unavailable. Please try again later.",
				true
			);
		}
	};

	searchForm.addEventListener("submit", (event) => {
		event.preventDefault();
		query.country = searchInput.value.trim();
		query.page = 1;
		loadFavorites();
	});

	sortSelect.addEventListener("change", () => {
		query.sort = sortSelect.value;
		query.page = 1;
		loadFavorites();
	});

	prevButton.addEventListener("click", () => {
		if (query.page > 1) {
			query.page -= 1;
			loadFavorites();
		}
	});

	nextButton.addEventListener("click", () => {
		query.page += 1;
		loadFavorites();
	});

	loadFavorites();

	// Logout functionality
	const logoutButton = document.getElementById("logout-button");
	if (logoutButton) {
		logoutButton.addEventListener("click", async () => {
			const csrf = readCsrfHeader();
			const headers = {
				"Content-Type": "application/json",
			};
			if (csrf?.headerName && csrf.token) {
				headers[csrf.headerName] = csrf.token;
			}

			try {
				await fetch("/api/auth/logout", {
					method: "POST",
					credentials: "include",
					headers,
				});
			} catch (error) {
				// Ignore errors, still redirect
			} finally {
				window.location.href = "/login";
			}
		});
	}
})();

