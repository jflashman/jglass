export const DEVICE_CONFIG_PROTOCOL = "jglass/device-config/1";

export function normalizeWakePhrase(value: unknown): string {
  if (typeof value !== "string" || value.length > 128 || /[^A-Za-z ]/.test(value)) {
    throw new Error("JGLASS_WAKE_PHRASE must contain one to four English words, at most 32 characters");
  }
  const phrase = value.trim().replace(/ +/g, " ").toUpperCase();
  if (!phrase || phrase.length > 32 || phrase.split(" ").length > 4) {
    throw new Error("JGLASS_WAKE_PHRASE must contain one to four English words, at most 32 characters");
  }
  return phrase;
}

export function deviceConfig(wakePhrase: string) {
  return { protocol: DEVICE_CONFIG_PROTOCOL, wakePhrase: normalizeWakePhrase(wakePhrase) };
}
