import React, { useState } from 'react';
import { useForm, useFieldArray } from 'react-hook-form';
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Loader2, Plus, Trash2, AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

// Schemas
const guardianSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  phone: z.string().min(10, 'Valid phone number required').regex(/^\+?[0-9]{10,15}$/, 'Invalid phone format'),
  email: z.string().email('Invalid email').optional().or(z.literal('')),
  relationship: z.string().min(1, 'Relationship is required'),
  isPrimaryContact: z.boolean().default(false),
  canPickUpChild: z.boolean().default(true),
  canViewFees: z.boolean().default(true),
  canViewResults: z.boolean().default(true),
  canViewAttendance: z.boolean().default(true),
  canReceiveSms: z.boolean().default(true),
});

const enrollSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  middleName: z.string().optional(),
  gender: z.string().min(1, 'Please select a gender'),
  dateOfBirth: z.string().min(1, 'Date of birth is required'),
  classId: z.string().uuid('Please select a class'),
  medicalNotes: z.string().optional(),
  guardians: z.array(guardianSchema).min(1, 'At least one guardian is required'),
});

type EnrollFormValues = z.infer<typeof enrollSchema>;

interface EnrollStudentModalProps {
  isOpen: boolean;
  onClose: () => void;
  classes: Array<{ id: string; name: string }>;
  onSubmit: (data: EnrollFormValues) => Promise<void>;
}

export const EnrollStudentModal: React.FC<EnrollStudentModalProps> = ({ isOpen, onClose, classes, onSubmit }) => {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<EnrollFormValues>({
    resolver: zodResolver(enrollSchema) as any,
    defaultValues: {
      firstName: '',
      lastName: '',
      middleName: '',
      gender: '',
      dateOfBirth: '',
      classId: '',
      medicalNotes: '',
      guardians: [
        {
          firstName: '',
          lastName: '',
          phone: '',
          email: '',
          relationship: '',
          isPrimaryContact: true,
          canPickUpChild: true,
          canViewFees: true,
          canViewResults: true,
          canViewAttendance: true,
          canReceiveSms: true,
        },
      ],
    },
  });

  const { fields: guardianFields, append: appendGuardian, remove: removeGuardian } = useFieldArray({
    control: form.control,
    name: 'guardians',
  });

  const handleSubmit = async (values: EnrollFormValues) => {
    try {
      setIsSubmitting(true);
      setError(null);
      await onSubmit(values);
      form.reset();
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to enroll student');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Enroll New Student</DialogTitle>
          <DialogDescription>
            Enter the student's details and their guardian information.
          </DialogDescription>
        </DialogHeader>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
            
            {/* Student Information Section */}
            <div className="space-y-4">
              <h3 className="text-lg font-semibold border-b pb-2">Student Information</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <FormField control={form.control} name="firstName" render={({ field }) => (
                  <FormItem>
                    <FormLabel>First Name *</FormLabel>
                    <FormControl><Input {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField control={form.control} name="middleName" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Middle Name</FormLabel>
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
              
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <FormField control={form.control} name="gender" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Gender *</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue={field.value}>
                      <FormControl><SelectTrigger><SelectValue placeholder="Select gender" /></SelectTrigger></FormControl>
                      <SelectContent>
                        <SelectItem value="MALE">Male</SelectItem>
                        <SelectItem value="FEMALE">Female</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField control={form.control} name="dateOfBirth" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Date of Birth *</FormLabel>
                    <FormControl><Input type="date" {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField control={form.control} name="classId" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Class *</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue={field.value}>
                      <FormControl><SelectTrigger><SelectValue placeholder="Assign class" /></SelectTrigger></FormControl>
                      <SelectContent>
                        {classes.map(c => (
                          <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )} />
              </div>

              <FormField control={form.control} name="medicalNotes" render={({ field }) => (
                <FormItem>
                  <FormLabel>Medical Notes (Optional)</FormLabel>
                  <FormControl><Input placeholder="Allergies, conditions, etc." {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
            </div>

            {/* Guardians Information Section */}
            <div className="space-y-4">
              <div className="flex items-center justify-between border-b pb-2">
                <h3 className="text-lg font-semibold flex items-center gap-2">
                  Guardian Information
                </h3>
                <Button 
                  type="button" 
                  variant="outline" 
                  size="sm" 
                  onClick={() => appendGuardian({
                    firstName: '', lastName: '', phone: '', email: '', relationship: '',
                    isPrimaryContact: false, canPickUpChild: true, canViewFees: true, 
                    canViewResults: true, canViewAttendance: true, canReceiveSms: true
                  })}
                >
                  <Plus className="w-4 h-4 mr-1" /> Add Guardian
                </Button>
              </div>

              <Alert className="bg-blue-50 border-blue-200">
                <AlertCircle className="w-4 h-4 text-blue-600" />
                <AlertDescription className="text-blue-800">
                  <strong>Important:</strong> The Guardian's Phone Number acts as their unique account ID. 
                  They will use this number to sign in and verify their identity.
                </AlertDescription>
              </Alert>

              <div className="space-y-6">
                {guardianFields.map((field, index) => (
                  <div key={field.id} className="p-4 border rounded-lg bg-gray-50 relative">
                    {guardianFields.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="absolute top-2 right-2 text-red-500 hover:text-red-700"
                        onClick={() => removeGuardian(index)}
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    )}
                    
                    <h4 className="font-medium mb-3">Guardian {index + 1}</h4>
                    
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <FormField control={form.control} name={`guardians.${index}.firstName`} render={({ field }) => (
                        <FormItem>
                          <FormLabel>First Name *</FormLabel>
                          <FormControl><Input {...field} /></FormControl>
                          <FormMessage />
                        </FormItem>
                      )} />
                      <FormField control={form.control} name={`guardians.${index}.lastName`} render={({ field }) => (
                        <FormItem>
                          <FormLabel>Last Name *</FormLabel>
                          <FormControl><Input {...field} /></FormControl>
                          <FormMessage />
                        </FormItem>
                      )} />
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
                      <FormField control={form.control} name={`guardians.${index}.phone`} render={({ field }) => (
                        <FormItem>
                          <FormLabel>Phone Number (Login ID) *</FormLabel>
                          <FormControl><Input placeholder="+234..." {...field} /></FormControl>
                          <FormMessage />
                        </FormItem>
                      )} />
                      <FormField control={form.control} name={`guardians.${index}.relationship`} render={({ field }) => (
                        <FormItem>
                          <FormLabel>Relationship *</FormLabel>
                          <FormControl><Input placeholder="e.g. Father, Mother, Uncle" {...field} /></FormControl>
                          <FormMessage />
                        </FormItem>
                      )} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Enroll Student
              </Button>
            </DialogFooter>

          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
};
