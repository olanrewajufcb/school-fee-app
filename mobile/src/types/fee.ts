export type FeeFrequency = 'TERMLY' | 'ANNUAL' | 'ONE_TIME';
export type FeeStatus = 'UNPAID' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'WAIVED';

export interface FeeItem {
  id: string;
  name: string;
  description?: string;
  amount: number;
  isMandatory: boolean;
}

export interface StudentFee {
  id: string;
  studentId: string;
  studentName?: string;
  feeStructureId: string;
  feeName: string;
  totalAmount: number;
  amountPaid: number;
  remainingBalance: number;
  status: FeeStatus;
  dueDate: string;
  termName?: string;
  sessionName?: string;
}

export interface FeeDashboardSummary {
  totalExpectedRevenue: number;
  totalCollectedRevenue: number;
  outstandingRevenue: number;
  collectionRate: number;
  totalStudents: number;
  paidStudents: number;
  partialStudents: number;
  unpaidStudents: number;
}
