import React, { useEffect } from 'react';
import { View, ActivityIndicator } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useAuthStore } from '../store/authStore';
import { AuthNavigator } from './AuthNavigator';
import { ParentTabNavigator } from './ParentTabNavigator';
import { TeacherTabNavigator } from './TeacherTabNavigator';
import { AdminTabNavigator } from './AdminTabNavigator';
import { ReceiptDetailScreen } from '../features/parent/screens/ReceiptDetailScreen';

export type RootStackParamList = {
  Auth: undefined;
  ParentMain: undefined;
  TeacherMain: undefined;
  AdminMain: undefined;
  ReceiptDetail: { receiptNumber: string };
};

const RootStack = createNativeStackNavigator<RootStackParamList>();

export const RootNavigator: React.FC = () => {
  const { isAuthenticated, isLoading, user, initializeAuth } = useAuthStore();

  useEffect(() => {
    initializeAuth();
  }, []);

  if (isLoading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#f8fafc' }}>
        <ActivityIndicator size="large" color="#2563eb" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <RootStack.Navigator screenOptions={{ headerShown: false }}>
        {!isAuthenticated ? (
          <RootStack.Screen name="Auth" component={AuthNavigator} />
        ) : user?.userType === 'TEACHER' ? (
          <>
            <RootStack.Screen name="TeacherMain" component={TeacherTabNavigator} />
            <RootStack.Screen
              name="ReceiptDetail"
              component={ReceiptDetailScreen}
              options={{ presentation: 'modal' }}
            />
          </>
        ) : user?.userType === 'SCHOOL_ADMIN' || user?.userType === 'SUPER_ADMIN' ? (
          <>
            <RootStack.Screen name="AdminMain" component={AdminTabNavigator} />
            <RootStack.Screen
              name="ReceiptDetail"
              component={ReceiptDetailScreen}
              options={{ presentation: 'modal' }}
            />
          </>
        ) : (
          <>
            <RootStack.Screen name="ParentMain" component={ParentTabNavigator} />
            <RootStack.Screen
              name="ReceiptDetail"
              component={ReceiptDetailScreen}
              options={{ presentation: 'modal' }}
            />
          </>
        )}
      </RootStack.Navigator>
    </NavigationContainer>
  );
};

export default RootNavigator;
