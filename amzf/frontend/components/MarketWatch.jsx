import React, { useState, useEffect, useContext } from 'react';
import { TrendingUp, TrendingDown, Activity, RefreshCw, Clock } from 'lucide-react';
import { AuthContext } from '../PyramidDashboardV04';

const API_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_API__)
  || (import.meta?.env?.VITE_API_BASE_URL)
  || "http://localhost:9090";

const WS_BASE_URL = (typeof window !== "undefined" && window.__ANNUPAPER_WS__)
  || (import.meta?.env?.VITE_WS_BASE_URL)
  || "ws://localhost:9090";

export default function MarketWatch() {
  const { token } = useContext(AuthContext);
  const [watchlist, setWatchlist] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdate, setLastUpdate] = useState(null);
  const [ws, setWs] = useState(null);
  const [wsConnected, setWsConnected] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: null, direction: 'asc' });

  // Fetch initial watchlist data
  useEffect(() => {
    fetchMarketWatch();
  }, [token]);

  // Setup WebSocket connection for real-time updates
  useEffect(() => {
    if (!token) return;

    const websocket = new WebSocket(`${WS_BASE_URL}/ws?token=${token}`);

    websocket.onopen = () => {
      setWsConnected(true);
    };

    websocket.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);

        switch (message.type) {
          case 'ACK':
            // Connection acknowledged by server
            break;

          case 'TICK':
            // Real-time tick price update
            const { symbol, lastPrice, timestamp } = message.payload;
            setWatchlist(prev => prev.map(item =>
              item.symbol === symbol
                ? { ...item, lastPrice, lastTickTime: timestamp }
                : item
            ));
            setLastUpdate(new Date());
            break;

          case 'CANDLE':
            // Candle closed event (optional: could display notification)
            break;

          case 'ERROR':
            console.error('MarketWatch: Server error', message.payload);
            break;

          case 'BATCH':
            // Batched events
            if (message.payload && message.payload.events) {
              message.payload.events.forEach(evt => {
                if (evt.type === 'TICK') {
                  const { symbol, lastPrice, timestamp } = evt.payload;
                  setWatchlist(prev => prev.map(item =>
                    item.symbol === symbol
                      ? { ...item, lastPrice, lastTickTime: timestamp }
                      : item
                  ));
                }
              });
              setLastUpdate(new Date());
            }
            break;

          default:
            // Unhandled message type
        }
      } catch (err) {
        console.error('MarketWatch: Error parsing WebSocket message:', err);
      }
    };

    websocket.onerror = (error) => {
      // Note: In development, React Strict Mode may cause mount/unmount cycles
      // which can trigger error events from old connections. This is expected.
      console.warn('MarketWatch: WebSocket error (may be from old connection in dev mode)', error);
      setWsConnected(false);
    };

    websocket.onclose = (event) => {
      setWsConnected(false);
    };

    setWs(websocket);

    // Cleanup on unmount
    return () => {
      if (websocket.readyState === WebSocket.OPEN || websocket.readyState === WebSocket.CONNECTING) {
        websocket.close();
      }
    };
  }, [token]);

  const fetchMarketWatch = async () => {
    try {
      setLoading(true);
      const response = await fetch(`${API_BASE_URL}/api/market-watch`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch market watch: ${response.status}`);
      }

      const data = await response.json();
      setWatchlist(data);
      setLastUpdate(new Date());
      setError(null);
    } catch (err) {
      setError(err.message);
      console.error('Error fetching market watch:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatPrice = (price) => {
    if (!price) return '-';
    return typeof price === 'number' ? price.toFixed(2) : parseFloat(price).toFixed(2);
  };

  const formatTime = (timestamp) => {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  const formatDateTime = (timestamp) => {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const handleSort = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const getSortedWatchlist = () => {
    if (!sortConfig.key) return watchlist;

    return [...watchlist].sort((a, b) => {
      const aVal = a[sortConfig.key];
      const bVal = b[sortConfig.key];

      // Handle null/undefined values
      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      // Compare values
      if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
  };

  const SortIcon = ({ columnKey }) => {
    if (sortConfig.key !== columnKey) {
      return <span className="text-slate-600 ml-1">⇅</span>;
    }
    return sortConfig.direction === 'asc'
      ? <span className="text-blue-400 ml-1">↑</span>
      : <span className="text-blue-400 ml-1">↓</span>;
  };


  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="flex items-center gap-2 text-slate-400">
          <RefreshCw className="animate-spin" />
          <span>Loading Market Watch...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-500/20 border border-red-500/50 rounded-xl p-4">
        <p className="text-red-400">Error: {error}</p>
        <button
          onClick={fetchMarketWatch}
          className="mt-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-500 transition-colors"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Activity className="w-6 h-6 text-blue-400" />
          <h2 className="text-xl font-semibold text-white">Market Watch</h2>
          {lastUpdate && (
            <div className="flex items-center gap-2 text-sm text-slate-400">
              <Clock className="w-4 h-4" />
              <span>Last updated: {formatTime(lastUpdate.toISOString())}</span>
            </div>
          )}
        </div>
        <button
          onClick={fetchMarketWatch}
          className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white rounded-lg transition-all"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      {/* Market Status Indicator */}
      <div className={`flex items-center gap-2 px-4 py-2 rounded-xl border ${
        wsConnected
          ? 'bg-gradient-to-br from-green-500/20 to-green-600/10 border-green-500/30'
          : 'bg-gradient-to-br from-yellow-500/20 to-yellow-600/10 border-yellow-500/30'
      }`}>
        <div className={`w-2 h-2 rounded-full ${
          wsConnected ? 'bg-green-400 animate-pulse' : 'bg-yellow-400'
        }`} />
        <span className={`text-sm font-medium ${wsConnected ? 'text-green-400' : 'text-yellow-400'}`}>
          {wsConnected ? 'Live Updates - Prices update automatically' : 'Connecting to live updates...'}
        </span>
      </div>

      {/* Watchlist Table */}
      {watchlist.length === 0 ? (
        <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-8 text-center">
          <p className="text-slate-400">No symbols in your watchlist</p>
        </div>
      ) : (
        <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-900/50 border-b border-slate-700/50">
                <tr>
                  <th
                    className="px-4 py-3 text-left text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('symbol')}
                  >
                    Symbol <SortIcon columnKey="symbol" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('lastPrice')}
                  >
                    LTP <SortIcon columnKey="lastPrice" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('overnightChange')}
                  >
                    Chng <SortIcon columnKey="overnightChange" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('overnightChangePercent')}
                  >
                    Chng% <SortIcon columnKey="overnightChangePercent" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('dailyOpen')}
                  >
                    Open <SortIcon columnKey="dailyOpen" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('dailyHigh')}
                  >
                    High <SortIcon columnKey="dailyHigh" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('dailyLow')}
                  >
                    Low <SortIcon columnKey="dailyLow" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('weekHigh52')}
                  >
                    52W High <SortIcon columnKey="weekHigh52" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('weekLow52')}
                  >
                    52W Low <SortIcon columnKey="weekLow52" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('dailyVolume')}
                  >
                    Volume <SortIcon columnKey="dailyVolume" />
                  </th>
                  <th
                    className="px-4 py-3 text-right text-xs font-medium text-slate-400 uppercase tracking-wider cursor-pointer hover:text-slate-200 transition-colors"
                    onClick={() => handleSort('lastTickTime')}
                  >
                    Last Tick Time <SortIcon columnKey="lastTickTime" />
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-700/50">
                {getSortedWatchlist().map((item, index) => (
                  <tr
                    key={item.id || index}
                    className="hover:bg-slate-700/30 transition-colors"
                  >
                    <td className="px-4 py-3 text-sm font-medium text-white">
                      {item.symbol}
                    </td>
                    <td className="px-4 py-3 text-sm text-right font-semibold text-blue-400">
                      {formatPrice(item.lastPrice)}
                    </td>
                    <td className={`px-4 py-3 text-sm text-right font-medium ${
                      item.overnightChange > 0 ? 'text-green-400' :
                      item.overnightChange < 0 ? 'text-red-400' : 'text-slate-300'
                    }`}>
                      {item.overnightChange != null ? formatPrice(item.overnightChange) : '-'}
                    </td>
                    <td className={`px-4 py-3 text-sm text-right font-medium ${
                      item.overnightChangePercent > 0 ? 'text-green-400' :
                      item.overnightChangePercent < 0 ? 'text-red-400' : 'text-slate-300'
                    }`}>
                      {item.overnightChangePercent != null ? `${formatPrice(item.overnightChangePercent)}%` : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-slate-300">
                      {formatPrice(item.dailyOpen)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-green-400">
                      {formatPrice(item.dailyHigh)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-red-400">
                      {formatPrice(item.dailyLow)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-emerald-400">
                      {formatPrice(item.weekHigh52)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-orange-400">
                      {formatPrice(item.weekLow52)}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-slate-300">
                      {item.dailyVolume ? item.dailyVolume.toLocaleString() : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-right text-slate-400">
                      {item.lastTickTime ? formatDateTime(item.lastTickTime) : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Status */}
      <div className="flex items-center justify-between text-xs text-slate-500">
        <div className="flex items-center gap-2">
          <div className={`w-2 h-2 rounded-full ${
            wsConnected ? 'bg-green-400' : 'bg-red-400'
          }`} />
          <span className="text-slate-400">
            WebSocket: {wsConnected ? 'Connected' : 'Disconnected'}
          </span>
        </div>
        <span className="text-slate-400">{watchlist.length} symbols</span>
      </div>
    </div>
  );
}
