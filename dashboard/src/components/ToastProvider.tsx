import React, { createContext, useContext, useState, type ReactNode, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Loader2, X } from 'lucide-react';

type ToastType = 'success' | 'error' | 'loading' | 'info';

interface ToastMessage {
  id: string;
  message: string;
  type: ToastType;
}

interface ToastContextValue {
  showToast: (message: string, type?: ToastType, duration?: number) => string;
  updateToast: (id: string, newMessage: string, newType?: ToastType, duration?: number) => void;
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}

export const ToastProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (message: string, type: ToastType = 'info', duration: number = 3000) => {
      const id = Math.random().toString(36).substring(2, 9);
      setToasts((prev) => [...prev, { id, message, type }]);

      if (type !== 'loading' && duration > 0) {
        setTimeout(() => {
          removeToast(id);
        }, duration);
      }
      return id;
    },
    [removeToast]
  );

  const updateToast = useCallback(
    (id: string, newMessage: string, newType: ToastType = 'info', duration: number = 3000) => {
      setToasts((prev) =>
        prev.map((t) => (t.id === id ? { ...t, message: newMessage, type: newType } : t))
      );

      if (newType !== 'loading' && duration > 0) {
        setTimeout(() => {
          removeToast(id);
        }, duration);
      }
    },
    [removeToast]
  );

  return (
    <ToastContext.Provider value={{ showToast, updateToast, removeToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-center justify-between gap-3 min-w-[280px] p-4 rounded-xl shadow-lg border backdrop-blur-md transition-all transform animate-in slide-in-from-bottom-5 duration-300 ${
              toast.type === 'error'
                ? 'bg-rose-50 border-rose-200 text-rose-700 dark:bg-rose-900/30 dark:border-rose-500/30 dark:text-rose-400'
                : toast.type === 'success'
                ? 'bg-emerald-50 border-emerald-200 text-emerald-700 dark:bg-emerald-900/30 dark:border-emerald-500/30 dark:text-emerald-400'
                : toast.type === 'loading'
                ? 'bg-blue-50 border-blue-200 text-blue-700 dark:bg-blue-900/30 dark:border-blue-500/30 dark:text-blue-400'
                : 'bg-white border-slate-200 text-slate-700 dark:bg-slate-800 dark:border-slate-700 dark:text-slate-300'
            }`}
          >
            <div className="flex items-center gap-3">
              {toast.type === 'success' && <CheckCircle2 className="w-5 h-5 flex-shrink-0" />}
              {toast.type === 'error' && <AlertCircle className="w-5 h-5 flex-shrink-0" />}
              {toast.type === 'loading' && <Loader2 className="w-5 h-5 animate-spin flex-shrink-0 text-blue-500" />}
              {toast.type === 'info' && <div className="w-2 h-2 rounded-full bg-blue-400 flex-shrink-0" />}
              
              <p className="text-sm font-bold tracking-wide">{toast.message}</p>
            </div>
            <button
              onClick={() => removeToast(toast.id)}
              className="p-1.5 rounded-lg opacity-50 hover:opacity-100 hover:bg-black/5 dark:hover:bg-white/10 transition-all flex-shrink-0"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}