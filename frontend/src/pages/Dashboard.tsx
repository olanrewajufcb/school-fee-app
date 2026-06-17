import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { schoolsApi, feesApi, paymentsApi } from '@/services/api';
import {
  School,
  Receipt,
  CreditCard,
  TrendingUp,
  AlertCircle,
  CheckCircle,
  Clock,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';


export function Dashboard() {
  const [stats, setStats] = useState({
    schools: 0,
    feeTypes: 0,
    payments: 0,
    totalRevenue: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    try {
      const [schoolsRes, feesRes, paymentsRes] = await Promise.all([
        schoolsApi.getAll(),
        feesApi.getAllFeeTypes(),
        paymentsApi.getAll(),
      ]);

      const payments = paymentsRes.data as Array<Record<string, unknown>>;
      const totalRevenue = payments.reduce(
        (sum: number, p: Record<string, unknown>) => sum + Number(p.amount || 0),
        0
      );

      setStats({
        schools: (schoolsRes.data as Array<unknown>)?.length || 0,
        feeTypes: (feesRes.data as Array<unknown>)?.length || 0,
        payments: payments.length || 0,
        totalRevenue,
      });
    } catch (error) {
      console.error('Error fetching dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const statusData = [
    { name: 'Paid', value: 45, color: '#10b981' },
    { name: 'Pending', value: 30, color: '#f59e0b' },
    { name: 'Overdue', value: 15, color: '#ef4444' },
    { name: 'Partial', value: 10, color: '#3b82f6' },
  ];

  const revenueData = [
    { month: 'Jan', revenue: 12000 },
    { month: 'Feb', revenue: 15000 },
    { month: 'Mar', revenue: 18000 },
    { month: 'Apr', revenue: 14000 },
    { month: 'May', revenue: 22000 },
    { month: 'Jun', revenue: 25000 },
  ];

  const statCards = [
    {
      title: 'Total Schools',
      value: stats.schools,
      icon: School,
      color: 'text-blue-600',
      bg: 'bg-blue-50',
    },
    {
      title: 'Fee Types',
      value: stats.feeTypes,
      icon: Receipt,
      color: 'text-green-600',
      bg: 'bg-green-50',
    },
    {
      title: 'Total Payments',
      value: stats.payments,
      icon: CreditCard,
      color: 'text-purple-600',
      bg: 'bg-purple-50',
    },
    {
      title: 'Revenue',
      value: `$${stats.totalRevenue.toLocaleString()}`,
      icon: TrendingUp,
      color: 'text-amber-600',
      bg: 'bg-amber-50',
    },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-slate-900">Dashboard</h1>
        <p className="text-slate-500 mt-1">Overview of your school fee management system</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((stat) => {
          const Icon = stat.icon;
          return (
            <Card key={stat.title}>
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-slate-500">{stat.title}</p>
                    <p className="text-2xl font-bold mt-1">{stat.value}</p>
                  </div>
                  <div className={`p-3 rounded-lg ${stat.bg}`}>
                    <Icon className={`w-6 h-6 ${stat.color}`} />
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Monthly Revenue</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={revenueData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip formatter={(value) => `$${value}`} />
                <Bar dataKey="revenue" fill="#3b82f6" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Fee Status Distribution</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={statusData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {statusData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex flex-wrap justify-center gap-4 mt-4">
              {statusData.map((item) => (
                <div key={item.name} className="flex items-center gap-2">
                  <div
                    className="w-3 h-3 rounded-full"
                    style={{ backgroundColor: item.color }}
                  />
                  <span className="text-sm text-slate-600">{item.name}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <CardHeader>
          <CardTitle>Quick Actions</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="flex items-center gap-3 p-4 bg-green-50 rounded-lg border border-green-100">
              <CheckCircle className="w-8 h-8 text-green-600" />
              <div>
                <p className="font-medium text-green-900">Process Payment</p>
                <p className="text-sm text-green-700">Record a new fee payment</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-4 bg-amber-50 rounded-lg border border-amber-100">
              <Clock className="w-8 h-8 text-amber-600" />
              <div>
                <p className="font-medium text-amber-900">Pending Fees</p>
                <p className="text-sm text-amber-700">View pending fee payments</p>
              </div>
            </div>
            <div className="flex items-center gap-3 p-4 bg-red-50 rounded-lg border border-red-100">
              <AlertCircle className="w-8 h-8 text-red-600" />
              <div>
                <p className="font-medium text-red-900">Overdue Fees</p>
                <p className="text-sm text-red-700">View and follow up overdue fees</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
