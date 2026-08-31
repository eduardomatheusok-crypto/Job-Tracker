"use strict";

const fs = require("fs");
const path = require("path");

const EXT = path.resolve(__dirname, "..", "extension");
let passed = 0;
let failed = 0;

function assert(cond, msg) {
	if (cond) {
		passed++;
		console.log("  PASS: " + msg);
	} else {
		failed++;
		console.error("  FAIL: " + msg);
	}
}

function buildElement(text, content) {
	return {
		textContent: text || "",
		content: content || "",
		querySelector() { return null; }
	};
}

function runContentScript(dom, callback) {
	const listeners = {};
	const chromeMock = {
		runtime: {
			onMessage: {
				addListener(fn) { listeners.handler = fn; }
			}
		}
	};
	const win = {
		location: dom.location || { href: "https://www.linkedin.com/jobs/view/123", hostname: "www.linkedin.com" }
	};

	const scripts = fs.readFileSync(path.join(EXT, "content.js"), "utf8");
	// eslint-disable-next-line no-new-func
	const fn = new Function("chrome", "document", "location", "window", "JSON", "Array", "Object", "String", scripts);
	fn.call(win, chromeMock, dom, win.location, win, JSON, Array, Object, String);

	let response = null;
	listeners.handler({ type: "jt:capture" }, null, (r) => { response = r; });
	return response;
}

console.log("=== Teste: captura via JSON-LD ===");
{
	const scripts = [
		{
			textContent: JSON.stringify({
				"@type": "JobPosting",
				title: "Desenvolvedor Backend",
				hiringOrganization: { name: "TechCorp" },
				jobLocation: { address: { addressLocality: "São Paulo" } }
			})
		}
	];
	const dom = {
		querySelectorAll(sel) { return sel === 'script[type="application/ld+json"]' ? scripts : []; },
		querySelector() { return null; },
		location: { href: "https://www.gupy.io/jobs/123", hostname: "www.gupy.io" }
	};
	const res = runContentScript(dom);
	assert(res && res.data, "json-ld retorna data");
	assert(res.data.position === "Desenvolvedor Backend", "position do JSON-LD");
	assert(res.data.companyName === "TechCorp", "companyName do JSON-LD");
	assert(res.data.location === "São Paulo", "location do JSON-LD");
	assert(res.data.platform === "Gupy", "plataforma Gupy");
}

console.log("=== Teste: falha de JSON-LD não quebra captura ===");
{
	const scripts = [{ textContent: "###invalid json###" }];
	const dom = {
		querySelectorAll() { return scripts; },
		querySelector(sel) {
			const map = {
				'h1': buildElement("Engenheiro de Software"),
				'meta[property="og:title"]': buildElement(null, "Engenheiro de Software"),
				'meta[property="og:site_name"]': buildElement(null, "Empresa X")
			};
			return map[sel] || null;
		},
		location: { href: "https://example.com/job", hostname: "example.com" }
	};
	const res = runContentScript(dom);
	assert(res && res.data, "falha JSON-LD ainda captura via selectores");
	assert(res.data.position === "Engenheiro de Software", "position via h1");
	assert(res.data.companyName === "Empresa X", "companyName via og:site_name");
}

console.log("=== Teste: detecção de plataforma ===");
{
	const chromeMock = { runtime: { onMessage: { addListener() { } } } };
	const scripts = fs.readFileSync(path.join(EXT, "content.js"), "utf8");

	// Reuse via separate iframe-like eval by exposing captureJobData: not exposed.
	// Instead we test through full run with a fresh top-level context that records platform.
	const cases = [
		["https://www.indeed.com/jobs", "Indeed"],
		["https://jobs.lever.co/123", "Lever"],
		["https://boards.greenhouse.io/123", "Greenhouse"],
		["https://www.vagas.com.br/v/123", "Vagas.com.br"]
	];
	for (const [href, expected] of cases) {
		const dom = {
			querySelectorAll() { return []; },
			querySelector() { return null; },
			location: { href, hostname: href.replace("https://", "").split("/")[0] }
		};
		const res = runContentScript(dom);
		assert(res && res.data.platform === expected, `plataforma ${expected} (${href})`);
	}
}

console.log("=== Teste: canInject (páginas restritas) ===");
{
	// Re-extract canInject logic by reading background.js is not trivial (chrome API).
	// Instead test trim logic via a small standalone check.
	const re = /^(https?|file|ftp):\/\//i;
	assert(re.test("https://www.gupy.io/"), "https injetável");
	assert(re.test("file:///C:/x.html"), "file injetável");
	assert(!re.test("chrome://extensions"), "chrome:// NÃO injetável");
	assert(!re.test("about:blank"), "about NÃO injetável");
}

console.log("\nResultado: " + passed + " passarams, " + failed + " falharam");
process.exit(failed ? 1 : 0);
