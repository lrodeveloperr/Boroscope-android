import { createReadStream } from "node:fs";
import { createServer } from "node:http";
import { extname, resolve } from "node:path";

const args = process.argv.slice(2);
const readArg = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};

const host = readArg("--host", "0.0.0.0");
const port = Number(readArg("--port", "4173"));
const root = resolve(import.meta.dirname);

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml"
};

createServer((request, response) => {
  const requestedPath = new URL(request.url ?? "/", "http://preview.local").pathname;
  const relativePath = requestedPath === "/" ? "index.html" : requestedPath.slice(1);
  const filePath = resolve(root, relativePath);

  if (!filePath.startsWith(`${root}/`)) {
    response.writeHead(403).end("Forbidden");
    return;
  }

  const stream = createReadStream(filePath);
  stream.once("open", () => {
    response.writeHead(200, {
      "Content-Type": contentTypes[extname(filePath)] ?? "application/octet-stream",
      "Cache-Control": "no-store"
    });
    stream.pipe(response);
  });
  stream.once("error", () => response.writeHead(404).end("Not found"));
}).listen(port, host);
