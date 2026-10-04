export function currency(amount: number, code = "CNY") {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: code,
    minimumFractionDigits: 2,
  }).format(amount);
}

export function dateTime(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function folioStatus(value: number) {
  if (value === 0) return "OUTSTANDING";
  if (value === 1) return "SETTLED";
  if (value === 2) return "CREDIT";
  return `UNKNOWN (${value})`;
}

