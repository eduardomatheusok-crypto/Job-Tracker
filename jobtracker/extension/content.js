(() => {
	"use strict";

	chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
		if (message && message.type === "jt:capture") {
			sendResponse({ data: captureJobData() });
		}
	});

	function captureJobData() {
		const jsonLd = parseJsonLd();
		const ogTitle = metaContent('meta[property="og:title"]');
		const ogSiteName = metaContent('meta[property="og:site_name"]');

		const position = firstNonEmpty(
			textOf(jsonLd.title),
			textOf(selectFirst(POSITION_SELECTORS)),
			textOf(selectFirst(["h1"])),
			cutSuffix(ogTitle)
		);

		const companyName = firstNonEmpty(
			jsonLd.hiringOrganization,
			textOf(metaContent('meta[property="og:site_name"]')),
			textOf(selectFirst(COMPANY_SELECTORS))
		);

		const location = firstNonEmpty(
			jsonLd.location,
			textOf(selectFirst(LOCATION_SELECTORS))
		);

		return {
			position: clean(position),
			companyName: clean(companyName),
			location: clean(location),
			jobUrl: clean(location.href),
			platform: detectPlatform(location.hostname)
		};
	}

	const POSITION_SELECTORS = [
		".job-details-jobs-unified-top-card__job-title",
		".jobsearch-JobInfoHeader-title",
		'[data-job-title]',
		".job-title",
		".posting-title h1",
		".posting-header h1"
	];

	const COMPANY_SELECTORS = [
		".job-details-jobs-unified-top-card__company-name",
		"[data-company-name='true']",
		'.jobsearch-JobInfoHeader-companyName',
		"[data-company]",
		".company-name",
		".posting-company"
	];

	const LOCATION_SELECTORS = [
		'[data-testid="job-location"]',
		".job-details-jobs-unified-top-card__tertiary-description-container span",
		".posting-location",
		"[data-location]"
	];

	function parseJsonLd() {
		const scripts = document.querySelectorAll('script[type="application/ld+json"]');
		for (const script of scripts) {
			let parsed;
			try {
				parsed = JSON.parse(script.textContent || scripts.textContent || "");
			} catch (e) {
				continue;
			}
			const nouns = Array.isArray(parsed) ? parsed : [parsed];
			for (const item of nouns) {
				const job = findJobPosting(item);
				if (!job) {
					continue;
				}
				const org = job.hiringOrganization && typeof job.hiringOrganization === "object"
					? job.hiringOrganization.name
					: job.hiringOrganization;
				const address = job.jobLocation && job.jobLocation.address && typeof job.jobLocation.address === "object"
					? job.jobLocation.address
					: null;
				const locality = address && address.addressLocality;
				return {
					title: job.title,
					hiringOrganization: typeof org === "string" ? org : null,
					location: locality
				};
			}
		}
		return {};
	}

	function findJobPosting(node) {
		if (node && node["@type"] === "JobPosting") {
			return node;
		}
		if (node && typeof node === "object") {
			for (const key of Object.keys(node)) {
				if (key === "@graph" && Array.isArray(node[key])) {
					for (const child of node[key]) {
						const found = findJobPosting(child);
						if (found) {
							return found;
						}
					}
				}
			}
		}
		return null;
	}

	function metaContent(selector) {
		const node = document.querySelector(selector);
		return node ? node.content || "" : "";
	}

	function selectFirst(selectors) {
		for (const selector of selectors) {
			const node = document.querySelector(selector);
			if (node) {
				return node;
			}
		}
		return null;
	}

	function textOf(node) {
		return node ? (node.textContent || "").trim() : "";
	}

	function firstNonEmpty() {
		for (const value of arguments) {
			if (value && String(value).trim()) {
				return String(value).trim();
			}
		}
		return "";
	}

	function clean(value) {
		return value ? value.replace(/\s+/g, " ").trim() : "";
	}

	function cutSuffix(value) {
		if (!value) {
			return "";
		}
		return value.replace(/\s+[|]\s+(LinkedIn|Indeed|Glassdoor|Gupy|Indeed\.com|Recruit|SmartRecruiters)[^]*$/i, "");
	}

	function detectPlatform(hostname) {
		const host = hostname.toLowerCase();
		const known = [
			["linkedin", "LinkedIn"],
			["indeed", "Indeed"],
			["gupy", "Gupy"],
			["glassdoor", "Glassdoor"],
			["infojobs", "InfoJobs"],
			["programathor", "Programathor"],
			["catho", "Catho"],
			["workable", "Workable"],
			["greenhouse", "Greenhouse"],
			["lever", "Lever"],
			["bamboohr", "BambooHR"],
			["solides", "Solides"],
			["vagas.com.br", "Vagas.com.br"]
		];
		for (const [partial, label] of known) {
			if (host.includes(partial)) {
				return label;
			}
		}
		const bare = host.replace(/^www\./, "").split(".")[0];
		return bare ? bare.toUpperCase() : "";
	}
})();