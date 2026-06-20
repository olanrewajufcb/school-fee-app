import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

import { Input } from '@/components/ui/input';
import { Search, Building2, ArrowRight } from 'lucide-react';
import api from '@/lib/api';
import { useSchoolStore } from '@/store/schoolStore';

interface School {
    id: string;
    name: string;
    address: string;
    email: string;
    phone: string;
    studentCount?: number;
}

interface SchoolResponse {
    content: School[];
    totalElements: number;
    totalPages: number;
}

interface ApiResponse<T> {
    success: boolean;
    data: T;
}

export const SchoolSelectorView: React.FC = () => {
    const [schools, setSchools] = useState<School[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const selectSchool = useSchoolStore(state => state.selectSchool);

    useEffect(() => {
        fetchSchools();
    }, []);

    const fetchSchools = async () => {
        try {
            setIsLoading(true);
            const response = await api.get<ApiResponse<SchoolResponse>>('/api/v1/schools', {
                params: { size: 50, sort: 'name,asc' }
            });
            setSchools(response.data.data.content);
        } catch (err) {
            console.error('Failed to fetch schools:', err);
            setError('Failed to load schools. Please try again.');
        } finally {
            setIsLoading(false);
        }
    };

    const handleSelectSchool = (school: School) => {
        selectSchool(school.id, school.name);
        // Force reload the window so that the entire React app mounts with the new School Context
        // This ensures any already-mounted components or cached queries fetch correctly for the new school
        window.location.reload();
    };

    const filteredSchools = schools.filter(school => 
        school.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        school.address?.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <div className="flex h-screen bg-gray-50 flex-col items-center justify-center p-4">
            <Card className="w-full max-w-3xl shadow-lg border-0 bg-white">
                <CardHeader className="text-center space-y-2 border-b pb-6">
                    <div className="mx-auto mb-2 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100">
                        <Building2 className="h-8 w-8 text-blue-600" />
                    </div>
                    <CardTitle className="text-2xl font-bold tracking-tight">Select a School to Manage</CardTitle>
                    <CardDescription className="text-base">
                        You have Super Admin privileges. Please select a school to continue.
                    </CardDescription>
                </CardHeader>
                <CardContent className="p-6">
                    <div className="relative mb-6">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
                        <Input 
                            type="text" 
                            placeholder="Search schools by name or location..." 
                            className="pl-10 py-6 text-lg"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </div>

                    {error && (
                        <div className="p-4 mb-6 bg-red-50 text-red-700 rounded-lg text-center">
                            {error}
                        </div>
                    )}

                    <div className="space-y-3 max-h-[50vh] overflow-y-auto pr-2">
                        {isLoading ? (
                            <div className="py-12 text-center text-gray-500">
                                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto mb-4" />
                                Loading schools...
                            </div>
                        ) : filteredSchools.length > 0 ? (
                            filteredSchools.map((school) => (
                                <div 
                                    key={school.id}
                                    onClick={() => handleSelectSchool(school)}
                                    className="p-5 border rounded-xl hover:border-blue-500 hover:shadow-md transition-all cursor-pointer bg-white group flex items-center justify-between"
                                >
                                    <div>
                                        <h3 className="font-semibold text-lg text-gray-900 group-hover:text-blue-700">{school.name}</h3>
                                        <p className="text-gray-500 text-sm mt-1 flex items-center gap-2">
                                            {school.address || "No address provided"}
                                            {school.studentCount && (
                                                <span className="bg-gray-100 px-2 py-0.5 rounded-full text-xs">
                                                    {school.studentCount} students
                                                </span>
                                            )}
                                        </p>
                                    </div>
                                    <div className="bg-gray-50 p-2 rounded-full group-hover:bg-blue-50 group-hover:text-blue-600 text-gray-400 transition-colors">
                                        <ArrowRight className="w-5 h-5" />
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="py-12 text-center text-gray-500 bg-gray-50 rounded-xl border border-dashed border-gray-200">
                                <Building2 className="h-12 w-12 mx-auto text-gray-300 mb-3" />
                                <p className="text-lg font-medium text-gray-600">No schools found</p>
                                <p className="text-sm mt-1">Try adjusting your search query.</p>
                            </div>
                        )}
                    </div>
                </CardContent>
            </Card>
        </div>
    );
};

export default SchoolSelectorView;
