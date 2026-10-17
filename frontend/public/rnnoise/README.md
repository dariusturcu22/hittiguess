# Vendored noise filters

These four files are copied unchanged from `@sapphi-red/web-noise-suppressor@0.4.1`, because the package only documents Vite `?url` imports and this app loads the AudioWorklet and its WebAssembly by URL from `public/`.

- `noiseGateWorklet.js` from `dist/noiseGate/workletProcessor.js`
- `rnnoiseWorklet.js` from `dist/rnnoise/workletProcessor.js`
- `rnnoise.wasm` from `dist/rnnoise.wasm`
- `rnnoise_simd.wasm` from `dist/rnnoise_simd.wasm`

Re-copy all four from the same package version whenever that dependency is upgraded.
