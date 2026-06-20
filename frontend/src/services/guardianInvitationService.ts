import api from '@/lib/api';
import type { 
  BulkInvitationRequest, 
  GuardianInvitationResponse, 
  BulkInvitationResponse 
} from '@/types/guardian-invitation';
import type { ApiResponse } from '@/types/user-management';

export const guardianInvitationService = {
  inviteGuardian: async (guardianId: string): Promise<ApiResponse<GuardianInvitationResponse>> => {
    const { data } = await api.post<ApiResponse<GuardianInvitationResponse>>(`/api/v1/auth/guardians/${guardianId}/invite`);
    return data;
  },

  inviteGuardiansBulk: async (requestData: BulkInvitationRequest): Promise<ApiResponse<BulkInvitationResponse>> => {
    const { data } = await api.post<ApiResponse<BulkInvitationResponse>>('/api/v1/auth/guardians/invite-bulk', requestData);
    return data;
  }
};
