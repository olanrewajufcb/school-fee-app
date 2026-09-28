import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Home, CreditCard, Award, CalendarCheck, Bell } from 'lucide-react-native';
import { ParentOverviewScreen } from '../features/parent/screens/ParentOverviewScreen';
import { FeesScreen } from '../features/parent/screens/FeesScreen';
import { ResultsScreen } from '../features/parent/screens/ResultsScreen';
import { AttendanceScreen } from '../features/parent/screens/AttendanceScreen';
import { NotificationsScreen } from '../features/notifications/NotificationsScreen';

const Tab = createBottomTabNavigator();

export const ParentTabNavigator: React.FC = () => {
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
        name="Overview"
        component={ParentOverviewScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Home size={size} color={color} />,
          title: 'Home',
        }}
      />
      <Tab.Screen
        name="Fees"
        component={FeesScreen}
        options={{
          tabBarIcon: ({ color, size }) => <CreditCard size={size} color={color} />,
          title: 'Fees',
        }}
      />
      <Tab.Screen
        name="Results"
        component={ResultsScreen}
        options={{
          tabBarIcon: ({ color, size }) => <Award size={size} color={color} />,
          title: 'Results',
        }}
      />
      <Tab.Screen
        name="Attendance"
        component={AttendanceScreen}
        options={{
          tabBarIcon: ({ color, size }) => <CalendarCheck size={size} color={color} />,
          title: 'Attendance',
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

export default ParentTabNavigator;
