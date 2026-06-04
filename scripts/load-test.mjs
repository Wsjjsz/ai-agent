import { mkdir, writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";

const targetUrl = stripTrailingSlash(process.env.LOAD_TEST_TARGET || "http://127.0.0.1:8123");
const totalRequests = positiveInt(process.env.LOAD_TEST_REQUESTS, 3000);
const concurrency = positiveInt(process.env.LOAD_TEST_CONCURRENCY, 100);
const guestId = process.env.LOAD_TEST_GUEST_ID || "loadtestguest0001";
const reportDir = process.env.LOAD_TEST_REPORT_DIR || "target/load-test";

const scenarios = [
  {
    name: "ready",
    method: "GET",
    path: "/api/health/ready",
    headers: {},
  },
  {
    name: "auth-me",
    method: "GET",
    path: "/api/auth/me",
    headers: { "X-Guest-Id": guestId },
  },
  {
    name: "session-list",
    method: "GET",
    path: "/api/session/list",
    headers: { "X-Guest-Id": guestId },
  },
];

const selectedScenario = process.env.LOAD_TEST_SCENARIO;
const plan = selectedScenario
  ? scenarios.filter((scenario) => scenario.name === selectedScenario)
  : scenarios;

if (plan.length === 0) {
  throw new Error(`Unknown LOAD_TEST_SCENARIO: ${selectedScenario}`);
}

const results = [];
for (const scenario of plan) {
  console.log(`Running ${scenario.name}: ${scenario.method} ${scenario.path}, requests=${totalRequests}, concurrency=${concurrency}`);
  results.push(await runScenario(scenario));
}

await mkdir(reportDir, { recursive: true });
await writeFile(`${reportDir}/load-test-report.json`, JSON.stringify({ targetUrl, totalRequests, concurrency, results }, null, 2));
await writeFile(`${reportDir}/load-test-report.md`, toMarkdown(results));

console.log(`Reports generated: ${reportDir}/load-test-report.md, ${reportDir}/load-test-report.json`);

async function runScenario(scenario) {
  const latencies = [];
  const statusCounts = new Map();
  let completed = 0;
  let failures = 0;
  const started = performance.now();

  async function worker() {
    while (completed < totalRequests) {
      const current = completed++;
      if (current >= totalRequests) {
        return;
      }
      const start = performance.now();
      try {
        const response = await fetch(`${targetUrl}${scenario.path}`, {
          method: scenario.method,
          headers: scenario.headers,
          body: scenario.body,
        });
        await response.arrayBuffer();
        const elapsed = performance.now() - start;
        latencies.push(elapsed);
        statusCounts.set(response.status, (statusCounts.get(response.status) || 0) + 1);
        if (response.status < 200 || response.status >= 300) {
          failures++;
        }
      } catch (error) {
        failures++;
        latencies.push(performance.now() - start);
        statusCounts.set("network_error", (statusCounts.get("network_error") || 0) + 1);
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  const elapsedSeconds = (performance.now() - started) / 1000;
  latencies.sort((left, right) => left - right);
  return {
    name: scenario.name,
    method: scenario.method,
    path: scenario.path,
    requests: totalRequests,
    concurrency,
    failures,
    rps: totalRequests / elapsedSeconds,
    latencyMs: {
      min: percentile(latencies, 0),
      p50: percentile(latencies, 0.5),
      p90: percentile(latencies, 0.9),
      p95: percentile(latencies, 0.95),
      p99: percentile(latencies, 0.99),
      max: percentile(latencies, 1),
    },
    statusCounts: Object.fromEntries(statusCounts.entries()),
  };
}

function toMarkdown(results) {
  const lines = [
    "# Load Test Report",
    "",
    `- Target: ${targetUrl}`,
    `- Requests per scenario: ${totalRequests}`,
    `- Concurrency: ${concurrency}`,
    "",
    "| Scenario | Path | Failures | RPS | P50 | P95 | P99 | Max | Status |",
    "|---|---|---:|---:|---:|---:|---:|---:|---|",
  ];
  for (const result of results) {
    lines.push([
      result.name,
      result.path,
      result.failures,
      result.rps.toFixed(2),
      ms(result.latencyMs.p50),
      ms(result.latencyMs.p95),
      ms(result.latencyMs.p99),
      ms(result.latencyMs.max),
      Object.entries(result.statusCounts).map(([status, count]) => `${status}:${count}`).join(", "),
    ].join(" | ").replace(/^/, "| ").replace(/$/, " |"));
  }
  lines.push("");
  return lines.join("\n");
}

function percentile(values, ratio) {
  if (values.length === 0) {
    return 0;
  }
  const index = Math.min(values.length - 1, Math.max(0, Math.ceil(values.length * ratio) - 1));
  return values[index];
}

function ms(value) {
  return `${value.toFixed(1)}ms`;
}

function positiveInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function stripTrailingSlash(value) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}
