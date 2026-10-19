import "@testing-library/jest-dom/vitest";

// jsdom provides no ResizeObserver, but Radix primitives used across the app
// observe element size on mount. A no-op implementation keeps those components
// mountable under test without pulling in a real layout engine.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === "undefined") {
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}

// jsdom provides no Element.scrollIntoView either, but Radix Select scrolls
// the focused option into view when its listbox opens.
if (
  typeof Element !== "undefined" &&
  typeof Element.prototype.scrollIntoView !== "function"
) {
  Element.prototype.scrollIntoView = () => {};
}
