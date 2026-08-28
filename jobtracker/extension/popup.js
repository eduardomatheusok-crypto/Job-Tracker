(() => {
	"use strict";

	const $ = (id) => document.getElementById(id);

	const els = {
		loginView: $("loginView"),
		saveView: $("saveView"),
		userEmail: $("userEmail"),
		loginForm: $("loginForm"),
		apiUrl: $("apiUrl"),
		email: $("email"),
		password: $("password"),
		loginMessage: $("loginMessage"),
		saveForm: $("saveForm"),
		companyName: $("companyName"),
		position: $("position"),
		location: $("location"),
		jobUrl: $("jobUrl"),
		platform: $("platform"),
		status: $("status"),
		notes: $("notes"),
		saveMessage: $("saveMessage"),
		logout: $("logout")
	};

	init();

	function init() {
		els.loginForm.addEventListener("submit", onLogin);
		els.saveForm.addEventListener("submit", onSave);
		els.logout.addEventListener("click", onLogout);

		chrome.runtime.sendMessage({ type: "status" }).then((status) => {
			if (status && status.loggedIn) {
				showSaveView(status.userEmail);
			} else {
				showLoginView();
			}
		});
	}

	async function onLogin(event) {
		event.preventDefault();
		setBusy(els.loginForm, true);

		const apiUrl = els.apiUrl.value.trim().replace(/\/+$/, "");
		els.apiUrl.value = apiUrl;

		const result = await chrome.runtime.sendMessage({
			type: "login",
			apiUrl,
			email: els.email.value.trim(),
			password: els.password.value
		});

		setBusy(els.loginForm, false);
		if (result && result.ok) {
			els.loginMessage.textContent = "";
			showSaveView(result.userEmail);
		} else {
			els.loginMessage.textContent = result ? result.message : "Erro ao conectar.";
			els.loginMessage.className = "message error";
		}
	}

	async function onSave(event) {
		event.preventDefault();
		setBusy(els.saveForm, true);

		const payload = {
			companyName: els.companyName.value.trim(),
			position: els.position.value.trim(),
			location: els.location.value.trim(),
			jobUrl: els.jobUrl.value.trim(),
			platform: els.platform.value.trim(),
			notes: els.notes.value.trim(),
			status: els.status.value
		};

		const result = await chrome.runtime.sendMessage({ type: "save", payload });

		setBusy(els.saveForm, false);
		if (result && result.ok) {
			els.saveMessage.textContent = result.message;
			els.saveMessage.className = "message success";
			els.saveForm.reset();
			els.status.value = "SAVED";
		} else {
			els.saveMessage.textContent = result ? result.message : "Erro ao salvar.";
			els.saveMessage.className = "message error";
		}
	}

	async function onLogout() {
		await chrome.runtime.sendMessage({ type: "logout" });
		showLoginView();
	}

	function showLoginView() {
		els.loginView.classList.remove("hidden");
		els.saveView.classList.add("hidden");
		els.loginMessage.textContent = "";
		chrome.storage.local.get(["jtApiUrl"]).then((stored) => {
			if (stored.jtApiUrl && !els.apiUrl.value) {
				els.apiUrl.value = stored.jtApiUrl;
			}
		});
	}

	function showSaveView(userEmail) {
		els.loginView.classList.add("hidden");
		els.saveView.classList.remove("hidden");
		els.userEmail.classList.remove("hidden");
		els.userEmail.textContent = userEmail || "";
		els.saveMessage.textContent = "";
		els.saveMessage.className = "message";

		chrome.runtime.sendMessage({ type: "capture" }).then((data) => {
			if (data && !data.error) {
				fill(data);
			}
		});
	}

	function fill(data) {
		if (data.companyName) {
			els.companyName.value = data.companyName;
		}
		if (data.position) {
			els.position.value = data.position;
		}
		if (data.location) {
			els.location.value = data.location;
		}
		if (data.jobUrl) {
			els.jobUrl.value = data.jobUrl;
		}
		if (data.platform) {
			els.platform.value = data.platform;
		}
	}

	function setBusy(form, busy) {
		const button = form.querySelector("button[type='submit']");
		if (button) {
			button.disabled = busy;
		}
	}
})();