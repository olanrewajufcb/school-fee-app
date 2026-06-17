import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/components/auth/AuthProvider';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ShieldAlert, ArrowLeft } from 'lucide-react';

export const UnauthorizedPage: React.FC = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
            <Card className="w-full max-w-md text-center">
                <CardHeader>
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
                        <ShieldAlert className="h-8 w-8 text-red-600" />
                    </div>
                    <CardTitle className="text-xl">Access Denied</CardTitle>
                </CardHeader>

                <CardContent className="space-y-4">
                    <p className="text-gray-600">
                        You don't have permission to access this page.
                    </p>

                    {user && (
                        <p className="text-sm text-gray-500">
                            Signed in as: {user.email}<br />
                            Role: {user.userType}
                        </p>
                    )}

                    <div className="flex flex-col gap-2">
                        <Button onClick={() => navigate('/')} variant="outline">
                            <ArrowLeft className="mr-2 h-4 w-4" />
                            Back to Dashboard
                        </Button>

                        <Button onClick={logout} variant="ghost" className="text-red-600">
                            Sign Out
                        </Button>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
};

export default UnauthorizedPage;
