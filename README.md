# pocketLM

On-device LLM server for Android.

Runs GGUF models via llama.cpp, exposes OpenAI-compatible API, manages API keys/limits.
Built for use with cwbridge and Fire devices.

## Status
- [x] Project skeleton
- [ ] llama.cpp JNI integration
- [ ] OpenAI-compatible server
- [ ] API key management
- [ ] Background service
- [ ] Model download/import UI
- [ ] LiteRT backend (later)

## Build
1. Open in Android Studio
2. Sync Gradle
3. Build & run on device (arm64-v8a)

## Planned ports
Default: 9090 (configurable: 90, 11255, etc)

## License
MIT (or whatever you want)
