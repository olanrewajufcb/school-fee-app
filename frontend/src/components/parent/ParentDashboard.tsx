import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useStudentDetails } from '@/hooks/useStudentDetails';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
    Wallet,
    FileText,
    CalendarCheck,
    ChevronRight,
    AlertCircle
} from 'lucide-react';

export const ParentDashboard: React.FC = () => {
    const { user, isLoading: authLoading } = useAuth();
    const navigate = useNavigate();

    if (authLoading) {
        return <DashboardSkeleton />;
    }

    if (!user) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <Card className="w-96">
                    <CardContent className="pt-6 text-center">
                        <AlertCircle className="mx-auto h-12 w-12 text-yellow-500 mb-4" />
                        <p className="text-lg">Please log in to view your dashboard.</p>
                        <Button onClick={() => window.location.href = '/login'} className="mt-4">
                            Go to Login
                        </Button>
                    </CardContent>
                </Card>
            </div>
        );
    }

    return (
        <div className="container mx-auto px-4 py-8 max-w-4xl">
            {/* Welcome Header */}
            <div className="mb-8">
                <h1 className="text-2xl font-bold">
                    Welcome, {user.firstName} {user.lastName}
                </h1>
                <p className="text-gray-600">{user.schoolName}</p>
            </div>

            {/* Quick Actions */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                <Card
                    className="cursor-pointer hover:shadow-md transition-shadow"
                    onClick={() => navigate('/payments')}
                >
                    <CardContent className="pt-6 flex items-center gap-4">
                        <Wallet className="h-8 w-8 text-green-600" />
                        <div>
                            <p className="font-semibold">Pay Fees</p>
                            <p className="text-sm text-gray-500">Pay for all children at once</p>
                        </div>
                    </CardContent>
                </Card>

                <Card
                    className="cursor-pointer hover:shadow-md transition-shadow"
                    onClick={() => navigate('/receipts')}
                >
                    <CardContent className="pt-6 flex items-center gap-4">
                        <FileText className="h-8 w-8 text-blue-600" />
                        <div>
                            <p className="font-semibold">Receipts</p>
                            <p className="text-sm text-gray-500">Download payment receipts</p>
                        </div>
                    </CardContent>
                </Card>

                <Card
                    className="cursor-pointer hover:shadow-md transition-shadow"
                    onClick={() => navigate('/attendance')}
                >
                    <CardContent className="pt-6 flex items-center gap-4">
                        <CalendarCheck className="h-8 w-8 text-purple-600" />
                        <div>
                            <p className="font-semibold">Attendance</p>
                            <p className="text-sm text-gray-500">View attendance records</p>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Children Section */}
            <h2 className="text-xl font-semibold mb-4">My Children</h2>

            {user.children.length === 0 ? (
                <Card>
                    <CardContent className="pt-6 text-center py-12">
                        <AlertCircle className="mx-auto h-12 w-12 text-yellow-500 mb-4" />
                        <p className="text-lg font-medium">No children linked to your account</p>
                        <p className="text-gray-500 mt-2">
                            Please contact your school administrator to link your children to this account.
                        </p>
                        <Button
                            variant="outline"
                            className="mt-4"
                            onClick={() => window.location.href = `tel:${user.schoolId}`}
                        >
                            Contact School
                        </Button>
                    </CardContent>
                </Card>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {user.children.map((child) => (
                        <ChildCard
                            key={child.studentId}
                            studentId={child.studentId}
                            relationship={child.relationship}
                            canViewFees={child.canViewFees}
                            canViewResults={child.canViewResults}
                            canViewAttendance={child.canViewAttendance}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

// ============================================================================
// Child Card Component
// ============================================================================

interface ChildCardProps {
    studentId: string;
    relationship: string;
    canViewFees: boolean;
    canViewResults: boolean;
    canViewAttendance: boolean;
}

const ChildCard: React.FC<ChildCardProps> = ({
                                                 studentId,
                                                 relationship,
                                                 canViewFees,
                                                 canViewResults,
                                                 canViewAttendance,
                                             }) => {
    const { student, isLoading, error } = useStudentDetails(studentId);
    const navigate = useNavigate();

    if (isLoading) {
        return (
            <Card>
                <CardContent className="pt-6">
                    <Skeleton className="h-6 w-3/4 mb-2" />
                    <Skeleton className="h-4 w-1/2 mb-4" />
                    <Skeleton className="h-10 w-full" />
                </CardContent>
            </Card>
        );
    }

    if (error || !student) {
        return (
            <Card className="border-red-200">
                <CardContent className="pt-6">
                    <p className="text-red-500">Failed to load student information</p>
                </CardContent>
            </Card>
        );
    }

    return (
        <Card className="hover:shadow-md transition-shadow">
            <CardHeader>
                <CardTitle className="flex items-center justify-between">
                    <span>{student.firstName} {student.lastName}</span>
                    <span className="text-sm font-normal text-gray-500 bg-gray-100 px-2 py-1 rounded">
            {relationship}
          </span>
                </CardTitle>
            </CardHeader>

            <CardContent>
                {/* Student Info */}
                <div className="space-y-2 mb-4">
                    <div className="flex justify-between text-sm">
                        <span className="text-gray-500">Class</span>
                        <span className="font-medium">{student.currentClass}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                        <span className="text-gray-500">Admission No.</span>
                        <span className="font-medium">{student.admissionNumber}</span>
                    </div>
                    {student.feeStatus && (
                        <div className="flex justify-between text-sm">
                            <span className="text-gray-500">Fee Status</span>
                            <FeeStatusBadge status={student.feeStatus.status} balance={student.feeStatus.balance} />
                        </div>
                    )}
                </div>

                {/* Action Buttons */}
                <div className="space-y-2">
                    {canViewFees && (
                        <Button
                            variant="outline"
                            className="w-full justify-between"
                            onClick={() => navigate(`/fees/${studentId}`)}
                        >
              <span className="flex items-center gap-2">
                <Wallet className="h-4 w-4" />
                View Fees
              </span>
                            <ChevronRight className="h-4 w-4" />
                        </Button>
                    )}

                    {canViewResults && (
                        <Button
                            variant="outline"
                            className="w-full justify-between"
                            onClick={() => navigate(`/results/${studentId}`)}
                        >
              <span className="flex items-center gap-2">
                <FileText className="h-4 w-4" />
                View Results
              </span>
                            <ChevronRight className="h-4 w-4" />
                        </Button>
                    )}

                    {canViewAttendance && (
                        <Button
                            variant="outline"
                            className="w-full justify-between"
                            onClick={() => navigate(`/attendance/${studentId}`)}
                        >
              <span className="flex items-center gap-2">
                <CalendarCheck className="h-4 w-4" />
                View Attendance
              </span>
                            <ChevronRight className="h-4 w-4" />
                        </Button>
                    )}

                    {/* Pay Fees — always available if canViewFees */}
                    {canViewFees && student.feeStatus && student.feeStatus.balance > 0 && (
                        <Button
                            className="w-full justify-between bg-green-600 hover:bg-green-700"
                            onClick={() => navigate(`/pay/${studentId}`)}
                        >
              <span className="flex items-center gap-2">
                <Wallet className="h-4 w-4" />
                Pay Fees — ₦{student.feeStatus.balance.toLocaleString()}
              </span>
                            <ChevronRight className="h-4 w-4" />
                        </Button>
                    )}
                </div>
            </CardContent>
        </Card>
    );
};

// ============================================================================
// Fee Status Badge
// ============================================================================

interface FeeStatusBadgeProps {
    status: string;
    balance: number;
}

const FeeStatusBadge: React.FC<FeeStatusBadgeProps> = ({ status, balance }) => {
    const statusConfig: Record<string, { color: string; label: string }> = {
        PAID: { color: 'text-green-600 bg-green-50', label: 'Paid' },
        PARTIAL: { color: 'text-yellow-600 bg-yellow-50', label: `Owing ₦${balance.toLocaleString()}` },
        PENDING: { color: 'text-orange-600 bg-orange-50', label: `Due: ₦${balance.toLocaleString()}` },
        OVERDUE: { color: 'text-red-600 bg-red-50', label: `Overdue: ₦${balance.toLocaleString()}` },
    };

    const config = statusConfig[status] || { color: 'text-gray-600 bg-gray-50', label: status };

    return (
        <span className={`text-xs font-medium px-2 py-1 rounded ${config.color}`}>
      {config.label}
    </span>
    );
};

// ============================================================================
// Loading Skeleton
// ============================================================================

const DashboardSkeleton: React.FC = () => (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
        <Skeleton className="h-8 w-64 mb-2" />
        <Skeleton className="h-4 w-48 mb-8" />

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {[1, 2, 3].map((i) => (
                <Card key={i}>
                    <CardContent className="pt-6">
                        <Skeleton className="h-8 w-8 mb-2" />
                        <Skeleton className="h-4 w-3/4 mb-1" />
                        <Skeleton className="h-3 w-1/2" />
                    </CardContent>
                </Card>
            ))}
        </div>

        <Skeleton className="h-6 w-32 mb-4" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[1, 2].map((i) => (
                <Card key={i}>
                    <CardContent className="pt-6">
                        <Skeleton className="h-6 w-3/4 mb-2" />
                        <Skeleton className="h-4 w-1/2 mb-4" />
                        <Skeleton className="h-10 w-full" />
                    </CardContent>
                </Card>
            ))}
        </div>
    </div>
);

export default ParentDashboard;