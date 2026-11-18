import React from 'react';
import {
  LayoutDashboard,
  Building2,
  FolderArchive,
  Layers,
  CheckSquare,
  BookOpen,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

interface SidebarProps {
  activeTab: string;
  onSelectTab: (tabId: string) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
  pendingReviewCount: number;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeTab,
  onSelectTab,
  collapsed,
  onToggleCollapse,
  pendingReviewCount,
}) => {
  const primaryNavItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'organizations', label: 'Organisations', icon: Building2 },
    { id: 'catalogues', label: 'Material Catalogues', icon: FolderArchive },
    { id: 'harmonization', label: 'Harmonization', icon: Layers },
    { id: 'review-queue', label: 'Review Queue', icon: CheckSquare, badge: pendingReviewCount },
    { id: 'registry', label: 'Unified Registry', icon: BookOpen },
  ];

  return (
    <aside
      className={`relative flex shrink-0 flex-col border-r border-[var(--sidebar-border)] bg-[var(--sidebar)] transition-all duration-200 select-none z-30 ${
        collapsed ? 'w-14 sm:w-16' : 'w-44 lg:w-60 2xl:w-72'
      }`}
      style={{ minHeight: '100vh' }}
    >
      {/* Brand Header */}
      <div className="px-4 py-5 border-b border-[var(--sidebar-border)]">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="w-7 h-7 rounded bg-[var(--navy)] flex items-center justify-center text-white font-mono font-bold text-xs">
              M
            </div>
            {!collapsed && (
              <div>
                <span className="font-mono text-sm font-bold tracking-wider text-[var(--navy)] block leading-tight">
                  MATRIQ
                </span>
                <span className="text-[10px] text-[var(--text-muted)] tracking-normal block">
                  Material Intelligence
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Primary Navigation */}
      <nav aria-label="Workspace" className="sticky top-2 flex-1 px-2 py-3 space-y-1">
        {!collapsed && <p className="label-tech px-2 pb-2">Operational Modules</p>}
        {primaryNavItems.map((item, index) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectTab(item.id)}
              aria-label={item.label}
              aria-current={isActive ? "page" : undefined}
              title={collapsed ? item.label : undefined}
              className={`w-full flex items-center justify-between px-3 py-2.5 rounded text-left transition-colors text-[13px] font-mono cursor-pointer ${
                isActive
                  ? 'bg-[var(--navy)] font-semibold text-white'
                  : 'text-[var(--text-secondary)] hover:bg-[#eeece6] hover:text-[var(--navy)]'
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                {!collapsed && <span aria-hidden className="text-[9px] tabular-nums opacity-75">{String(index + 1).padStart(2, '0')}</span>}
                <Icon className={`w-4 h-4 flex-shrink-0 ${isActive ? 'text-[#82a8ff]' : 'text-current'}`} />
                {!collapsed && <span className="truncate">{item.label}</span>}
              </div>
              {!collapsed && item.badge !== undefined && item.badge > 0 && (
                <span className={`px-1.5 py-0.2 rounded text-[10px] font-mono font-bold ${
                  isActive ? 'bg-[#39516d] text-white' : 'border border-[#f0d194] bg-[#fff3cf] text-[#984510]'
                }`}>
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Collapse Footer */}
      <div className="p-2 border-t border-[var(--sidebar-border)] flex items-center justify-between">
        {!collapsed && (
          <span className="text-[10px] font-mono text-[var(--text-muted)] px-2">
            One Material. One Identity.
          </span>
        )}
        <button
          onClick={onToggleCollapse}
          className="p-1.5 text-[var(--text-muted)] hover:text-[var(--navy)] hover:bg-[#eeece6] rounded transition-colors ml-auto cursor-pointer"
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>
    </aside>
  );
};
