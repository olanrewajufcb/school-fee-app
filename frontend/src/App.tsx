import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { RouteGuard } from '@/components/guards/RouteGuard';
import { ParentDashboard } from '@/components/parent/ParentDashboard';
import { useAuth } from '@/components/auth/AuthProvider';
import LoginPage from "@/pages/auth/LoginPage";
import ParentJoinPage from "@/pages/auth/ParentJoinPage";
import UnauthorizedPage from "@/pages/auth/UnAuthorizedPage";

// Lazy-loaded pages (loaded only when needed)
const AdminDashboard = React.lazy(() => import('@/components/admin/AdminDashboard'));
const SuperAdminDashboard = React.lazy(() => import('@/components/super-admin/SuperAdminDashboard'));
const AccountantDashboard = React.lazy(() => import('@/components/accountant/AccountantDashboard'));
const TeacherDashboard = React.lazy(() => import('@/components/teacher/TeacherDashboard'));
const ApiExplorer = React.lazy(() => import('@/pages/ApiExplorer'));

export const App: React.FC = () => {
    return (
        <BrowserRouter>
            <AuthProvider>
                <React.Suspense
                    fallback={
                        <div className="flex items-center justify-center min-h-screen">
                            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
                        </div>
                    }
                >
                    <Routes>
                        {/* Public routes */}
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/join" element={<ParentJoinPage />} />
                        <Route path="/join/:inviteToken" element={<ParentJoinPage />} />
                        <Route path="/unauthorized" element={<UnauthorizedPage />} />
                        <Route path="/docs" element={<ApiExplorer />} />

                        {/* Parent routes */}
                        <Route
                            path="/dashboard"
                            element={
                                <RouteGuard requiredUserTypes={['PARENT']}>
                                    <ParentDashboard />
                                </RouteGuard>
                            }
                        />

                        {/* Admin routes */}
                        <Route
                            path="/admin"
                            element={
                                <RouteGuard requiredUserTypes={['SCHOOL_ADMIN']}>
                                    <AdminDashboard />
                                </RouteGuard>
                            }
                        />

                        <Route
                            path="/super-admin/*"
                            element={
                                <RouteGuard requiredUserTypes={['SUPER_ADMIN']}>
                                    <SuperAdminDashboard />
                                </RouteGuard>
                            }
                        />

                        {/* Accountant routes */}
                        <Route path="/accountant" element={<Navigate to="/accountant/dashboard" replace />} />
                        <Route
                            path="/accountant/dashboard"
                            element={
                                <RouteGuard requiredUserTypes={['ACCOUNTANT', 'SCHOOL_ADMIN', 'SUPER_ADMIN']}>
                                    <AccountantDashboard />
                                </RouteGuard>
                            }
                        />

                        {/* Teacher routes */}
                        <Route path="/teacher" element={<Navigate to="/teacher/dashboard" replace />} />
                        <Route
                            path="/teacher/dashboard"
                            element={
                                <RouteGuard requiredUserTypes={['TEACHER']}>
                                    <TeacherDashboard />
                                </RouteGuard>
                            }
                        />

                        {/* Default redirect based on user type */}
                        <Route
                            path="/"
                            element={<RoleBasedRedirect />}
                        />

                        {/* Catch-all */}
                        <Route path="*" element={<Navigate to="/" replace />} />
                    </Routes>
                </React.Suspense>
            </AuthProvider>
        </BrowserRouter>
    );
};

// Redirect user to their appropriate dashboard based on role
const RoleBasedRedirect: React.FC = () => {
    const { isAuthenticated, isLoading, isParent, isSchoolAdmin, isAccountant, isTeacher, isSuperAdmin } = useAuth();

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    if (isSuperAdmin) return <Navigate to="/super-admin" replace />;
    if (isSchoolAdmin) return <Navigate to="/admin" replace />;
    if (isAccountant) return <Navigate to="/accountant/dashboard" replace />;
    if (isTeacher) return <Navigate to="/teacher/dashboard" replace />;
    if (isParent) return <Navigate to="/dashboard" replace />;

    return <Navigate to="/unauthorized" replace />;
};

export default App;
