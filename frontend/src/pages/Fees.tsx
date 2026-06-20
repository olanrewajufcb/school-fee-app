import { useState, useEffect } from 'react';
import { feesApi } from '@/services/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Receipt, Plus, Pencil, Trash2, DollarSign } from 'lucide-react';
import { toast } from 'sonner';

interface FeeTypeData {
  id: number;
  name: string;
  description: string;
  amount: number;
  frequency: string;
  schoolId: number;
  academicYear: string;
  active: boolean;
}

export function Fees() {
  const [feeTypes, setFeeTypes] = useState<FeeTypeData[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    amount: '',
    frequency: 'ANNUALLY',
    schoolId: '1',
    academicYear: '2024-2025',
    active: true,
  });

  useEffect(() => {
    fetchFeeTypes();
  }, []);

  const fetchFeeTypes = async () => {
    try {
      const response = await feesApi.getAllFeeTypes();
      setFeeTypes(response.data as FeeTypeData[]);
    } catch (error) {
      toast.error('Failed to fetch fee types');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        ...formData,
        amount: parseFloat(formData.amount),
        schoolId: parseInt(formData.schoolId),
      };

      if (editingId) {
        await feesApi.updateFeeType(editingId, payload);
        toast.success('Fee type updated successfully');
      } else {
        await feesApi.createFeeType(payload);
        toast.success('Fee type created successfully');
      }
      setDialogOpen(false);
      resetForm();
      fetchFeeTypes();
    } catch (error) {
      toast.error(editingId ? 'Failed to update fee type' : 'Failed to create fee type');
      console.error(error);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Are you sure you want to delete this fee type?')) return;
    try {
      await feesApi.deleteFeeType(id);
      toast.success('Fee type deleted successfully');
      fetchFeeTypes();
    } catch (error) {
      toast.error('Failed to delete fee type');
      console.error(error);
    }
  };

  const handleEdit = (feeType: FeeTypeData) => {
    setEditingId(feeType.id);
    setFormData({
      name: feeType.name,
      description: feeType.description,
      amount: feeType.amount.toString(),
      frequency: feeType.frequency,
      schoolId: feeType.schoolId.toString(),
      academicYear: feeType.academicYear,
      active: feeType.active,
    });
    setDialogOpen(true);
  };

  const resetForm = () => {
    setEditingId(null);
    setFormData({
      name: '',
      description: '',
      amount: '',
      frequency: 'ANNUALLY',
      schoolId: '1',
      academicYear: '2024-2025',
      active: true,
    });
  };

  const getFrequencyColor = (freq: string) => {
    switch (freq) {
      case 'MONTHLY': return 'bg-purple-100 text-purple-800';
      case 'QUARTERLY': return 'bg-blue-100 text-blue-800';
      case 'ANNUALLY': return 'bg-green-100 text-green-800';
      case 'ONE_TIME': return 'bg-amber-100 text-amber-800';
      default: return 'bg-slate-100 text-slate-800';
    }
  };

  const totalFees = feeTypes.reduce((sum, f) => sum + (f.amount || 0), 0);
  const activeFees = feeTypes.filter((f) => f.active).length;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Fee Management</h1>
          <p className="text-slate-500 mt-1">Manage fee types and student fee assignments</p>
        </div>
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={() => { resetForm(); setDialogOpen(true); }}>
              <Plus className="w-4 h-4 mr-2" />
              Add Fee Type
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[500px]">
            <DialogHeader>
              <DialogTitle>{editingId ? 'Edit Fee Type' : 'Add New Fee Type'}</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="feeName">Fee Name</Label>
                <Input
                  id="feeName"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g., Tuition Fee"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="description">Description</Label>
                <Input
                  id="description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="Fee description"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="amount">Amount ($)</Label>
                  <Input
                    id="amount"
                    type="number"
                    step="0.01"
                    value={formData.amount}
                    onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                    placeholder="0.00"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="frequency">Frequency</Label>
                  <select
                    id="frequency"
                    value={formData.frequency}
                    onChange={(e) => setFormData({ ...formData, frequency: e.target.value })}
                    className="w-full h-10 px-3 rounded-md border border-input bg-background text-sm"
                  >
                    <option value="ONE_TIME">One Time</option>
                    <option value="MONTHLY">Monthly</option>
                    <option value="QUARTERLY">Quarterly</option>
                    <option value="HALF_YEARLY">Half Yearly</option>
                    <option value="ANNUALLY">Annually</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="academicYear">Academic Year</Label>
                  <Input
                    id="academicYear"
                    value={formData.academicYear}
                    onChange={(e) => setFormData({ ...formData, academicYear: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="schoolId">School ID</Label>
                  <Input
                    id="schoolId"
                    type="number"
                    value={formData.schoolId}
                    onChange={(e) => setFormData({ ...formData, schoolId: e.target.value })}
                  />
                </div>
              </div>
              <Button type="submit" className="w-full">
                {editingId ? 'Update Fee Type' : 'Create Fee Type'}
              </Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Total Fee Types</p>
                <p className="text-2xl font-bold">{feeTypes.length}</p>
              </div>
              <div className="p-3 rounded-lg bg-blue-50">
                <Receipt className="w-6 h-6 text-blue-600" />
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Active Fees</p>
                <p className="text-2xl font-bold">{activeFees}</p>
              </div>
              <div className="p-3 rounded-lg bg-green-50">
                <Receipt className="w-6 h-6 text-green-600" />
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Total Amount</p>
                <p className="text-2xl font-bold">${totalFees.toLocaleString()}</p>
              </div>
              <div className="p-3 rounded-lg bg-amber-50">
                <DollarSign className="w-6 h-6 text-amber-600" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Fee Types</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Frequency</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {feeTypes.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-slate-500">
                      <Receipt className="w-12 h-12 mx-auto mb-2 text-slate-300" />
                      No fee types found
                    </TableCell>
                  </TableRow>
                ) : (
                  feeTypes.map((fee) => (
                    <TableRow key={fee.id}>
                      <TableCell>{fee.id}</TableCell>
                      <TableCell className="font-medium">{fee.name}</TableCell>
                      <TableCell className="text-slate-500 max-w-[200px] truncate">
                        {fee.description}
                      </TableCell>
                      <TableCell className="font-medium">${fee.amount?.toLocaleString()}</TableCell>
                      <TableCell>
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getFrequencyColor(fee.frequency)}`}>
                          {fee.frequency}
                        </span>
                      </TableCell>
                      <TableCell>
                        <span
                          className={`px-2 py-1 rounded-full text-xs font-medium ${
                            fee.active
                              ? 'bg-green-100 text-green-800'
                              : 'bg-slate-100 text-slate-800'
                          }`}
                        >
                          {fee.active ? 'Active' : 'Inactive'}
                        </span>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="icon" onClick={() => handleEdit(fee)}>
                          <Pencil className="w-4 h-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(fee.id)}>
                          <Trash2 className="w-4 h-4 text-red-500" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
