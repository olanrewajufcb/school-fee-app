import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Users, CalendarCheck, Award, Bell } from 'lucide-react-native';
import { TeacherClassScreen } from '../features/teacher/screens/TeacherClassScreen';
import { AttendanceRollCallScreen } from '../features/teacher/screens/AttendanceRollCallScreen';
import { GradebookScreen } from '../features/teacher/screens/GradebookScreen';
import { NotificationsScreen } from '../features/notifications/NotificationsScreen';

const Tab = createBottomTabNavigator();

export const TeacherTabNavigator: React.FC = () => {
  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: '#ffffff' },
        headerTitleStyle: { fontWeight: '800', color: '#0f172a' },
        tabBarActiveTintColor: '#059669',
        tabBarInactiveTintColor: '#64748b',
        tabBarStyle: {
          backgroundColor: '#ffffff',
          borderTopColor: '#e2e8f0',
          height: 60,
          paddingBottom: 8,
          paddingTop: 6,
        },
      }}
    >
      <Tab.Screen
        name="MyClass"
        component={TeacherClassScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Users size={size} color={color} />,
          title: 'My Class',
        }}
      />
      <Tab.Screen
        name="AttendanceRoll"
        component={AttendanceRollCallScreen}
        options={{
          tabBarIcon: ({ color, size }) => <CalendarCheck size={size} color={color} />,
          title: 'Daily Roll',
        }}
      />
      <Tab.Screen
        name="Gradebook"
        component={GradebookScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Award size={size} color={color} />,
          title: 'Marks',
        }}
      />
      <Tab.Screen
        name="Notifications"
        component={NotificationsScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Bell size={size} color={color} />,
          title: 'Alerts',
        }}
      />
    </Tab.Navigator>
  );
};

export default TeacherTabNavigator;
