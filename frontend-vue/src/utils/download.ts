export const blobDownload = (data: string | Blob, fileName: string) => {
  const blob = new Blob([data], { type: 'application/octet-stream' });
  const link = document.createElement('a');
  link.download = fileName;
  const href = URL.createObjectURL(blob);
  link.href = href;
  link.click();
  link.remove();
  URL.revokeObjectURL(href);
};