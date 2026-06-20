export interface BulkInvitationRequest {
  guardianIds: string[];
}

export interface GuardianInvitationResponse {
  guardianId: string;
  success: boolean;
  message?: string;
}

export interface BulkInvitationResponse {
  totalAttempted: number;
  successCount: number;
  failedCount: number;
  details: GuardianInvitationResponse[];
}
