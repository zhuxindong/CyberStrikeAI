const normalizeLsMtime = (month: string, day: string, timeOrYear: string) => {
  if (!month || !day || !timeOrYear) return '';
  const token = String(timeOrYear).trim();
  if (/^\d{4}$/.test(token)) return token + ' ' + month + ' ' + day;
  const now = new Date();
  let year = now.getFullYear();
  if (/^\d{1,2}:\d{2}$/.test(token)) {
    const monthMap: any = { Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5, Jul: 6, Aug: 7, Sep: 8, Oct: 9, Nov: 10, Dec: 11 };
    const m = monthMap[month];
    const d = parseInt(day, 10);
    if (m != null && !isNaN(d)) {
      const inferred = new Date(year, m, d);
      if (inferred.getTime() > now.getTime()) year = year - 1;
    }
    return year + ' ' + month + ' ' + day + ' ' + token;
  }
  return month + ' ' + day + ' ' + token;
}

const modeToType = (mode: string) => {
  if (!mode || !mode.length) return '';
  var c = mode.charAt(0);
  if (c === 'd') return 'dir';
  if (c === '-') return 'file';
  if (c === 'l') return 'link';
  if (c === 'c') return 'char';
  if (c === 'b') return 'block';
  if (c === 's') return 'socket';
  if (c === 'p') return 'pipe';
  return c;
}

export const parseWebshellListItems = (rawOutput: string) => {
  const lines = (rawOutput || '').split(/\n/).filter((l: string) => { return l.trim(); }).slice(3);
  const items = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    let name = '';
    let isDir = false;
    let size = '';
    let mode = '';
    let mtime = '';
    let owner = '';
    let group = '';
    let type = '';
    const mLs = line.match(/^(\S+)\s+(\d+)\s+(\S+)\s+(\S+)\s+(\d+)\s+([A-Za-z]{3})\s+(\d{1,2})\s+(\S+)\s+(.+)$/);
    if (mLs) {
      mode = mLs[1];
      owner = mLs[3];
      group = mLs[4];
      size = mLs[5];
      mtime = normalizeLsMtime(mLs[6], mLs[7], mLs[8]);
      name = (mLs[9] || '').trim();
      isDir = mode?.startsWith('d');
      type = modeToType(mode);
    } else {
      const mName = line.match(/\s*(\S+)\s*$/);
      name = mName ? mName[1].trim() : line.trim();
      if (name === '.' || name === '..') continue;
      isDir = line.startsWith('d') || line.toLowerCase().indexOf('<dir>') !== -1;
      if (line.startsWith('-') || line.startsWith('d')) {
        const parts = line.split(/\s+/);
        if (parts.length >= 5) { mode = parts[0]; size = parts[4]; }
        if (parts.length >= 4) { owner = parts[2] || ''; group = parts[3] || ''; }
        if (parts.length >= 8 && /^[A-Za-z]{3}$/.test(parts[5])) mtime = normalizeLsMtime(parts[5], parts[6], parts[7]);
        type = modeToType(mode);
      }
    }
    if (name === '.' || name === '..') continue;
    items.push({
      name: name,
      isDir: isDir,
      line: line,
      size: size,
      mode: mode,
      mtime: mtime,
      owner: owner,
      group: group,
      type: type
    });
  }
  return items;
}