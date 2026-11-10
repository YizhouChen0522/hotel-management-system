export function currency(amount: number, code = "CNY") {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: code,
    minimumFractionDigits: 2,
  }).format(amount);
}

export function dateTime(value: string | null) {
  if (!value) return "—";
  const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!parts) return value;
  return `${parts[1]}年${Number(parts[2])}月${Number(parts[3])}日 ${parts[4]}:${parts[5]}`;
}

export function folioStatus(value: number) {
  if (value === 0) return "OUTSTANDING";
  if (value === 1) return "SETTLED";
  if (value === 2) return "CREDIT";
  return `UNKNOWN (${value})`;
}

