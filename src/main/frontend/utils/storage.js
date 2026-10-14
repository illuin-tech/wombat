export function readPreference(key) {
  try {
    return localStorage.getItem(key);
  } catch (error) {
    console.warn(`Cannot read preference ${key}`, error);
    return null;
  }
}

export function writePreference(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch (error) {
    console.warn(`Cannot persist preference ${key}`, error);
  }
}
