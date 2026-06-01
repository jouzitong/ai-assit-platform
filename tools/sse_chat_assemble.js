#!/usr/bin/env node

const http = require('http');

const host = process.env.AI_ENGINE_HOST || '127.0.0.1';
const port = Number(process.env.AI_ENGINE_PORT || '13101');
const path = '/api/v1/ai/execution/chat/stream';

const body = JSON.stringify({
  provider: 'DASHSCOPE',
  model: 'qwen-math-turbo',
  messages: [{ role: 'USER', content: '请用中文介绍一下你自己，100字以内。' }],
  options: { temperature: 0.3, maxTokens: 200 }
});

const req = http.request({
  host,
  port,
  path,
  method: 'POST',
  headers: {
    'Content-Type': 'application/json; charset=UTF-8',
    Accept: 'text/event-stream; charset=UTF-8',
    'Content-Length': Buffer.byteLength(body)
  }
}, (res) => {
  res.setEncoding('utf8');

  let sseBuffer = '';
  let fullText = '';

  res.on('data', (chunk) => {
    sseBuffer += chunk;

    let idx;
    while ((idx = sseBuffer.indexOf('\n\n')) >= 0) {
      const rawEvent = sseBuffer.slice(0, idx);
      sseBuffer = sseBuffer.slice(idx + 2);

      const dataLines = rawEvent
        .split('\n')
        .filter((l) => l.startsWith('data:'))
        .map((l) => l.slice(5).trim())
        .filter(Boolean);

      if (!dataLines.length) continue;

      const payloadText = dataLines.join('');
      try {
        const payload = JSON.parse(payloadText);
        const delta = payload.delta || '';
        if (delta) {
          process.stdout.write(`[chunk] ${JSON.stringify(delta)}\n`);
          fullText += delta;
        }
      } catch (e) {
        process.stdout.write(`[raw] ${payloadText}\n`);
      }
    }
  });

  res.on('end', () => {
    process.stdout.write('\n===== assembled =====\n');
    process.stdout.write(fullText + '\n');
  });
});

req.on('error', (err) => {
  console.error('request error:', err.message);
  process.exitCode = 1;
});

req.write(body);
req.end();
