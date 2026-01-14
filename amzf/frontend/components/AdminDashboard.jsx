import React, { useState, useContext } from 'react';
import { Shield, Users, Network, Briefcase, Eye, ArrowLeft, Database, List, Settings } from 'lucide-react';
import UserList from './admin/UserList';
import BrokerManagement from './admin/BrokerManagement';
import PortfolioManagement from './admin/PortfolioManagement';
import WatchlistManagement from './admin/WatchlistManagement';
import WatchlistTemplateManagement from './admin/WatchlistTemplateManagement';
import DataBrokerConfig from './admin/DataBrokerConfig';
import MtfConfigManagement from './admin/MtfConfigManagement';

// Import AuthContext from parent (assumes it's exported from PyramidDashboardV04.jsx)
// For now, we'll assume it's available via React context

const AdminDashboard = ({ user, token, onLogout, onBack }) => {
  const [activeTab, setActiveTab] = useState('users');

  // Role check - redirect if not admin
  if (!user || user.role !== 'ADMIN') {
    return (
      <div className="min-h-screen bg-gray-900 flex items-center justify-center">
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-8 max-w-md">
          <Shield className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <h1 className="text-2xl font-bold text-red-500 text-center mb-2">Access Denied</h1>
          <p className="text-gray-400 text-center">
            You do not have administrator privileges to access this page.
          </p>
        </div>
      </div>
    );
  }

  const tabs = [
    { id: 'users', label: 'Users', icon: Users },
    { id: 'data-broker', label: 'Data Broker', icon: Database },
    { id: 'brokers', label: 'Broker Management', icon: Network },
    { id: 'portfolios', label: 'Portfolio Management', icon: Briefcase },
    { id: 'watchlist', label: 'Watchlists', icon: Eye },
    { id: 'mtf-config', label: 'MTF Configuration', icon: Settings }
  ];

  const renderTabContent = () => {
    switch (activeTab) {
      case 'users':
        return <UserList token={token} />;
      case 'data-broker':
        return <DataBrokerConfig token={token} />;
      case 'brokers':
        return <BrokerManagement token={token} />;
      case 'portfolios':
        return <PortfolioManagement token={token} />;
      case 'watchlist':
        return <WatchlistManagement token={token} />;
      case 'mtf-config':
        return <MtfConfigManagement token={token} />;
      default:
        return <UserList token={token} />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-900 text-gray-100">
      {/* Header */}
      <header className="bg-gray-800 border-b border-gray-700 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <Shield className="w-8 h-8 text-purple-500" />
              <div>
                <h1 className="text-2xl font-bold text-white">Admin Panel</h1>
                <p className="text-sm text-gray-400">AnnuPaper v04 Administration</p>
              </div>
            </div>
            <div className="flex items-center space-x-4">
              <button
                onClick={onBack}
                className="flex items-center space-x-2 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
                <span>Back to Dashboard</span>
              </button>
              <div className="text-right">
                <p className="text-sm font-medium text-white">{user.displayName}</p>
                <p className="text-xs text-purple-400">{user.role}</p>
              </div>
              <button
                onClick={onLogout}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Tab Navigation */}
      <div className="bg-gray-800 border-b border-gray-700">
        <div className="max-w-7xl mx-auto px-4">
          <nav className="flex space-x-1">
            {tabs.map(tab => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`
                    flex items-center space-x-2 px-6 py-3 font-medium border-b-2 transition-colors
                    ${isActive
                      ? 'border-purple-500 text-purple-400 bg-gray-750'
                      : 'border-transparent text-gray-400 hover:text-gray-300 hover:bg-gray-750'
                    }
                  `}
                >
                  <Icon className="w-5 h-5" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Content Area */}
      <main className="max-w-7xl mx-auto px-4 py-6">
        {renderTabContent()}
      </main>
    </div>
  );
};

export default AdminDashboard;
