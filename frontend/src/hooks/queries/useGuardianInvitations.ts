import { useMutation } from '@tanstack/react-query';
import { guardianInvitationService } from '@/services/guardianInvitationService';
import type { BulkInvitationRequest } from '@/types/guardian-invitation';

export const useInviteGuardianMutation = () => {
  return useMutation({
    mutationFn: async (guardianId: string) => {
      const response = await guardianInvitationService.inviteGuardian(guardianId);
      return response.data;
    },
  });
};

export const useInviteBulkGuardiansMutation = () => {
  return useMutation({
    mutationFn: async (data: BulkInvitationRequest) => {
      const response = await guardianInvitationService.inviteGuardiansBulk(data);
      return response.data;
    },
  });
};
