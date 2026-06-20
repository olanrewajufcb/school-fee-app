import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Loader2, AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

const parentSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  phoneNumber: z.string().min(10, 'Valid phone number required').regex(/^\+?[0-9]{10,15}$/, 'Invalid phone format'),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
  relationship: z.string().min(1, 'Relationship is required'),
  isPrimaryContact: z.boolean().default(false),
});

type ParentFormValues = z.infer<typeof parentSchema>;

interface AddGuardianToStudentModalProps {
  isOpen: boolean;
  onClose: () => void;
  studentId: string;
  studentName: string;
  onSubmit: (data: any) => Promise<void>;
}

export const AddGuardianToStudentModal: React.FC<AddGuardianToStudentModalProps> = ({ 
  isOpen, onClose, studentId, studentName, onSubmit 
}) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<ParentFormValues>({
    resolver: zodResolver(parentSchema) as any,
    defaultValues: {
      firstName: '',
      lastName: '',
      phoneNumber: '',
      email: '',
      relationship: '',
      isPrimaryContact: false,
    },
  });

  const handleSubmit = async (values: ParentFormValues) => {
    try {
      setIsSubmitting(true);
      setError(null);
      
      const payload = {
        firstName: values.firstName,
        lastName: values.lastName,
        phoneNumber: values.phoneNumber,
        email: values.email,
        children: [
          {
            studentId: studentId,
            relationship: values.relationship,
            isPrimaryContact: values.isPrimaryContact,
            canPickUpChild: true,
            canViewFees: true,
            canViewResults: true,
            canViewAttendance: true,
            canReceiveSms: true,
          }
        ]
      };
      
      await onSubmit(payload);
      form.reset();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to add guardian');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Add Guardian to {studentName}</DialogTitle>
          <DialogDescription>
            Register a guardian for this student. They will receive an SMS invitation.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Alert className="bg-blue-50 border-blue-200">
          <AlertCircle className="w-4 h-4 text-blue-600" />
          <AlertDescription className="text-blue-800">
            <strong>Important:</strong> The Guardian's Phone Number acts as their login identity.
          </AlertDescription>
        </Alert>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <FormField control={form.control} name="firstName" render={({ field }) => (
                <FormItem>
                  <FormLabel>First Name *</FormLabel>
                  <FormControl><Input {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="lastName" render={({ field }) => (
                <FormItem>
                  <FormLabel>Last Name *</FormLabel>
                  <FormControl><Input {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
            </div>

            <FormField control={form.control} name="phoneNumber" render={({ field }) => (
              <FormItem>
                <FormLabel>Phone Number (Login ID) *</FormLabel>
                <FormControl><Input placeholder="+234..." {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />

            <FormField control={form.control} name="email" render={({ field }) => (
              <FormItem>
                <FormLabel>Email (Optional)</FormLabel>
                <FormControl><Input type="email" {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />

            <FormField control={form.control} name="relationship" render={({ field }) => (
              <FormItem>
                <FormLabel>Relationship *</FormLabel>
                <FormControl><Input placeholder="Father, Mother, Aunt..." {...field} /></FormControl>
                <FormMessage />
              </FormItem>
            )} />

            <DialogFooter className="mt-6">
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Add Guardian
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};
