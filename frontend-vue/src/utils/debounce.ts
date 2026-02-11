export function debounce(callback: () => void, timeout: number) {
  let timer: any;
  return () => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = setTimeout(callback, timeout);
  };
}