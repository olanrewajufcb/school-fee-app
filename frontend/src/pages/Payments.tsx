import { useState, useEffect } from 'react';
import { paymentsApi } from '@/services/api';
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { CreditCard, Plus, CheckCircle, Clock, AlertCircle } from 'lucide-react';
import { toast } from 'sonner';

interface PaymentData {
  id: number;
  studentFeeId: number;
  studentId: number;
  amount: number;
  paymentMethod: string;
  status: string;
  transactionId: string;
  processedAt: string;
}

export function Payments() {
  const [payments, setPayments] = useState<PaymentData[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [summary, setSummary] = useState({ totalPayments: 0, totalAmount: 0 });
  const [formData, setFormData] = useState({
    studentFeeId: '',
    studentId: '',
    amount: '',
    paymentMethod: 'CASH',
  });

  useEffect(() => {
    fetchPayments();
    fetchSummary();
  }, []);

  const fetchPayments = async () => {
    try {
      const response = await paymentsApi.getAll();
      setPayments(response.data as PaymentData[]);
    } catch (error) {
      toast.error('Failed to fetch payments');
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const fetchSummary = async () => {
    try {
      const response = await paymentsApi.getSummary();
      setSummary(response.data as { totalPayments: number; totalAmount: number });
    } catch (error) {
      console.error(error);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await paymentsApi.process({
        studentFeeId: parseInt(formData.studentFeeId),
        studentId: parseInt(formData.studentId),
        amount: parseFloat(formData.amount),
        paymentMethod: formData.paymentMethod,
      });
      toast.success('Payment processed successfully');
      setDialogOpen(false);
      setFormData({ studentFeeId: '', studentId: '', amount: '', paymentMethod: 'CASH' });
      fetchPayments();
      fetchSummary();
    } catch (error) {
      toast.error('Failed to process payment');
      console.error(error);
    }
  };


  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle className="w-4 h-4 text-green-600" />;
      case 'PENDING':
        return <Clock className="w-4 h-4 text-amber-600" />;
      case 'FAILED':
        return <AlertCircle className="w-4 h-4 text-red-600" />;
      default:
        return null;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Payments</h1>
          <p className="text-slate-500 mt-1">Process and manage fee payments</p>
        </div>
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={() => setDialogOpen(true)}>
              <Plus className="w-4 h-4 mr-2" />
              Process Payment
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[500px]">
            <DialogHeader>
              <DialogTitle>Process New Payment</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="studentId">Student ID</Label>
                  <Input
                    id="studentId"
                    type="number"
                    value={formData.studentId}
                    onChange={(e) => setFormData({ ...formData, studentId: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="studentFeeId">Student Fee ID</Label>
                  <Input
                    id="studentFeeId"
                    type="number"
                    value={formData.studentFeeId}
                    onChange={(e) => setFormData({ ...formData, studentFeeId: e.target.value })}
                    required
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="payAmount">Amount ($)</Label>
                  <Input
                    id="payAmount"
                    type="number"
                    step="0.01"
                    value={formData.amount}
                    onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                    placeholder="0.00"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label>Payment Method</Label>
                  <Select
                    value={formData.paymentMethod}
                    onValueChange={(value) => setFormData({ ...formData, paymentMethod: value })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="CASH">Cash</SelectItem>
                      <SelectItem value="CARD">Credit Card</SelectItem>
                      <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                      <SelectItem value="ONLINE">Online Payment</SelectItem>
                      <SelectItem value="CHECK">Check</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <Button type="submit" className="w-full">
                Process Payment
              </Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Total Payments</p>
                <p className="text-2xl font-bold">{summary.totalPayments}</p>
              </div>
              <div className="p-3 rounded-lg bg-blue-50">
                <CreditCard className="w-6 h-6 text-blue-600" />
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Total Amount</p>
                <p className="text-2xl font-bold">${summary.totalAmount.toLocaleString()}</p>
              </div>
              <div className="p-3 rounded-lg bg-green-50">
                <CheckCircle className="w-6 h-6 text-green-600" />
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">Completed</p>
                <p className="text-2xl font-bold">
                  {payments.filter((p) => p.status === 'COMPLETED').length}
                </p>
              </div>
              <div className="p-3 rounded-lg bg-purple-50">
                <CheckCircle className="w-6 h-6 text-purple-600" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Recent Payments</CardTitle>
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
                  <TableHead>Transaction ID</TableHead>
                  <TableHead>Student ID</TableHead>
                  <TableHead>Amount</TableHead>
                  <TableHead>Method</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {payments.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-slate-500">
                      <CreditCard className="w-12 h-12 mx-auto mb-2 text-slate-300" />
                      No payments found
                    </TableCell>
                  </TableRow>
                ) : (
                  payments.map((payment) => (
                    <TableRow key={payment.id}>
                      <TableCell>{payment.id}</TableCell>
                      <TableCell className="font-mono text-xs">{payment.transactionId}</TableCell>
                      <TableCell>{payment.studentId}</TableCell>
                      <TableCell className="font-medium">
                        ${payment.amount?.toLocaleString()}
                      </TableCell>
                      <TableCell>
                        <span className="px-2 py-1 rounded-full text-xs font-medium bg-slate-100">
                          {payment.paymentMethod}
                        </span>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1">
                          {getStatusIcon(payment.status)}
                          <span
                            className={`text-xs font-medium ${
                              payment.status === 'COMPLETED'
                                ? 'text-green-700'
                                : payment.status === 'PENDING'
                                ? 'text-amber-700'
                                : 'text-red-700'
                            }`}
                          >
                            {payment.status}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-slate-500 text-sm">
                        {payment.processedAt
                          ? new Date(payment.processedAt).toLocaleDateString()
                          : '-'}
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
