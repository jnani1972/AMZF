/**
 * Orders Page
 * Order management and history with real-time updates
 */

import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useOrders } from '../../hooks/useApi';
import { useOrderUpdates } from '../../hooks/useWebSocket';
import { Header } from '../../components/organisms/Header/Header';
import { Text } from '../../components/atoms/Text/Text';
import { Card } from '../../components/atoms/Card/Card';
import { Badge } from '../../components/atoms/Badge/Badge';
import { Button } from '../../components/atoms/Button/Button';
import { Alert } from '../../components/atoms/Alert/Alert';
import { Spinner } from '../../components/atoms/Spinner/Spinner';
import { EmptyState } from '../../components/molecules/EmptyState/EmptyState';
import type { OrderResponse, OrderStatus } from '../../types';
import { FileText, RefreshCw } from 'lucide-react';
import { getNavItems } from '../../lib/navigation';
import { useMemo } from 'react';

type OrderFilter = 'all' | OrderStatus;

/**
 * Get user display object from auth user
 */
const getUserDisplay = (user: any) =>
  user ? { name: user.displayName, email: user.email } : undefined;

/**
 * Merge live orders with API orders, avoiding duplicates
 */
const mergeOrders = (liveOrders: OrderResponse[], apiOrders: OrderResponse[] | null | undefined): OrderResponse[] => {
  if (!apiOrders) return liveOrders;
  const apiOrdersFiltered = apiOrders.filter(
    (o) => !liveOrders.find((lo) => lo.brokerOrderId === o.brokerOrderId)
  );
  return [...liveOrders, ...apiOrdersFiltered];
};

/**
 * Orders component
 */
export function Orders() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navItems = getNavItems(location.pathname);
  const [filter, setFilter] = useState<OrderFilter>('all');
  const { data: orders, loading, error, refetch } = useOrders();
  const [liveOrders, setLiveOrders] = useState<OrderResponse[]>([]);

  // Listen for real-time order updates
  useOrderUpdates((order) => {
    setLiveOrders((prev) => {
      const index = prev.findIndex((o) => o.brokerOrderId === order.brokerOrderId);
      if (index >= 0) {
        const updated = [...prev];
        updated[index] = order;
        return updated;
      }
      return [order, ...prev];
    });
  });

  // Merge API orders with live updates
  const allOrders = useMemo(() => mergeOrders(liveOrders, orders), [liveOrders, orders]);

  // Filter orders
  const filteredOrders = useMemo(() => {
    return filter === 'all' ? allOrders : allOrders.filter((order) => order.status === filter);
  }, [allOrders, filter]);

  /**
   * Get badge variant for order status
   */
  const getStatusVariant = (status: OrderStatus) => {
    switch (status) {
      case 'COMPLETE':
        return 'success';
      case 'CANCELLED':
      case 'REJECTED':
        return 'error';
      case 'PENDING':
      case 'TRIGGER_PENDING':
      case 'MODIFY_PENDING':
      case 'CANCEL_PENDING':
        return 'warning';
      case 'OPEN':
        return 'info';
      default:
        return 'default';
    }
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />
        <main className="container mx-auto p-6">
          <div className="flex items-center justify-center py-12">
            <Spinner size="lg" variant="primary" />
          </div>
        </main>
      </div>
    );
  }

  /**
   * Render error state
   */
  if (error) {
    return (
      <div className="min-h-screen bg-background">
        <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />
        <main className="container mx-auto p-6">
          <Alert variant="error">
            Failed to load orders: {error}
            <Button variant="secondary" size="sm" onClick={refetch} className="mt-3">
              Retry
            </Button>
          </Alert>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header navItems={navItems} user={getUserDisplay(user)} onLogout={logout} />

      <main className="container mx-auto p-6 space-y-6">
        {/* Page Header */}
        <div className="flex items-center justify-between">
          <div>
            <Text variant="h1" className="mb-2">
              Orders
            </Text>
            <Text variant="body" className="text-muted">
              View and manage your orders
            </Text>
          </div>

          <Button
            variant="secondary"
            iconLeft={<RefreshCw size={20} />}
            onClick={refetch}
          >
            Refresh
          </Button>
        </div>

        {/* Filter Tabs */}
        <Card>
          <div className="p-6">
            <div className="flex flex-wrap gap-2">
              {(['all', 'OPEN', 'PENDING', 'COMPLETE', 'CANCELLED'] as OrderFilter[]).map(
                (status) => (
                  <Button
                    key={status}
                    variant={filter === status ? 'primary' : 'secondary'}
                    size="sm"
                    onClick={() => setFilter(status)}
                  >
                    {status === 'all' ? 'All' : status}
                    {status === 'all' && allOrders.length > 0 && ` (${allOrders.length})`}
                  </Button>
                )
              )}
            </div>
          </div>
        </Card>

        {/* Orders Table */}
        <Card>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Symbol</th>
                  <th>Type</th>
                  <th>Direction</th>
                  <th className="text-right">Quantity</th>
                  <th className="text-right">Price</th>
                  <th>Status</th>
                  <th>Time</th>
                </tr>
              </thead>
              <tbody>
                {filteredOrders.length > 0 ? (
                  filteredOrders.map((order) => (
                    <tr key={order.brokerOrderId}>
                      <td>
                        <div className="table-secondary">
                          {order.brokerOrderId.slice(0, 8)}...
                        </div>
                      </td>
                      <td>
                        <div className="table-primary">{order.symbol}</div>
                      </td>
                      <td>
                        <div className="table-secondary">{order.orderType}</div>
                      </td>
                      <td>
                        <Badge variant={order.direction === 'BUY' ? 'success' : 'error'}>
                          {order.direction}
                        </Badge>
                      </td>
                      <td className="text-right">
                        <div className="table-numeric">
                          {order.filledQuantity}/{order.quantity}
                        </div>
                      </td>
                      <td className="text-right">
                        <div className="table-currency">
                          ₹{order.avgFillPrice?.toFixed(2) || order.orderPrice?.toFixed(2) || '-'}
                        </div>
                      </td>
                      <td>
                        <Badge variant={getStatusVariant(order.status)}>
                          {order.status}
                        </Badge>
                      </td>
                      <td>
                        <div className="table-date">
                          {new Date(order.orderTime).toLocaleString()}
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={8} className="table-empty">
                      <EmptyState
                        icon={<FileText size={48} />}
                        title="No Orders Found"
                        description={filter === 'all' ? "You haven't placed any orders yet" : `No orders with status: ${filter}`}
                        ctaText={filter === 'all' ? undefined : 'Show All Orders'}
                        onCtaClick={filter === 'all' ? undefined : () => setFilter('all')}
                      />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </main>
    </div>
  );
}
