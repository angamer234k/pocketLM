# pocketLM

On-device LLM server for Android.

Runs GGUF via **llama.cpp**, OpenAI-compatible API (streaming SSE supported).
Built for cwbridge + Fire devices.

## Status
- [x] llama.cpp JNI (load / complete / stream / unload)
- [x] `/v1/models` + `/v1/chat/completions`
- [x] SSE streaming (`"stream": true`)
- [x] GGUF import picker (copies into app storage)
- [ ] Chat templates
- [ ] API keys / rate limits
- [ ] GPU / NPU backends

## Usage
1. Install APK from Actions artifacts.
2. **Import GGUF** → pick a small model (0.5–1B Q4 recommended first).
3. Tap the model card to load it.
4. **Start Server** (default `9090`).
5. Call from LAN:

```bash
# non-stream
curl http://PHONE_IP:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}],"max_tokens":64}'

# stream (SSE)
curl -N http://PHONE_IP:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}],"max_tokens":64,"stream":true}'
```

## Build
Actions → **Build Debug APK**. First native build is slow (compiles llama.cpp).

## License
MIT
