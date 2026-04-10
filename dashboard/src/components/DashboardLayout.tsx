import React, { useState, useEffect } from 'react';
import { Server, Moon, Sun, LayoutDashboard, Cpu, ListTodo, Menu, X } from 'lucide-react';
import { Outlet, NavLink } from 'react-router-dom';
import frierenLogo from '../assets/frieren.png';

const DashboardLayout: React.FC = () => {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    return (localStorage.getItem('theme') as 'light' | 'dark') || 'dark';
  });

  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(t => t === 'dark' ? 'light' : 'dark');
  };

  const getNavClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-semibold transition-all duration-300 w-full ${
      isActive 
        ? 'bg-white/40 dark:bg-white/10 dark:text-mint-300 text-purple-700 shadow-sm border border-white/50 dark:border-white/10' 
        : 'text-slate-600 dark:text-slate-300 hover:bg-white/20 dark:hover:bg-white/5 hover:text-purple-600 dark:hover:text-mint-200'
    }`;

  const closeMobileMenu = () => setIsMobileMenuOpen(false);

  return (
    <div className="min-h-screen h-screen relative overflow-hidden flex bg-transparent dark:text-slate-200 transition-colors duration-500">
      {/* Background Ornaments */}
      <div className="absolute inset-0 bg-stars pointer-events-none" />
      <div className="crescent-moon pointer-events-none transition-opacity duration-1000" style={{ opacity: theme === 'dark' ? 0.9 : 0.4 }} />
      <div className="cloud" style={{ top: '15%', left: '5%' }} />
      <div className="cloud" style={{ top: '60%', right: '8%', transform: 'scale(0.8)' }} />
      <div className="cloud" style={{ top: '80%', left: '15%', transform: 'scale(1.2)' }} />
      
      {/* Mobile Hamburger Header (Only visible on small screens) */}
      <div className="md:hidden absolute top-0 left-0 right-0 p-4 z-50 flex items-center justify-between glass-panel rounded-t-none border-t-0 shadow-sm">
        <div className="flex items-center gap-3">
           <img src={frierenLogo} alt="Frieren Logo" className="w-6 h-auto drop-shadow-md" />
           <span className="font-semibold text-slate-700 dark:text-slate-200">FernOS</span>
        </div>
        <button onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} className="p-2 bg-white/20 dark:bg-white/5 rounded-lg flex items-center justify-center text-slate-600 dark:text-slate-300">
          {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Sidebar Navigation */}
      <aside className={`fixed md:static inset-y-0 left-0 z-40 transform ${isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'} md:translate-x-0 transition-transform duration-300 ease-in-out w-64 glass-panel dark:bg-slate-900/40 m-0 md:m-4 md:mr-0 flex flex-col justify-between p-6 top-16 md:top-0 h-auto md:h-auto border-r md:border-r`}>
        <div>
          {/* Brand */}
          <div className="flex items-center gap-4 mb-10 hidden md:flex">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr flex items-center justify-center flex-shrink-0 shadow-[0_0_15px_rgba(192,132,252,0.4)]" style={{ background: 'linear-gradient(to top right, #331f4a, #1a2f3a)' }}>
              <img src={frierenLogo} alt="Frieren Logo" className="w-10 h-auto translate-y-[-2px] drop-shadow-lg" />
            </div>
            <h1 className="text-xl font-semibold text-slate-700 dark:text-slate-200 tracking-tight text-glow">
              FernOS
            </h1>
          </div>
          
          {/* Main Links */}
          <nav className="flex flex-col gap-3">
            <NavLink to="/" onClick={closeMobileMenu} className={getNavClass}>
              <LayoutDashboard className="w-4 h-4" /> Overview
            </NavLink>
            <NavLink to="/workers" onClick={closeMobileMenu} className={getNavClass}>
              <Cpu className="w-4 h-4" /> Workers
            </NavLink>
            <NavLink to="/jobs" onClick={closeMobileMenu} className={getNavClass}>
              <ListTodo className="w-4 h-4" /> Jobs
            </NavLink>
          </nav>
        </div>

        {/* Footer Area with Theme Toggle */}
        <div className="mt-8 flex flex-col gap-4">
          <button 
            onClick={toggleTheme}
            className="flex items-center gap-3 px-4 py-2 w-full rounded-xl text-sm font-semibold text-slate-600 dark:text-slate-300 hover:bg-white/20 dark:hover:bg-white/5 transition-all outline-none"
          >
            {theme === 'dark' ? <Sun className="w-4 h-4 text-orange-300" /> : <Moon className="w-4 h-4 text-blue-500" />}
            {theme === 'dark' ? 'Light Mode' : 'Dark Mode'}
          </button>
          
          <div className="px-4 py-3 bg-white/20 dark:bg-black/20 rounded-xl text-xs font-medium text-slate-500 dark:text-slate-400 flex flex-col gap-1.5 shadow-inner">
            <div className="flex items-center justify-between mb-1">
              <span className="flex items-center gap-1.5 font-semibold text-slate-600 dark:text-slate-300"><Server className="w-3.5 h-3.5 text-mint-500" /> System</span>
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shadow-[0_0_8px_rgba(52,211,153,0.8)]" title="System Online"></span>
            </div>
            <div className="flex justify-between">
              <span>RAM</span> <span className="font-semibold text-slate-700 dark:text-slate-300">6.1 GB</span>
            </div>
            <div className="flex justify-between">
              <span>Swap</span> <span className="font-semibold text-slate-700 dark:text-slate-300">0%</span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 pt-16 md:pt-0">
        <main className="relative z-10 flex-1 m-4 md:ml-6 mb-4 min-h-0 flex flex-col">
          <Outlet />
        </main>
      </div>
      
      {/* Mobile Menu Backdrop */}
      {isMobileMenuOpen && (
        <div 
          className="fixed inset-0 bg-slate-900/20 backdrop-blur-sm z-30 md:hidden transition-opacity" 
          onClick={closeMobileMenu}
        />
      )}
    </div>
  );
};

export default DashboardLayout;
