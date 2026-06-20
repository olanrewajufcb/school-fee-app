import React, { useState } from 'react';
import UserTable from './UserTable';
import CreateStaffModal from './modals/CreateStaffModal';
import CreateParentModal from './modals/CreateParentModal';
import { Button } from '@/components/ui/button';
import { PlusCircle, UserPlus } from 'lucide-react';

const UserManagementView: React.FC = () => {
  const [isStaffModalOpen, setIsStaffModalOpen] = useState(false);
  const [isParentModalOpen, setIsParentModalOpen] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-lg shadow-sm border border-gray-100">
        <div>
          <h2 className="text-xl font-semibold text-gray-800">Users Overview</h2>
          <p className="text-gray-500 text-sm mt-1">Manage school staff and parent accounts.</p>
        </div>
        <div className="flex gap-3 w-full sm:w-auto">
          <Button 
            onClick={() => setIsStaffModalOpen(true)}
            className="flex-1 sm:flex-none bg-blue-600 hover:bg-blue-700 text-white"
          >
            <UserPlus className="w-4 h-4 mr-2" />
            Add Staff
          </Button>
          <Button 
            onClick={() => setIsParentModalOpen(true)}
            variant="outline"
            className="flex-1 sm:flex-none border-blue-200 text-blue-700 hover:bg-blue-50"
          >
            <PlusCircle className="w-4 h-4 mr-2" />
            Add Parent
          </Button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-100 overflow-hidden">
        <UserTable />
      </div>

      {/* Modals */}
      <CreateStaffModal 
        isOpen={isStaffModalOpen} 
        onClose={() => setIsStaffModalOpen(false)} 
      />
      <CreateParentModal 
        isOpen={isParentModalOpen} 
        onClose={() => setIsParentModalOpen(false)} 
      />
    </div>
  );
};

export default UserManagementView;
