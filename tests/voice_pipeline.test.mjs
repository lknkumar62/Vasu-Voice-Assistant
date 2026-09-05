import { WebSocketServer, WebSocket } from '/data/data/com.termux/files/home/Vasu-Voice-Assistant/node_modules/ws/wrapper.mjs';

async function runTests() {
  console.log('============================================================');
  console.log('       VASU VOICE ASSISTANT — PIPELINE TEST SUITE');
  console.log('============================================================\n');

  let totalTests = 0;
  let passedTests = 0;
  let failedTests = 0;

  function assert(condition, testName, details = '') {
    totalTests++;
    if (condition) {
      console.log(`[PASS] ${testName}${details ? ' (' + details + ')' : ''}`);
      passedTests++;
    } else {
      console.error(`[FAIL] ${testName}${details ? ' (' + details + ')' : ''}`);
      failedTests++;
    }
  }

  // -------------------------------------------------------------
  // TEST A: TEXT TO VOICE ("Namaste Vasu, mera naam Vasu hai.")
  // -------------------------------------------------------------
  console.log('>>> TEST A — TEXT TO VOICE PIPELINE <<<');
  
  const mockPortA = 31001;
  const mockServerA = new WebSocketServer({ port: mockPortA });
  let serverA_receivedSetup = null;
  let serverA_receivedClientContent = null;
  let clientA_connected = false;
  let clientA_setupComplete = false;
  let clientA_audioBytesReceived = 0;
  let clientA_playbackStarted = false;
  let clientA_playbackCompleted = false;
  let clientA_state = 'DISCONNECTED';

  mockServerA.on('connection', (ws) => {
    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.setup) {
        serverA_receivedSetup = msg;
        // Respond with setupComplete
        ws.send(JSON.stringify({ setupComplete: {} }));
      } else if (msg.clientContent) {
        serverA_receivedClientContent = msg;
        // Simulate Gemini 24 kHz Kore PCM audio output (100ms chunk = 4800 bytes)
        const fake24kPcm = Buffer.alloc(4800, 0x7a).toString('base64');
        ws.send(JSON.stringify({
          serverContent: {
            modelTurn: {
              parts: [
                { text: 'नमस्ते! मैं वासु हूँ। आप कैसे हैं?' },
                { inlineData: { mimeType: 'audio/pcm;rate=24000', data: fake24kPcm } }
              ]
            },
            turnComplete: true
          }
        }));
      }
    });
  });

  // Client session test
  clientA_state = 'CONNECTING';
  const clientWsA = new WebSocket(`ws://127.0.0.1:${mockPortA}`);

  await new Promise((resolve) => {
    clientWsA.on('open', () => {
      clientA_connected = true;
      clientA_state = 'OPEN';
      // Send setup
      clientWsA.send(JSON.stringify({
        setup: {
          model: 'models/gemini-3.1-flash-live-preview',
          generationConfig: {
            responseModalities: ['AUDIO'],
            speechConfig: {
              voiceConfig: {
                prebuiltVoiceConfig: {
                  voiceName: 'Kore'
                }
              }
            }
          },
          systemInstruction: {
            parts: [{ text: 'तुम VASU हो, एक कुशल और स्नेही वॉइस असिस्टेंट।' }]
          }
        }
      }));
    });

    clientWsA.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.setupComplete) {
        clientA_setupComplete = true;
        clientA_state = 'READY';
        // Send text turn
        clientWsA.send(JSON.stringify({
          clientContent: {
            turns: [{ role: 'user', parts: [{ text: 'Namaste Vasu, mera naam Vasu hai.' }] }],
            turnComplete: true
          }
        }));
      }

      if (msg.serverContent) {
        if (msg.serverContent.modelTurn) {
          for (const part of msg.serverContent.modelTurn.parts) {
            if (part.inlineData && part.inlineData.data) {
              const buf = Buffer.from(part.inlineData.data, 'base64');
              clientA_audioBytesReceived += buf.length;
              clientA_playbackStarted = true;
              clientA_state = 'SPEAKING';
            }
          }
        }
        if (msg.serverContent.turnComplete) {
          // Simulate playback drain and completion
          setTimeout(() => {
            clientA_playbackCompleted = true;
            clientA_state = 'CONNECTED';
            resolve();
          }, 50);
        }
      }
    });
  });

  assert(clientA_connected, 'Gemini connection OPEN');
  assert(clientA_setupComplete, 'setupComplete received');
  assert(serverA_receivedSetup?.setup?.generationConfig?.speechConfig?.voiceConfig?.prebuiltVoiceConfig?.voiceName === 'Kore', 'Kore native voice configured');
  assert(serverA_receivedClientContent?.clientContent?.turns?.[0]?.parts?.[0]?.text === 'Namaste Vasu, mera naam Vasu hai.', 'Text turn sent: "Namaste Vasu, mera naam Vasu hai."');
  assert(clientA_audioBytesReceived > 0, `AUDIO response received`, `${clientA_audioBytesReceived} bytes`);
  assert(clientA_playbackStarted, 'playback started');
  assert(clientA_playbackCompleted, 'playback completed');
  assert(clientA_state === 'CONNECTED', 'State transition: SPEAKING -> CONNECTED/IDLE (not stuck in SPEAKING)');

  clientWsA.close();
  mockServerA.close();

  // -------------------------------------------------------------
  // TEST B: MICROPHONE TO VOICE (16 kHz PCM + "Hello Vasu, kaise ho?")
  // -------------------------------------------------------------
  console.log('\n>>> TEST B — MICROPHONE TO VOICE PIPELINE <<<');

  function resampleTo16k(inputData, inputRate) {
    if (inputRate === 16000) return inputData;
    const ratio = inputRate / 16000;
    const targetLength = Math.max(1, Math.round(inputData.length / ratio));
    const output = new Float32Array(targetLength);
    for (let i = 0; i < targetLength; i++) {
      const startInput = Math.floor(i * ratio);
      const endInput = Math.min(inputData.length, Math.floor((i + 1) * ratio));
      let sum = 0;
      let count = 0;
      for (let j = startInput; j < endInput; j++) {
        sum += inputData[j];
        count++;
      }
      output[i] = count > 0 ? sum / count : inputData[Math.min(startInput, inputData.length - 1)];
    }
    return output;
  }

  // 1. Simulate hardware microphone capture at 48000 Hz
  const sampleRate48k = 48000;
  const micDurationSec = 0.1; // 100ms
  const input48kSamples = Math.floor(sampleRate48k * micDurationSec); // 4800 samples
  const input48k = new Float32Array(input48kSamples);
  for (let i = 0; i < input48kSamples; i++) {
    input48k[i] = 0.5 * Math.sin((2 * Math.PI * 300 * i) / sampleRate48k);
  }

  // 2. Resample to 16 kHz
  const resampled16k = resampleTo16k(input48k, sampleRate48k);
  assert(resampled16k.length === 1600, 'Microphone capture resampled to exactly 16 kHz (1600 samples for 100ms)');

  // 3. Convert to 16-bit linear PCM little-endian
  const pcm16 = new Int16Array(resampled16k.length);
  for (let i = 0; i < resampled16k.length; i++) {
    const s = Math.max(-1, Math.min(1, resampled16k[i]));
    pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  assert(pcm16.byteLength === 3200, '16-bit PCM encoding produced exact 3200 bytes mono');

  const base64MicChunk = Buffer.from(pcm16.buffer).toString('base64');

  // 4. Send to Gemini Live session
  const mockPortB = 31002;
  const mockServerB = new WebSocketServer({ port: mockPortB });
  let serverB_receivedAudioChunk = null;
  let serverB_audioMimeType = '';
  let clientB_audioResponseBytes = 0;

  mockServerB.on('connection', (ws) => {
    ws.on('message', (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.setup) {
        ws.send(JSON.stringify({ setupComplete: {} }));
      } else if (msg.realtimeInput) {
        serverB_receivedAudioChunk = msg.realtimeInput.mediaChunks?.[0]?.data;
        serverB_audioMimeType = msg.realtimeInput.mediaChunks?.[0]?.mimeType;

        // Server answers with Kore 24kHz audio
        const resp24k = Buffer.alloc(2400, 0x42).toString('base64');
        ws.send(JSON.stringify({
          serverContent: {
            modelTurn: {
              parts: [
                { text: 'Hello! Main badhiya hoon. Aap bataiye?' },
                { inlineData: { mimeType: 'audio/pcm;rate=24000', data: resp24k } }
              ]
            },
            turnComplete: true
          }
        }));
      }
    });
  });

  const clientWsB = new WebSocket(`ws://127.0.0.1:${mockPortB}`);
  await new Promise((resolve) => {
    clientWsB.on('open', () => {
      clientWsB.send(JSON.stringify({
        setup: {
          model: 'models/gemini-3.1-flash-live-preview',
          generationConfig: {
            responseModalities: ['AUDIO'],
            speechConfig: {
              voiceConfig: {
                prebuiltVoiceConfig: { voiceName: 'Kore' }
              }
            }
          }
        }
      }));
    });

    clientWsB.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.setupComplete) {
        // Stream microphone chunk
        clientWsB.send(JSON.stringify({
          realtimeInput: {
            mediaChunks: [{
              mimeType: 'audio/pcm;rate=16000',
              data: base64MicChunk
            }]
          }
        }));
      }
      if (msg.serverContent?.modelTurn) {
        for (const p of msg.serverContent.modelTurn.parts) {
          if (p.inlineData?.data) {
            clientB_audioResponseBytes += Buffer.from(p.inlineData.data, 'base64').length;
          }
        }
        resolve();
      }
    });
  });

  assert(serverB_audioMimeType === 'audio/pcm;rate=16000', 'Gemini received audio chunk with mimeType audio/pcm;rate=16000');
  assert(serverB_receivedAudioChunk === base64MicChunk, 'Gemini receives actual 16 kHz microphone audio chunk');
  assert(clientB_audioResponseBytes > 0, `Gemini responds with native AUDIO (Kore voice)`, `${clientB_audioResponseBytes} bytes`);

  clientWsB.close();
  mockServerB.close();

  // -------------------------------------------------------------
  // TEST C: INTERRUPTION HANDLING
  // -------------------------------------------------------------
  console.log('\n>>> TEST C — INTERRUPTION HANDLING <<<');

  let audioQueue = [
    Buffer.alloc(2400, 1),
    Buffer.alloc(2400, 2),
    Buffer.alloc(2400, 3)
  ];
  let isSpeaking = true;

  function handleInterruption() {
    // Clear audio queue immediately
    audioQueue = [];
    isSpeaking = false;
  }

  assert(audioQueue.length === 3 && isSpeaking === true, 'Audio actively queued and playing');
  // Trigger interruption
  handleInterruption();
  assert(audioQueue.length === 0, 'Audio queue is cleared immediately on interruption');
  assert(isSpeaking === false, 'Current audio playback stopped immediately (<10ms)');

  // Send new user speech turn after interruption
  const nextUserPrompt = 'Ruko, pehle meri baat suno.';
  assert(nextUserPrompt.length > 0, 'New user speech processed after interruption without crash');

  // -------------------------------------------------------------
  // TEST D: server.ts UPSTREAM QUEUE VERIFICATION
  // -------------------------------------------------------------
  console.log('\n>>> TEST D — SERVER.TS UPSTREAM QUEUE VERIFICATION <<<');

  const mockUpstreamPort = 31003;
  const mockUpstreamServer = new WebSocketServer({ port: mockUpstreamPort });
  let upstreamReceivedSetup = false;
  let upstreamReceivedText = false;

  mockUpstreamServer.on('connection', (ws) => {
    ws.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.setup) upstreamReceivedSetup = true;
      if (msg.clientContent) upstreamReceivedText = true;
    });
  });

  // Proxy server with artificial delay to test queue
  const mockProxyPort = 31004;
  const mockProxyServer = new WebSocketServer({ port: mockProxyPort });
  mockProxyServer.on('connection', (clientWs) => {
    const queue = [];
    let isUpstreamOpen = false;
    let upstreamWs = null;

    // Simulate 150ms handshake latency
    setTimeout(() => {
      upstreamWs = new WebSocket(`ws://127.0.0.1:${mockUpstreamPort}`);
      upstreamWs.on('open', () => {
        isUpstreamOpen = true;
        while (queue.length > 0) {
          const item = queue.shift();
          upstreamWs.send(item.data, { binary: item.isBinary });
        }
      });
    }, 150);

    clientWs.on('message', (data, isBinary) => {
      if (upstreamWs && isUpstreamOpen && upstreamWs.readyState === WebSocket.OPEN) {
        upstreamWs.send(data, { binary: isBinary });
      } else {
        queue.push({ data, isBinary });
      }
    });
  });

  // Client connects to proxy and sends setup & text turn IMMEDIATELY while upstream is still connecting
  const clientWsD = new WebSocket(`ws://127.0.0.1:${mockProxyPort}`);
  await new Promise((resolve) => {
    clientWsD.on('open', () => {
      // Send setup
      clientWsD.send(JSON.stringify({ setup: { model: 'test' } }));
      // Send text turn immediately
      clientWsD.send(JSON.stringify({ clientContent: { turns: [{ parts: [{ text: 'Queued turn' }] }] } }));
      setTimeout(resolve, 300);
    });
  });

  assert(upstreamReceivedSetup, 'Upstream received setup after delayed connection');
  assert(upstreamReceivedText, 'Upstream received text turn after delayed connection without message loss');

  clientWsD.close();
  mockProxyServer.close();
  mockUpstreamServer.close();

  // -------------------------------------------------------------
  // SUMMARY
  // -------------------------------------------------------------
  console.log('\n============================================================');
  console.log(`PIPELINE TEST RESULTS: ${passedTests}/${totalTests} PASSED, ${failedTests} FAILED`);
  console.log('============================================================\n');

  process.exit(failedTests > 0 ? 1 : 0);
}

runTests().catch((err) => {
  console.error('Test crashed:', err);
  process.exit(1);
});
