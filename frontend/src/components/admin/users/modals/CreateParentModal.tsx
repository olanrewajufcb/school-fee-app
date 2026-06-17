import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useCreateParentMutation } from '@/hooks/queries/useUsers';
import { toast } from 'sonner';
import type { CreateParentRequest } from '@/types/user-management';
import { X, Plus } from 'lucide-react';

const formSchema = z.object({
  firstName: z.string().min(2, 'First name must be at least 2 characters'),
  lastName: z.string().min(2, 'Last name must be at least 2 characters'),
  email: z.string().email('Invalid email address'),
  phone: z.string().min(10, 'Phone number must be at least 10 digits'),
  studentIds: z.array(z.string()).optional(),
});

type FormValues = z.infer<typeof formSchema>;

interface CreateParentModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const CreateParentModal: React.FC<CreateParentModalProps> = ({ isOpen, onClose }) => {
  const { mutateAsync: createParent, isPending } = useCreateParentMutation();
  const [studentIds, setStudentIds] = useState<string[]>([]);
  const [newStudentId, setNewStudentId] = useState('');

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      studentIds: [],
    },
  });

  const onSubmit = async (data: FormValues) => {
    try {
      await createParent({
        ...data,
        studentIds,
      } as CreateParentRequest);
      toast.success('Parent account created successfully');
      form.reset();
      setStudentIds([]);
      onClose();
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Failed to create parent account');
    }
  };

  const addStudentId = () => {
    if (newStudentId && !studentIds.includes(newStudentId)) {
      setStudentIds([...studentIds, newStudentId]);
      setNewStudentId('');
    }
  };

  const removeStudentId = (id: string) => {
    setStudentIds(studentIds.filter(s => s !== id));
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[425px] bg-white">
        <DialogHeader>
          <DialogTitle className="text-xl text-gray-800">Add Parent</DialogTitle>
          <DialogDescription className="text-gray-500">
            Create a parent account and link them to students.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 pt-4">
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>First Name</FormLabel>
                    <FormControl>
                      <Input placeholder="Jane" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Last Name</FormLabel>
                    <FormControl>
                      <Input placeholder="Smith" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Email Address</FormLabel>
                  <FormControl>
                    <Input type="email" placeholder="jane.smith@email.com" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="phone"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Phone Number</FormLabel>
                  <FormControl>
                    <Input placeholder="+1234567890" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="space-y-3">
              <FormLabel>Linked Students (UUIDs)</FormLabel>
              <div className="flex gap-2">
                <Input 
                  placeholder="Enter Student ID" 
                  value={newStudentId}
                  onChange={(e) => setNewStudentId(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addStudentId())}
                />
                <Button type="button" onClick={addStudentId} variant="secondary">
                  <Plus className="w-4 h-4" />
                </Button>
              </div>
              
              {studentIds.length > 0 && (
                <div className="flex flex-wrap gap-2 mt-2 p-3 bg-gray-50 rounded-md border border-gray-100">
                  {studentIds.map(id => (
                    <span key={id} className="inline-flex items-center gap-1 bg-white border border-gray-200 text-xs px-2 py-1 rounded-full shadow-sm text-gray-700">
                      <span className="truncate max-w-[120px]">{id}</span>
                      <button type="button" onClick={() => removeStudentId(id)} className="text-gray-400 hover:text-red-500">
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-gray-100 mt-6">
              <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending} className="bg-blue-600 hover:bg-blue-700">
                {isPending ? 'Creating...' : 'Create Parent'}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};

export default CreateParentModal;
