// HTTPS front door for a local-network playtest. Browsers only expose the microphone,
// tab-audio capture, and the Clipboard API on a secure origin, so a plain-HTTP LAN
// address can never carry voice or the DJ's audio. This serves one HTTPS origin on the
// LAN address and forwards backend paths to the Spring Boot service and everything
// else to the Next dev server, WebSocket upgrades included. Serving both from one
// origin means one certificate to accept and no cross-origin API calls.
//
// Usage: node scripts/lan-https-proxy.mjs <lan-address>
// The certificate is generated once with the JDK's keytool into .lan-https/.
// See docs/DEV_SETUP.md.

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import http from "node:http";
import https from "node:https";
import net from "node:net";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

export const HTTPS_PORT = 3443;
export const FRONTEND_PORT = 3000;
export const BACKEND_PORT = 8080;
const LOOPBACK_HOST = "127.0.0.1";
const CERTIFICATE_DIRECTORY = ".lan-https";
const KEYSTORE_FILE = "lan.p12";
const KEYSTORE_ADDRESS_FILE = "address.txt";
// Only protects a throwaway local development certificate that never leaves this machine.
const KEYSTORE_PASSWORD = "hittiguess-lan";
const CERTIFICATE_ALIAS = "hittiguess-lan";
const CERTIFICATE_VALIDITY_DAYS = "365";
const BAD_GATEWAY_STATUS = 502;
const BACKEND_PATH_PREFIXES = [
  "/api/",
  "/auth/",
  "/oauth2/authorization/",
  "/login/oauth2/",
  "/v3/api-docs",
  "/swagger-ui",
  "/actuator/",
];
const BACKEND_WEBSOCKET_PATH = "/ws";

export function isBackendPath(pathname) {
  if (pathname === BACKEND_WEBSOCKET_PATH || pathname.startsWith(`${BACKEND_WEBSOCKET_PATH}/`)) return true;
  return BACKEND_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function targetPortFor(requestUrl) {
  const pathname = new URL(requestUrl, "https://placeholder").pathname;
  return isBackendPath(pathname) ? BACKEND_PORT : FRONTEND_PORT;
}

function keytoolCommand() {
  const javaHome = process.env.JAVA_HOME;
  const executableName = process.platform === "win32" ? "keytool.exe" : "keytool";
  const bundledKeytool = javaHome ? path.join(javaHome, "bin", executableName) : undefined;
  return bundledKeytool && existsSync(bundledKeytool) ? bundledKeytool : "keytool";
}

// Regenerated whenever the LAN address changes, since the certificate names it.
function ensureKeystore(repositoryRoot, lanAddress) {
  const directory = path.join(repositoryRoot, CERTIFICATE_DIRECTORY);
  const keystorePath = path.join(directory, KEYSTORE_FILE);
  const addressPath = path.join(directory, KEYSTORE_ADDRESS_FILE);
  const isCurrent = existsSync(keystorePath) && existsSync(addressPath)
    && readFileSync(addressPath, "utf8").trim() === lanAddress;
  if (isCurrent) return keystorePath;

  mkdirSync(directory, { recursive: true });
  const subjectAlternativeName = net.isIP(lanAddress) ? `ip:${lanAddress}` : `dns:${lanAddress}`;
  if (existsSync(keystorePath)) execFileSync(keytoolCommand(), ["-delete", "-alias", CERTIFICATE_ALIAS, "-keystore", keystorePath, "-storepass", KEYSTORE_PASSWORD]);
  execFileSync(keytoolCommand(), [
    "-genkeypair",
    "-alias", CERTIFICATE_ALIAS,
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", CERTIFICATE_VALIDITY_DAYS,
    "-dname", `CN=${lanAddress}`,
    "-ext", `SAN=${subjectAlternativeName},dns:localhost,ip:127.0.0.1`,
    "-storetype", "PKCS12",
    "-keystore", keystorePath,
    "-storepass", KEYSTORE_PASSWORD,
  ], { stdio: "inherit" });
  writeFileSync(addressPath, lanAddress);
  return keystorePath;
}

// Forwarded headers let each service see the original HTTPS origin, the host the
// browser used, and the client's address.
function forwardedHeaders(request) {
  return {
    ...request.headers,
    "x-forwarded-proto": "https",
    "x-forwarded-host": request.headers.host ?? "",
    "x-forwarded-for": request.socket.remoteAddress ?? "",
  };
}

function proxyRequest(request, response) {
  const upstream = http.request({
    host: LOOPBACK_HOST,
    port: targetPortFor(request.url ?? "/"),
    method: request.method,
    path: request.url,
    headers: forwardedHeaders(request),
  }, (upstreamResponse) => {
    response.writeHead(upstreamResponse.statusCode ?? BAD_GATEWAY_STATUS, upstreamResponse.headers);
    upstreamResponse.pipe(response);
  });
  upstream.on("error", () => {
    if (!response.headersSent) response.writeHead(BAD_GATEWAY_STATUS);
    response.end();
  });
  request.pipe(upstream);
}

// A WebSocket upgrade is relayed as raw bytes once the upstream accepts it: the
// request line and headers are replayed, then both sockets are piped together.
function proxyUpgrade(request, clientSocket, head) {
  const upstreamSocket = net.connect(targetPortFor(request.url ?? "/"), LOOPBACK_HOST, () => {
    const headerLines = Object.entries(forwardedHeaders(request))
      .flatMap(([name, value]) => (Array.isArray(value) ? value : [value]).map((headerValue) => `${name}: ${headerValue}`));
    upstreamSocket.write(`${request.method} ${request.url} HTTP/${request.httpVersion}\r\n${headerLines.join("\r\n")}\r\n\r\n`);
    if (head.length > 0) upstreamSocket.write(head);
    upstreamSocket.pipe(clientSocket);
    clientSocket.pipe(upstreamSocket);
  });
  const closeBoth = () => {
    upstreamSocket.destroy();
    clientSocket.destroy();
  };
  upstreamSocket.on("error", closeBoth);
  clientSocket.on("error", closeBoth);
}

function start() {
  const [, , lanAddress] = process.argv;
  if (!lanAddress) {
    console.error("Usage: node scripts/lan-https-proxy.mjs <lan-address>");
    process.exit(1);
  }
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const keystorePath = ensureKeystore(repositoryRoot, lanAddress);
  const server = https.createServer({ pfx: readFileSync(keystorePath), passphrase: KEYSTORE_PASSWORD }, proxyRequest);
  server.on("upgrade", proxyUpgrade);
  server.listen(HTTPS_PORT, () => {
    console.log(`LAN playtest over HTTPS: https://${lanAddress}:${HTTPS_PORT}`);
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  start();
}
