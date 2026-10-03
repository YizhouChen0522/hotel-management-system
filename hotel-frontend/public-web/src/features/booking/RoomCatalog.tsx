"use client";
import { useEffect, useState } from "react";
import { AuthGate } from "@/components/auth/AuthGate";
import { bookingApi } from "@/features/booking/booking-api";
import { money } from "@/features/booking/format";
import type { RoomType } from "@/types/booking";

export function RoomCatalog() {
  const [rooms, setRooms] = useState<RoomType[]>([]);
  const [error, setError] = useState("");
  useEffect(() => { bookingApi.roomTypes().then(setRooms).catch((e: unknown) => setError(e instanceof Error ? e.message : "房型加载失败")); }, []);
  return <main className="catalog-page"><header className="page-title"><p className="eyebrow">ROOMS & SUITES</p><h1>选择适合您的空间</h1><p>房型信息可匿名浏览；实时价格和库存由酒店服务器返回。</p></header>{error && <p className="form-error center-state">{error}</p>}<div className="room-grid">{rooms.map((room) => <article className="room-card" key={room.id}><div><p className="eyebrow">ROOM TYPE</p><h2>{room.name}</h2><p>{room.description || "酒店房型详情"}</p><small>最多 {room.capacity} 人 · 基础展示价 {money(room.basePrice)}</small></div><AuthGate label="登录并查看实时价格" target={`/booking?roomTypeId=${room.id}`}/></article>)}</div></main>;
}
