const http = require('http');
const fs = require('fs');
const path = require('path');

const root = __dirname;
const port = Number(process.env.PORT || 8765);

http.createServer((request, response) => {
  const pathname = new URL(request.url, `http://${request.headers.host || 'localhost'}`).pathname;
  const requested = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
  const file = path.resolve(root, requested);

  if (!file.startsWith(root + path.sep) && file !== path.join(root, 'index.html')) {
    response.writeHead(403).end('Forbidden');
    return;
  }

  fs.readFile(file, (error, content) => {
    if (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500).end('Not found');
      return;
    }
    const extension = path.extname(file);
    const type = extension === '.html' ? 'text/html; charset=utf-8'
      : extension === '.js' ? 'text/javascript; charset=utf-8' : 'application/octet-stream';
    response.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store' });
    response.end(content);
  });
}).listen(port, '127.0.0.1', () => {
  console.log(`Daily Mart UI prototype running at http://127.0.0.1:${port}`);
});
