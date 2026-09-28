export interface ApiResponse<T> {
  success: boolean;
  data: T;
  meta?: {
    page?: number;
    size?: number;
    totalElements?: number;
    totalPages?: number;
  };
  errors?: Array<{
    code?: string;
    message?: string;
    field?: string;
  }>;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
