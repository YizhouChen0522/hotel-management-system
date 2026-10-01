export interface ApiResult<T> { code: number; message: string; data: T; }
export interface User { id: number; username: string; realName: string | null; phone: string | null; email: string; status: number; }
export interface LoginRequest { email: string; password: string; }
export interface AuthSession { token: string; user: User; roles: string[]; }
