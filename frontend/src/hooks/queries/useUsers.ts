import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userManagementService } from '@/services/userManagementService';
import type { 
  CreateStaffRequest, 
  CreateParentRequest
} from '@/types/user-management';

// --- QUERIES ---

export const useUsersQuery = (params: { page: number; size: number; userType?: string; status?: string; search?: string; sortBy?: string }) => {
  return useQuery({
    queryKey: ['users', params],
    queryFn: async () => {
      const response = await userManagementService.getUsers(params);
      return response;
    },
  });
};

// --- MUTATIONS ---

export const useCreateStaffMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateStaffRequest) => {
      const response = await userManagementService.createStaff(data);
      return response;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
};

export const useCreateParentMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateParentRequest) => {
      const response = await userManagementService.createParent(data);
      return response;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
};
