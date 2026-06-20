import { useState, useEffect } from 'react';
import { notificationsApi } from '@/services/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
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
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Bell, Send, Mail, MessageSquare, FileText, Loader2 } from 'lucide-react';
import { toast } from 'sonner';

interface NotificationData {
  id: number;
  type: string;
  recipient: string;
  subject: string;
  message: string;
  status: string;
  sentAt: string;
}

interface TemplateData {
  id: number;
  code: string;
  name: string;
  content: string;
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationData[]>([]);
  const [templates, setTemplates] = useState<TemplateData[]>([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('all');
  const [formData, setFormData] = useState({
    type: 'EMAIL',
    recipient: '',
    subject: '',
    message: '',
  });

  useEffect(() => {
    fetchNotifications();
    fetchTemplates();
  }, []);

  const fetchNotifications = async () => {
    try {
      const response = await notificationsApi.getAll();
      setNotifications(response.data as NotificationData[]);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const fetchTemplates = async () => {
    try {
      const response = await notificationsApi.getTemplates();
      setTemplates(response.data as TemplateData[]);
    } catch (error) {
      console.error(error);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    setSending(true);
    try {
      await notificationsApi.send(formData);
      toast.success('Notification sent successfully');
      setDialogOpen(false);
      setFormData({ type: 'EMAIL', recipient: '', subject: '', message: '' });
      fetchNotifications();
    } catch (error) {
      toast.error('Failed to send notification');
      console.error(error);
    } finally {
      setSending(false);
    }
  };

  const handleUseTemplate = (template: TemplateData) => {
    setFormData({
      ...formData,
      subject: template.name,
      message: template.content,
    });
    setDialogOpen(true);
  };

  const handleSendReminders = async () => {
    try {
      await notificationsApi.sendReminders({});
      toast.success('Fee due reminders queued for sending');
    } catch (error) {
      toast.error('Failed to send reminders');
      console.error(error);
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'EMAIL':
        return <Mail className="w-4 h-4 text-blue-600" />;
      case 'SMS':
        return <MessageSquare className="w-4 h-4 text-green-600" />;
      case 'IN_APP':
        return <Bell className="w-4 h-4 text-purple-600" />;
      default:
        return <Bell className="w-4 h-4 text-slate-600" />;
    }
  };

  const filteredNotifications =
    activeTab === 'all'
      ? notifications
      : notifications.filter((n) => n.type === activeTab.toUpperCase());

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Notifications</h1>
          <p className="text-slate-500 mt-1">Send and manage notifications</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleSendReminders}>
            <Bell className="w-4 h-4 mr-2" />
            Send Fee Reminders
          </Button>
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button onClick={() => setDialogOpen(true)}>
                <Send className="w-4 h-4 mr-2" />
                Send Notification
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[500px]">
              <DialogHeader>
                <DialogTitle>Send Notification</DialogTitle>
              </DialogHeader>
              <form onSubmit={handleSendNotification} className="space-y-4">
                <div className="space-y-2">
                  <Label>Type</Label>
                  <Select
                    value={formData.type}
                    onValueChange={(value) => setFormData({ ...formData, type: value })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="EMAIL">Email</SelectItem>
                      <SelectItem value="SMS">SMS</SelectItem>
                      <SelectItem value="IN_APP">In-App</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="recipient">Recipient</Label>
                  <Input
                    id="recipient"
                    value={formData.recipient}
                    onChange={(e) => setFormData({ ...formData, recipient: e.target.value })}
                    placeholder={
                      formData.type === 'EMAIL'
                        ? 'email@example.com'
                        : formData.type === 'SMS'
                        ? '+1234567890'
                        : 'User ID'
                    }
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="notifSubject">Subject</Label>
                  <Input
                    id="notifSubject"
                    value={formData.subject}
                    onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="notifMessage">Message</Label>
                  <Textarea
                    id="notifMessage"
                    value={formData.message}
                    onChange={(e) => setFormData({ ...formData, message: e.target.value })}
                    rows={4}
                    required
                  />
                </div>
                <Button type="submit" className="w-full" disabled={sending}>
                  {sending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  Send Notification
                </Button>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Templates */}
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileText className="w-5 h-5" />
              Templates
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {templates.map((template) => (
              <div
                key={template.id}
                className="p-3 rounded-lg border border-slate-200 hover:border-blue-300 hover:bg-blue-50 transition-colors cursor-pointer"
                onClick={() => handleUseTemplate(template)}
              >
                <p className="font-medium text-sm">{template.name}</p>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">{template.content}</p>
                <p className="text-xs text-blue-600 mt-2">Click to use template</p>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* Notifications List */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <Tabs value={activeTab} onValueChange={setActiveTab}>
              <TabsList>
                <TabsTrigger value="all">All</TabsTrigger>
                <TabsTrigger value="email">Email</TabsTrigger>
                <TabsTrigger value="sms">SMS</TabsTrigger>
                <TabsTrigger value="in_app">In-App</TabsTrigger>
              </TabsList>
            </Tabs>
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
                    <TableHead>Type</TableHead>
                    <TableHead>Recipient</TableHead>
                    <TableHead>Subject</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Date</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredNotifications.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center py-8 text-slate-500">
                        <Bell className="w-12 h-12 mx-auto mb-2 text-slate-300" />
                        No notifications found
                      </TableCell>
                    </TableRow>
                  ) : (
                    filteredNotifications.map((notif) => (
                      <TableRow key={notif.id}>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            {getTypeIcon(notif.type)}
                            <span className="text-xs font-medium">{notif.type}</span>
                          </div>
                        </TableCell>
                        <TableCell className="text-sm">{notif.recipient}</TableCell>
                        <TableCell className="font-medium text-sm">{notif.subject}</TableCell>
                        <TableCell>
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                            {notif.status}
                          </span>
                        </TableCell>
                        <TableCell className="text-slate-500 text-sm">
                          {notif.sentAt
                            ? new Date(notif.sentAt).toLocaleDateString()
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
    </div>
  );
}
