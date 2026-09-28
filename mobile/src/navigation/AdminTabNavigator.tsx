import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { LayoutDashboard, CalendarCheck, Sparkles, Bell } from 'lucide-react-native';
import { AdminOverviewScreen } from '../features/admin/screens/AdminOverviewScreen';
import { AdminAttendanceScreen } from '../features/admin/screens/AdminAttendanceScreen';
import { SubscriptionScreen } from '../features/admin/screens/SubscriptionScreen';
import { NotificationsScreen } from '../features/notifications/NotificationsScreen';

const Tab = createBottomTabNavigator();

export const AdminTabNavigator: React.FC = () => {
  return (
    <Tab.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: '#ffffff' },
        headerTitleStyle: { fontWeight: '800', color: '#0f172a' },
        tabBarActiveTintColor: '#2563eb',
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
        name="AdminOverview"
        component={AdminOverviewScreen}
        options={{
          tabBarIcon: ({ color, size }) => <LayoutDashboard size={size} color={color} />,
          title: 'Dashboard',
        }}
      />
      <Tab.Screen
        name="AttendanceMonitor"
        component={AdminAttendanceScreen}
        options={{
          tabBarIcon: ({ color, size }) => <CalendarCheck size={size} color={color} />,
          title: 'Attendance',
        }}
      />
      <Tab.Screen
        name="Subscription"
        component={SubscriptionScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Sparkles size={size} color={color} />,
          title: 'Subscription',
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

export default AdminTabNavigator;
