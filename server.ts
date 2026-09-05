import http from 'http';
import express from 'express';
import { WebSocket, WebSocketServer } from 'ws';

const app = express();
const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

app.use(express.json());

// Safe configuration check without leaking secrets
app.get('/api/config', (_req, res) => {
  const apiKey = process.env.GEMINI_API_KEY || '';
  res.json({
    ok: true,
    hasApiKey: Boolean(apiKey.trim()),
    voiceName: 'Kore',
    audioFormatInput: '16-bit PCM / 16 kHz / mono',
    audioFormatOutput: '16-bit PCM / 24 kHz / mono',
  });
});

const server = http.createServer(app);

// Dedicated WebSocket Server for Gemini Live (Separate from Vite HMR)
const geminiWss = new WebSocketServer({ noServer: true });

geminiWss.on('connection', (clientWs) => {
  const apiKey = process.env.GEMINI_API_KEY || '';
  if (!apiKey.trim()) {
    console.warn('[VASU] Gemini session rejected: GEMINI_API_KEY is not set on server');
    clientWs.send(JSON.stringify({
      error: 'GEMINI_API_KEY missing on server. Please configure your Gemini API key.'
    }));
    clientWs.close(1008, 'API key missing');
    return;
  }

  const liveEndpoint = `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=${encodeURIComponent(apiKey)}`;
  console.log('[VASU] Gemini session connecting via secure server proxy');

  interface QueuedClientMessage {
    data: any;
    isBinary: boolean;
  }
  const pendingClientQueue: QueuedClientMessage[] = [];
  let isUpstreamOpen = false;

  let upstreamWs: WebSocket | null = null;
  try {
    upstreamWs = new WebSocket(liveEndpoint);
  } catch (err: any) {
    console.error('[VASU] Failed to initialize upstream Gemini WebSocket:', err?.message);
    pendingClientQueue.length = 0;
    clientWs.close(1011, 'Upstream connection error');
    return;
  }

  // Upstream handlers
  upstreamWs.on('open', () => {
    isUpstreamOpen = true;
    console.log('[VASU] Gemini session OPEN (upstream connected)');

    // Flush any client messages queued while upstream was connecting
    if (pendingClientQueue.length > 0) {
      console.log(`[VASU] Flushing ${pendingClientQueue.length} queued client message(s) to upstream`);
      while (pendingClientQueue.length > 0) {
        const item = pendingClientQueue.shift()!;
        if (upstreamWs && upstreamWs.readyState === WebSocket.OPEN) {
          upstreamWs.send(item.data, { binary: item.isBinary });
        }
      }
    }

    if (clientWs.readyState === WebSocket.OPEN) {
      clientWs.send(JSON.stringify({ _vasu_proxy: 'UPSTREAM_OPEN' }));
    }
  });

  upstreamWs.on('message', (data, isBinary) => {
    if (clientWs.readyState === WebSocket.OPEN) {
      clientWs.send(data, { binary: isBinary });
    }
  });

  upstreamWs.on('error', (err) => {
    console.error('[VASU] Upstream Gemini WebSocket error:', err.message);
    pendingClientQueue.length = 0;
    if (clientWs.readyState === WebSocket.OPEN) {
      clientWs.send(JSON.stringify({ error: `Upstream error: ${err.message}` }));
      clientWs.close(1011, `Upstream error: ${err.message}`);
    }
  });

  upstreamWs.on('close', (code, reason) => {
    isUpstreamOpen = false;
    pendingClientQueue.length = 0;
    console.log(`[VASU] Gemini session closed (upstream code=${code})`);
    if (clientWs.readyState === WebSocket.OPEN || clientWs.readyState === WebSocket.CONNECTING) {
      clientWs.close(code, reason);
    }
    upstreamWs = null;
  });

  // Client handlers
  clientWs.on('message', (data, isBinary) => {
    if (upstreamWs && isUpstreamOpen && upstreamWs.readyState === WebSocket.OPEN) {
      upstreamWs.send(data, { binary: isBinary });
    } else if (upstreamWs && upstreamWs.readyState === WebSocket.CONNECTING) {
      // Upstream is still establishing TLS/connection: queue message
      pendingClientQueue.push({ data, isBinary });
    } else {
      console.warn('[VASU] Dropped client message: upstream socket is not open or connecting');
    }
  });

  clientWs.on('error', (err) => {
    console.error('[VASU] Client WebSocket error:', err.message);
    pendingClientQueue.length = 0;
    if (upstreamWs && (upstreamWs.readyState === WebSocket.OPEN || upstreamWs.readyState === WebSocket.CONNECTING)) {
      upstreamWs.close();
    }
    upstreamWs = null;
  });

  clientWs.on('close', () => {
    pendingClientQueue.length = 0;
    if (upstreamWs && (upstreamWs.readyState === WebSocket.OPEN || upstreamWs.readyState === WebSocket.CONNECTING)) {
      upstreamWs.close();
    }
    upstreamWs = null;
  });
});

// Selective upgrade handler: only intercepts /api/live-ws, leaving Vite HMR untouched
server.on('upgrade', (request, socket, head) => {
  const pathname = request.url?.split('?')[0] || '';

  if (pathname === '/api/live-ws') {
    geminiWss.handleUpgrade(request, socket, head, (ws) => {
      geminiWss.emit('connection', ws, request);
    });
  }
  // Otherwise, do not destroy socket - allow Vite HMR or other upgrade handlers to process
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[VASU] Server running on http://0.0.0.0:${PORT}`);
});
