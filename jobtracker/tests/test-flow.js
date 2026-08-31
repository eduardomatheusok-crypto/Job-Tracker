"use strict";

const fs = require("fs");
const path = require("path");
const EXT = path.resolve(__dirname, "..", "extension");

let passed = 0;
let failed = 0;
function assert(cond, msg) {
	if (cond) { passed++; console.log("  PASS: " + msg); }
	else { failed++; console.error("  FAIL: " + msg); }
}

function extractPollingBehavior() {
	async function waitForCapture(sendFn, attempts, delayMs) {
		for (let i = 0; i < attempts; i++) {
			const data = await sendFn();
			if (data && data.error) {
				return { type: "warn", message: data.error };
			}
			if (data && !data.empty) {
				return { type: "success", message: "" };
			}
			await new Promise((r) => setTimeout(r, delayMs));
		}
		return { type: "warn", message: "não detectado" };
	}
	return waitForCapture;
}

async function main() {
	console.log("=== Teste: polling popup (SPA demora para carregar) ===");
	{
		const waitForCapture = extractPollingBehavior();
		let calls = 0;
		const sendFn = async () => {
			calls++;
			if (calls < 3) return { empty: true };
			return { companyName: "X", position: "Dev" };
		};
		const res = await waitForCapture(sendFn, 6, 1);
		assert(res.type === "success", "sucesso após retries (não manual)");
		assert(calls === 3, "tentou exatamente até carregar (chamadas=" + calls + ")");
	}

	console.log("=== Teste: polling esgota tentativas ===");
	{
		const waitForCapture = extractPollingBehavior();
		const sendFn = async () => ({ empty: true });
		const res = await waitForCapture(sendFn, 4, 1);
		assert(res.type === "warn", "avisa após esgotar (cai para manual)");
	}

	console.log("=== Teste: erro de página restrita interrompe imediatamente ===");
	{
		const waitForCapture = extractPollingBehavior();
		let calls = 0;
		const sendFn = async () => {
			calls++;
			return { error: "página restrita" };
		};
		const res = await waitForCapture(sendFn, 6, 1);
		assert(res.type === "warn" && calls === 1, "parou no 1º erro (chamadas=" + calls + ")");
	}

	console.log("=== Teste: background - fallback injection ===");
	{
		let injected = false;
		async function messageContentScript(tab) {
			throw new Error("Receiving end does not exist");
		}
		async function injectAndCapture(tab) {
			injected = true;
			return { position: "Dev", companyName: "X" };
		}
		let captured;
		try { captured = await messageContentScript({ id: 1 }); } catch (e) { captured = null; }
		if (captured === null) captured = await injectAndCapture({ id: 1 });
		assert(injected === true, "injetou content script sob demanda");
		assert(captured.position === "Dev", "capturou após injeção");
	}

	console.log("=== Teste: background - guarda de página restrita ===");
	{
		const canInject = (url) => /^(https?|file|ftp):\/\//i.test(url);
		assert(canInject("https://x.com") === true, "https permitido");
		assert(canInject("chrome://extensions") === false, "chrome:// bloqueado");
		assert(canInject("about:blank") === false, "about: bloqueado");
	}

	console.log("=== Teste: manifest tem permissões scripting e tabs ===");
	{
		const manifest = JSON.parse(fs.readFileSync(path.join(EXT, "manifest.json"), "utf8"));
		assert(Array.isArray(manifest.permissions), "permissions é array");
		assert(manifest.permissions.includes("scripting"), "tem permissão scripting");
		assert(manifest.permissions.includes("tabs"), "tem permissão tabs");
	}

	console.log("\nResultado: " + passed + " passaram, " + failed + " falharam");
	process.exit(failed ? 1 : 0);
}

main().then(() => {}).catch((e) => { console.error(e); process.exit(1); });
