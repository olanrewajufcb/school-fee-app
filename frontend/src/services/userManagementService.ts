import api from '@/lib/api';
import type { 
  CreateStaffRequest, 
  CreateParentRequest,
  PageResponse,
  ApiResponse,
  UserSummaryResponse
} from '@/types/user-management';

export const userManagementService = {
  getUsers: async (params: { page: number; size: number; userType?: string; status?: string; search?: string; sortBy?: string }): Promise<PageResponse<UserSummaryResponse>> => {
    const { data } = await api.get<ApiResponse<PageResponse<UserSummaryResponse>>>('/api/v1/auth/users', { params });
    return data.data;
  },

  createStaff: async (requestData: CreateStaffRequest): Promise<any> => {
    const { data } = await api.post<ApiResponse<any>>('/api/v1/auth/staff', requestData);
    return data.data;
  },

  createParent: async (requestData: CreateParentRequest): Promise<any> => {
    const { data } = await api.post<ApiResponse<any>>('/api/v1/auth/parents', requestData);
    return data.data;
  }
};
