export const bookingStatus: Record<number, string> = {
  0: "待酒店确认",
  1: "已确认",
  4: "已取消",
  5: "酒店已拒绝",
  6: "未到店",
};

export const money = (amount: number, currency = "CNY") =>
  new Intl.NumberFormat("zh-CN", { style: "currency", currency }).format(amount);

export const dateText = (value: string) =>
  new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium" }).format(new Date(`${value}T00:00:00`));
