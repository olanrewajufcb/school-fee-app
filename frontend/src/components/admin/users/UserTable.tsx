import React, { useState } from 'react';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { useUsersQuery } from '@/hooks/queries/useUsers';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Search } from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const columns: GridColDef[] = [
  { 
    field: 'name', 
    headerName: 'Name', 
    flex: 1,
    minWidth: 200,
    valueGetter: (_, row) => `${row.firstName} ${row.lastName}`
  },
  { 
    field: 'email', 
    headerName: 'Email', 
    flex: 1,
    minWidth: 200 
  },
  { 
    field: 'phone', 
    headerName: 'Phone', 
    flex: 1,
    minWidth: 150 
  },
  {
    field: 'userType',
    headerName: 'Role',
    width: 150,
    renderCell: (params) => {
      const type = params.value as string;
      const variant = type === 'SUPER_ADMIN' ? 'destructive' : type === 'SCHOOL_ADMIN' ? 'default' : 'secondary';
      return <Badge variant={variant} className="capitalize">{type.replace('_', ' ').toLowerCase()}</Badge>;
    }
  },
  {
    field: 'status',
    headerName: 'Status',
    width: 120,
    renderCell: (params) => {
      const isActive = params.value === 'ACTIVE';
      return (
        <span className={`px-2 py-1 rounded-full text-xs font-medium ${isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
          {params.value}
        </span>
      );
    }
  },
  {
    field: 'createdAt',
    headerName: 'Joined',
    width: 120,
    valueFormatter: (value: string) => new Date(value).toLocaleDateString()
  }
];

const UserTable: React.FC = () => {
  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 10,
  });
  const [search, setSearch] = useState('');
  const [userType, setUserType] = useState<string>('ALL');

  // Debounce search value for API call
  const [debouncedSearch, setDebouncedSearch] = useState('');
  React.useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 500);
    return () => clearTimeout(timer);
  }, [search]);

  const { data, isLoading, error } = useUsersQuery({
    page: paginationModel.page,
    size: paginationModel.pageSize,
    search: debouncedSearch || undefined,
    userType: userType === 'ALL' ? undefined : userType,
  });

  if (error) {
    return <div className="p-4 text-red-500 bg-red-50 rounded-md m-4">Failed to load users. Please try again.</div>;
  }

  return (
    <div className="w-full flex flex-col">
      {/* Filters */}
      <div className="p-4 border-b border-gray-100 flex flex-col sm:flex-row gap-4 bg-gray-50/50">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <Input 
            placeholder="Search by name, email or phone..." 
            className="pl-9 bg-white border-gray-200 focus-visible:ring-blue-500"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="w-full sm:w-48">
          <Select value={userType} onValueChange={setUserType}>
            <SelectTrigger className="bg-white border-gray-200">
              <SelectValue placeholder="Filter by Role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Roles</SelectItem>
              <SelectItem value="SCHOOL_ADMIN">Admin</SelectItem>
              <SelectItem value="TEACHER">Teacher</SelectItem>
              <SelectItem value="ACCOUNTANT">Accountant</SelectItem>
              <SelectItem value="PARENT">Parent</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* DataGrid */}
      <div style={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={data?.content || []}
          columns={columns}
          getRowId={(row) => row.userId}
          paginationMode="server"
          rowCount={data?.totalElements || 0}
          paginationModel={paginationModel}
          onPaginationModelChange={setPaginationModel}
          pageSizeOptions={[10, 25, 50]}
          loading={isLoading}
          disableRowSelectionOnClick
          className="border-0 rounded-none"
          sx={{
            '& .MuiDataGrid-columnHeaders': {
              backgroundColor: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              color: '#475569',
              fontWeight: 600,
            },
            '& .MuiDataGrid-cell': {
              borderColor: '#f1f5f9',
            },
            '& .MuiDataGrid-row:hover': {
              backgroundColor: '#f8fafc',
            }
          }}
        />
      </div>
    </div>
  );
};

export default UserTable;
