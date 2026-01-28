export function formatDate(date: any, format = 'YYYY-MM-DD HH:mm:ss') {
  // 统一转换为 Date 对象
  const d = date instanceof Date ? date : new Date(date);
  
  if (isNaN(d.getTime())) {
    return 'Invalid Date';
  }

  const pad = (n: any) => n.toString().padStart(2, '0');
  
  const map: any = {
    'YYYY': d.getFullYear(),
    'MM': pad(d.getMonth() + 1),
    'DD': pad(d.getDate()),
    'HH': pad(d.getHours()),
    'mm': pad(d.getMinutes()),
    'ss': pad(d.getSeconds()),
    'SSS': d.getMilliseconds().toString().padStart(3, '0'),
    'd': d.getDay(), // 星期 0-6
    'W': ['日', '一', '二', '三', '四', '五', '六'][d.getDay()] // 中文星期
  };

  return format.replace(/YYYY|MM|DD|HH|mm|ss|SSS|W|d/g, match => map[match]);
}