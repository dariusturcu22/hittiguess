import { act, fireEvent, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useTurnSound } from "./use-turn-sound";

afterEach(() => vi.unstubAllGlobals());

describe("useTurnSound", () => {
  it("plays after user interaction enables audio and closes the context on unmount", () => {
    const start = vi.fn();
    const close = vi.fn(async () => {});
    const oscillator = { frequency: { value: 0 }, connect: vi.fn(), disconnect: vi.fn(), start, stop: vi.fn(), onended: null };
    const gain = { gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() };
    const context = { state: "running", currentTime: 0, destination: {}, resume: vi.fn(async () => {}), close, createOscillator: () => oscillator, createGain: () => gain };
    const constructor = vi.fn(function createContext() { return context; });
    vi.stubGlobal("AudioContext", constructor);
    const view = renderHook(() => useTurnSound());
    act(() => view.result.current());
    expect(start).not.toHaveBeenCalled();
    fireEvent.keyDown(window, { key: "Enter" });
    act(() => view.result.current());
    expect(start).toHaveBeenCalledTimes(1);
    expect(constructor).toHaveBeenCalledTimes(1);
    view.unmount();
    expect(close).toHaveBeenCalledTimes(1);
    fireEvent.pointerDown(window);
    expect(constructor).toHaveBeenCalledTimes(1);
  });
});
