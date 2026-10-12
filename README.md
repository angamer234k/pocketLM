# pocketLM

On-device LLM server for Android.

GGUF via **llama.cpp** · OpenAI-compatible API · streaming SSE · download models in-app.

## Status
- [x] llama.cpp load / complete / stream
- [x] `/v1/models` + `/v1/chat/completions` (+ SSE)
- [x] Import local GGUF
- [x] **Download online** (catalog + custom HF URL)
- [ ] Chat templates
- [ ] API keys
- [ ] GPU / NPU

## Usage
1. Install APK from Actions.
2. **Download online** → tap a small model (Qwen 0.5B / SmolLM recommended first).
3. Wait for download → it auto-loads.
4. **Start Server** (port 9090).
5. From LAN:

```bash
curl -N http://PHONE_IP:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}],"max_tokens":64,"stream":true}'
```

Custom GGUF: paste any Hugging Face `resolve/main/...gguf` URL.

## License
MIT
