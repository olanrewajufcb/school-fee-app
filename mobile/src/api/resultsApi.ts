import apiClient from './client';
import {
  ApiResponse,
  StudentTerminalResult,
  EnterCaScoreRequest,
  EnterExamScoreRequest,
} from '../types';

export const resultsApi = {
  /**
   * Get current terminal results for all children of logged-in parent
   */
  async getMyChildrenResults(): Promise<StudentTerminalResult[]> {
    const res = await apiClient.get<ApiResponse<StudentTerminalResult[]>>('/api/v1/results/my-children/current');
    return res.data.data;
  },

  /**
   * Get a specific student's terminal result for a term
   */
  async getStudentResult(studentId: string, termId: string): Promise<StudentTerminalResult> {
    const res = await apiClient.get<ApiResponse<StudentTerminalResult>>(`/api/v1/results/students/${studentId}/term/${termId}`);
    return res.data.data;
  },

  /**
   * Teacher: Enter Continuous Assessment (CA) scores for a class & subject
   */
  async enterCaScores(payload: EnterCaScoreRequest): Promise<void> {
    await apiClient.post('/api/v1/results/ca-scores', payload);
  },

  /**
   * Teacher: Enter Exam scores for a class & subject
   */
  async enterExamScores(payload: EnterExamScoreRequest): Promise<void> {
    await apiClient.post('/api/v1/results/exam-scores', payload);
  },

  /**
   * Teacher: Add teacher comment on a student's report card
   */
  async addTeacherComment(studentId: string, termId: string, comment: string): Promise<void> {
    await apiClient.put(`/api/v1/results/report-cards/${studentId}/term/${termId}/teacher-comment`, { comment });
  },

  /**
   * Admin: Publish or unpublish class results
   */
  async togglePublishResults(termId: string, publish: boolean): Promise<void> {
    const action = publish ? 'publish' : 'unpublish';
    await apiClient.put(`/api/v1/results/terms/${termId}/${action}`);
  },
};

export default resultsApi;
