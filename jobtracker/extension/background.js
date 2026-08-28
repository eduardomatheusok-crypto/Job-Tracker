const STORAGE_KEYS = {
	token: "jtToken",
	apiUrl: "jtApiUrl",
	user: "jtUser"
};

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
	handle(message)
		.then(sendResponse)
		.catch((error) => sendResponse({ ok: false, message: errorMessage(error) }));
	return true;
});

async function handle(message) {
	switch (message.type) {
		case "status": {
			const stored = await chrome.storage.local.get([
				STORAGE_KEYS.token,
				STORAGE_KEYS.apiUrl,
				STORAGE_KEYS.user
			]);
			return {
				loggedIn: Boolean(stored.jtToken),
				apiUrl: stored.jtApiUrl || "",
				token: stored.jtToken || "",
				userEmail: stored.jtUser && stored.jtUser.email ? stored.jtUser.email : ""
			};
		}

		case "login": {
			const result = await apiFetch(message.apiUrl, "/api/auth/login", {
				method: "POST",
				body: { email: message.email, password: message.password }
			});
			if (!result.ok) {
				return { ok: false, message: describeError(result) };
			}
			await chrome.storage.local.set({
				jtToken: result.data.token,
				jtApiUrl: message.apiUrl,
				jtUser: result.data.user || {}
			});
			return {
				ok: true,
				message: "Conectado!",
				token: result.data.token,
				userEmail: result.data.user && result.data.user.email ? result.data.user.email : ""
			};
		}

		case "logout": {
			await chrome.storage.local.remove([
				STORAGE_KEYS.token,
				STORAGE_KEYS.apiUrl,
				STORAGE_KEYS.user
			]);
			return { ok: true, message: "Desconectado" };
		}

		case "capture": {
			const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
			if (!tab || tab.id === undefined) {
				return { error: "Nenhuma aba aberta" };
			}
			try {
				const response = await chrome.tabs.sendMessage(tab.id, { type: "jt:capture" });
				return response && response.data ? response.data : null;
			} catch (e) {
				return null;
			}
		}

		case "save": {
			const stored = await chrome.storage.local.get([
				STORAGE_KEYS.token,
				STORAGE_KEYS.apiUrl
			]);
			if (!stored.jtToken || !stored.jtApiUrl) {
				return { ok: false, message: "Faça login primeiro." };
			}
			const result = await apiFetch(stored.jtApiUrl, "/api/applications", {
				method: "POST",
				token: stored.jtToken,
				body: message.payload
			});
			if (result.status === 401) {
				return { ok: false, message: "Sessão expirada. Faça login novamente." };
			}
			return result.ok
				? { ok: true, message: "Candidatura salva!" }
				: { ok: false, message: describeError(result) };
		}

		default:
			return { ok: false, message: "Mensagem desconhecida" };
	}
}

async function apiFetch(apiUrl, path, { method = "GET", token, body } = {}) {
	const headers = { "Content-Type": "application/json" };
	if (token) {
		headers["Authorization"] = "Bearer " + token;
	}
	const response = await fetch(apiUrl + path, {
		method,
		headers,
		body: body !== undefined ? JSON.stringify(body) : undefined
	});

	let data = null;
	const text = await response.text();
	if (text) {
		try {
			data = JSON.parse(text);
		} catch (e) {
			data = text;
		}
	}
	return { ok: response.ok, status: response.status, data };
}

function describeError(result) {
	if (!result.data) {
		return "Erro " + result.status;
	}
	if (typeof result.data === "string") {
		return result.data;
	}
	if (result.data.message) {
		return String(result.data.message);
	}
	if (Array.isArray(result.data.errors)) {
		return result.data.errors.map((err) => err.message || String(err)).join("; ");
	}
	if (Array.isArray(result.data)) {
		return result.data.map((err) => err.message || String(err)).join("; ");
	}
	return JSON.stringify(result.data);
}

function errorMessage(error) {
	if (!error) {
		return "Erro inesperado";
	}
	if (error.message) {
		return error.message;
	}
	return String(error);
}