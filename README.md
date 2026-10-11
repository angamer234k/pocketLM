# pocketLM

On-device LLM server for Android.

Runs GGUF models via **llama.cpp**, exposes OpenAI-compatible API.
Built for cwbridge + Fire devices.

## Status
- [x] Project skeleton
- [x] llama.cpp JNI (load / complete / unload)
- [x] OpenAI-compatible `/v1/chat/completions` + `/v1/models`
- [ ] Streaming SSE
- [ ] Chat templates
- [ ] API key management
- [ ] Model download UI
- [ ] GPU / NPU (Vulkan / LiteRT later)

## Build
Open in Android Studio or use Actions → **Build Debug APK**.

First native build clones llama.cpp and compiles it — can take several minutes.

## Usage
1. Copy a small GGUF (e.g. Qwen2.5-0.5B Q4) to the phone, note the path.
2. Open pocketLM → paste path → **Load Model**.
3. **Start Server** (default port 9090).
4. From another device on the same network:

```bash
curl http://PHONE_IP:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}],"max_tokens":64}'
```

## License
MIT
