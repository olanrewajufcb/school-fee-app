import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  RefreshControl,
  SafeAreaView,
} from 'react-native';
import {
  Bell,
  CreditCard,
  Award,
  CalendarCheck,
  Megaphone,
  CheckCheck,
} from 'lucide-react-native';
import { AppNotification, NotificationType } from '../../types';

// Mock initial data populated for real-world school activities
const INITIAL_NOTIFICATIONS: AppNotification[] = [
  {
    id: 'notif-1',
    title: 'First Term Examination Results Released',
    message: 'Academic results for First Term 2025/2026 have been published. Tap to view your child’s report card.',
    type: 'RESULTS',
    read: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(), // 30m ago
  },
  {
    id: 'notif-2',
    title: 'Daily Attendance Alert: Present',
    message: 'Your child arrived at school and was marked Present on the morning roll call at 07:48 AM.',
    type: 'ATTENDANCE',
    read: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 4).toISOString(), // 4h ago
  },
  {
    id: 'notif-3',
    title: 'Second Term Tuition Invoice Available',
    message: 'Tuition invoice for Second Term 2025/2026 is now available. Early bird discount applies if paid before next month.',
    type: 'FEES',
    read: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(), // 1d ago
  },
  {
    id: 'notif-4',
    title: 'Upcoming PTA Meeting & Open Day',
    message: 'Dear parents, please note that the Termly Parent-Teacher Association meeting will hold this Friday at 10:00 AM.',
    type: 'GENERAL',
    read: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(), // 2d ago
  },
  {
    id: 'notif-5',
    title: 'Receipt Generated for Development Levy',
    message: 'Thank you! Payment of ₦15,000 for Development Levy was confirmed. E-Receipt #REC-2026-0428 is ready.',
    type: 'FEES',
    read: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(), // 3d ago
  },
];

type FilterTab = 'ALL' | 'FEES' | 'ATTENDANCE' | 'RESULTS' | 'GENERAL';

export const NotificationsScreen: React.FC<{ navigation?: any }> = ({ navigation }) => {
  const [notifications, setNotifications] = useState<AppNotification[]>(INITIAL_NOTIFICATIONS);
  const [activeTab, setActiveTab] = useState<FilterTab>('ALL');
  const [refreshing, setRefreshing] = useState(false);

  const unreadCount = notifications.filter((n) => !n.read).length;

  const filteredNotifications = notifications.filter((n) => {
    if (activeTab === 'ALL') return true;
    return n.type === activeTab;
  });

  const onRefresh = () => {
    setRefreshing(true);
    setTimeout(() => {
      setRefreshing(false);
    }, 800);
  };

  const handleMarkAllAsRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  };

  const handleNotificationPress = (item: AppNotification) => {
    // Mark as read
    setNotifications((prev) =>
      prev.map((n) => (n.id === item.id ? { ...n, read: true } : n))
    );

    // Deep link to relevant tab if navigation is available
    if (item.type === 'FEES') {
      navigation?.navigate('Fees');
    } else if (item.type === 'RESULTS') {
      navigation?.navigate('Results');
    } else if (item.type === 'ATTENDANCE') {
      navigation?.navigate('Attendance');
    }
  };

  const renderIcon = (type: NotificationType) => {
    switch (type) {
      case 'FEES':
        return <CreditCard size={20} color="#2563eb" />;
      case 'RESULTS':
        return <Award size={20} color="#059669" />;
      case 'ATTENDANCE':
        return <CalendarCheck size={20} color="#d97706" />;
      case 'GENERAL':
      default:
        return <Megaphone size={20} color="#7c3aed" />;
    }
  };

  const getIconBg = (type: NotificationType) => {
    switch (type) {
      case 'FEES':
        return '#dbeafe';
      case 'RESULTS':
        return '#dcfce7';
      case 'ATTENDANCE':
        return '#fef3c7';
      case 'GENERAL':
      default:
        return '#f3e8ff';
    }
  };

  const formatTimestamp = (iso: string) => {
    const diffMs = Date.now() - new Date(iso).getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours < 1) return 'Just now';
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays}d ago`;
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Top Bar */}
      <View style={styles.topBar}>
        <View>
          <Text style={styles.pageTitle}>Notifications</Text>
          <Text style={styles.pageSubtitle}>
            {unreadCount > 0 ? `${unreadCount} unread alerts` : 'All caught up'}
          </Text>
        </View>

        {unreadCount > 0 && (
          <TouchableOpacity onPress={handleMarkAllAsRead} style={styles.markReadButton}>
            <CheckCheck size={16} color="#2563eb" />
            <Text style={styles.markReadText}>Mark all read</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Filter Tabs */}
      <View style={styles.tabBar}>
        {(['ALL', 'FEES', 'RESULTS', 'ATTENDANCE', 'GENERAL'] as FilterTab[]).map((tab) => {
          const isActive = activeTab === tab;
          const label =
            tab === 'ALL'
              ? 'All'
              : tab === 'FEES'
              ? 'Fees'
              : tab === 'RESULTS'
              ? 'Results'
              : tab === 'ATTENDANCE'
              ? 'Roll Call'
              : 'School';

          return (
            <TouchableOpacity
              key={tab}
              onPress={() => setActiveTab(tab)}
              style={[styles.tabItem, isActive && styles.tabItemActive]}
            >
              <Text style={[styles.tabLabel, isActive && styles.tabLabelActive]}>
                {label}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>

      {/* Notifications List */}
      <FlatList
        data={filteredNotifications}
        keyExtractor={(item) => item.id}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        contentContainerStyle={styles.listContainer}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Bell size={40} color="#cbd5e1" />
            <Text style={styles.emptyTitle}>No Notifications</Text>
            <Text style={styles.emptySubtitle}>You have no notifications in this category.</Text>
          </View>
        }
        renderItem={({ item }) => (
          <TouchableOpacity
            onPress={() => handleNotificationPress(item)}
            activeOpacity={0.7}
            style={[styles.notificationCard, !item.read && styles.unreadCard]}
          >
            <View style={[styles.iconCircle, { backgroundColor: getIconBg(item.type) }]}>
              {renderIcon(item.type)}
            </View>

            <View style={styles.contentCol}>
              <View style={styles.titleRow}>
                <Text style={[styles.itemTitle, !item.read && styles.unreadTitle]}>
                  {item.title}
                </Text>
                {!item.read && <View style={styles.unreadDot} />}
              </View>

              <Text style={styles.itemMessage} numberOfLines={2}>
                {item.message}
              </Text>

              <Text style={styles.itemTimestamp}>{formatTimestamp(item.createdAt)}</Text>
            </View>
          </TouchableOpacity>
        )}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  pageTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#0f172a',
  },
  pageSubtitle: {
    fontSize: 12,
    color: '#64748b',
    marginTop: 2,
  },
  markReadButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: '#eff6ff',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8,
  },
  markReadText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2563eb',
  },
  tabBar: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: '#ffffff',
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
    gap: 6,
  },
  tabItem: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#f1f5f9',
  },
  tabItemActive: {
    backgroundColor: '#2563eb',
  },
  tabLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748b',
  },
  tabLabelActive: {
    color: '#ffffff',
  },
  listContainer: {
    padding: 16,
    gap: 10,
  },
  notificationCard: {
    flexDirection: 'row',
    padding: 14,
    backgroundColor: '#ffffff',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#f1f5f9',
    gap: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 4,
    elevation: 1,
  },
  unreadCard: {
    borderColor: '#bfdbfe',
    backgroundColor: '#f0f7ff',
  },
  iconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  contentCol: {
    flex: 1,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  itemTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1e293b',
    flex: 1,
  },
  unreadTitle: {
    fontWeight: '800',
    color: '#0f172a',
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#2563eb',
    marginLeft: 6,
  },
  itemMessage: {
    fontSize: 13,
    color: '#475569',
    lineHeight: 18,
    marginBottom: 6,
  },
  itemTimestamp: {
    fontSize: 11,
    color: '#94a3b8',
    fontWeight: '500',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#475569',
    marginTop: 12,
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#94a3b8',
    marginTop: 4,
    textAlign: 'center',
  },
});

export default NotificationsScreen;
