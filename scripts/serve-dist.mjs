import { createReadStream, existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const root = join(process.cwd(), 'dist');
const port = Number(process.env.PORT ?? 4173);

const mimeTypes = {
  '.css': 'text/css',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.json': 'application/json',
  '.map': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml'
};

createServer((request, response) => {
  const rawPath = decodeURIComponent(new URL(request.url ?? '/', `http://localhost:${port}`).pathname);
  const safePath = normalize(rawPath).replace(/^(\.\.[/\\])+/, '');
  let filePath = join(root, safePath === '/' ? 'index.html' : safePath);

  if (!filePath.startsWith(root) || !existsSync(filePath)) {
    filePath = join(root, 'index.html');
  }

  response.setHeader('Content-Type', mimeTypes[extname(filePath)] ?? 'application/octet-stream');
  createReadStream(filePath).pipe(response);
}).listen(port, '0.0.0.0', () => {
  console.log(`尼康图传预览: http://localhost:${port}`);
});
